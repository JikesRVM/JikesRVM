/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.Enumeration;

/**
 * <p> The main driver of the OPT_Compiler. 
 * <p> External drivers are responsible for providing the policies; the 
 * role of this class is simply to take a OPT_CompilationPlan
 * and execute it.
 *
 * Currently, this class is invoked from four clients:
 * <ul>
 *  <li> (1) Command line: ExecuteOptCode
 *  <li> (2) BootImageWriting: VM_BootImageCompiler.compile (optimizing version)
 *  <li> (3) RuntimeCompiler: VM_RuntimeCompiler.compile (optimizing version)
 *  <li> (4) AOS: Compilation threads execute controller plans by invoking 
 *      the opt compiler.
 * </ul>
 *
 * <p> Clients are responsible for ensuring that:
 *  <ul>
 *  <li> (1) the VM has been initialized
 *  <li> (2) OPT_Compiler.init has been called before the first opt compilation
 *  </ul>
 *
 * <p> This class is not meant to be instantiated.
 *
 * @author Brian Cooper
 * @author Vassily Litvinov
 * @author Dave Grove
 * @author Michael Hind
 * @author Stephen Fink
 *
 */
public class OPT_Compiler {

  ////////////////////////////////////////////
  // Initialization
  ////////////////////////////////////////////
  /**
   * Prepare compiler for use.
   * @param options options to use for compilations during initialization
   */
  public static void init (OPT_Options options) {
    try {
      if (!(VM.writingBootImage || VM.runningTool || VM.runningVM)) {
        // Caller failed to ensure that the VM was initialized.
        throw new OPT_OptimizingCompilerException("VM not initialized", true);
      }

      // Make a local copy so that some options can be forced off just for the
      // duration of this initialization step.
      options = (OPT_Options)options.clone();
      options.SIMPLE_ESCAPE_IPA = false;

      //-#if RVM_WITH_ADAPTIVE_SYSTEM
      // Disable instrumentation because this method is compiled
      // before the adaptive system is finished booting.  Not pretty,
      // but it works for now.
      options.INSERT_DEBUGGING_COUNTERS = false;
      options.INSERT_YIELDPOINT_COUNTERS = false;
      options.INSERT_INSTRUCTION_COUNTERS = false;
      options.INSERT_METHOD_COUNTERS_OPT = false;
      //-#endif

      initializeStatics();
      if (VM.runningVM) {
        // Make sure that VM_OptSaveVolatile.java is opt 
        // compiled (to get special prologues/epilogues)
        // TODO: This could be phased out as the new DynamicBridge 
        // magic comes on line.
        loadSpecialClass("LVM_OptSaveVolatile;", options);
      }
      isInitialized = true;
    } catch (OPT_OptimizingCompilerException e) {
      // failures during initialization can't be ignored
      e.isFatal = true;
      throw e;
    } catch (Throwable e) {
	e.printStackTrace();
	throw new OPT_OptimizingCompilerException("OPT_Compiler", 
						  "untrapped failure during init, "
						  + " Converting to OPT_OptimizingCompilerException");
    }
  }

  /**
   * Set up option used while compiling the boot image
   * @param options the options to set
   */
  public static void setBootOptions(OPT_Options options) {
    // Pick an optimization level
    options.setOptLevel(3); 

    // Disable things that we think are a bad idea in this context
    options.GUARDED_INLINE = false;        // badly hurts pBOB performance if enabled (25% reduction in TPM).

    // Pre-existence based inlining isn't supported for bootimage writing.
    // Similarly, we need to avoid IG_CODE_PATCH (uses same dependency database)
    // The problem is that some subset of bootimage methods can't be safely invalidated.
    // If a method that is executed when GC is disabled becomes invalid, we are unable
    // to invalidate it (since we can't recompile it the next time it is called).
    // We could work around this by doing eager recompilation of invalidated bootimage
    // methods, but for now simply give up on these optimizations when writing the bootimage.
    options.PREEX_INLINE = false;
    options.INLINING_GUARD = OPT_Options.IG_METHOD_TEST;

    // Compute summaries of bootimage methods if we haven't encountered them yet.
    // Does not handle unimplemented magics very well; disable until
    // we can get a chance to either implement them on IA32 or fix the 
    // analysis to not be so brittle.
    // options.SIMPLE_ESCAPE_IPA = true;

    // Static inlining controls. 
    // Be more aggressive when building the boot image then we are normally.
    options.IC_MAX_TARGET_SIZE = 5*VM_OptMethodSummary.CALL_COST;
    options.IC_MAX_INLINE_DEPTH = 6;
    options.IC_MAX_INLINE_EXPANSION_FACTOR = 7;
    OPT_InlineOracleDictionary.registerDefault(new OPT_StaticInlineOracle());
  }

  /**
   * Load a class which must be compiled by the opt compiler in a special way
   * @param klassName the class to load
   * @param options compiler options for compiling the class
   * @exception VM_ResolutionException if the class cannot be resolved
   */
  private static void loadSpecialClass (String klassName, OPT_Options options) 
    throws VM_ResolutionException {
    VM_Class klass = (VM_Class)OPT_ClassLoaderProxy.findOrCreateType(klassName, VM_SystemClassLoader.getVMClassLoader());
    klass.load();
    klass.resolve();
    klass.instantiate();
    klass.initialize();
    VM_Method[] methods = klass.getDeclaredMethods();
    for (int j = 0; j < methods.length; j++) {
      VM_Method meth = methods[j];
      if (meth.isClassInitializer())
        continue;
      if (!meth.isCompiled() || 
	  meth.getCurrentCompiledMethod().getCompilerInfo().getCompilerType() != VM_CompilerInfo.OPT) {
        OPT_CompilationPlan cp = 
	  new OPT_CompilationPlan(meth, 
				  OPT_OptimizationPlanner.createOptimizationPlan(options), 
				  null, options);
        meth.replaceCompiledMethod(compile(cp));
      }
    }
  }

  public static void preloadSpecialClass( OPT_Options options ) {
    String klassName = "L"+options.PRELOAD_CLASS+";";

    if (options.PRELOAD_AS_BOOT ) {
      setBootOptions( options  );
      // Make a local copy so that some options can be altered to mimic options
      // during boot build
      options = (OPT_Options)options.clone();
    }

    try {
      loadSpecialClass(klassName, options);
    } catch (Throwable e) {
      e.printStackTrace();
      VM.sysWrite("Ignoring failure of preloadSpecialClass of "+klassName+"\n");
    }
  }

  /**
   * Call the static init functions for the OPT_Compiler subsystems
   */
  private static void initializeStatics () {
    OPT_InlineOracleDictionary.registerDefault(new OPT_StaticInlineOracle());
    OPT_InvokeeThreadLocalContext.init();
    VM_Class.OptCLDepManager = new OPT_ClassLoadingDependencyManager();
    VM_OptStaticProgramStats.reset();
  }

  /**
   * Prevent instantiation by clients
   */
  private OPT_Compiler () {
  }

  /**
   * Has the optimizing compiler been initialized?
   */
  private static boolean isInitialized = false;

  /**
   * Has the optimizing compiler been initialized?
   */
  static boolean isInitialized () {
    return isInitialized;
  }

  /**
   * Reset the optimizing compiler
   */
  static void reset () {
    isInitialized = false;
  }

  ////////////////////////////////////////////
  // Public interface for compiling a method
  ////////////////////////////////////////////
  /**
   * Invoke the opt compiler to execute a compilation plan.
   * 
   * @param cp the compilation plan to be executed
   * @return the VM_CompiledMethod object containing the compiled code 
   * & VM_OptCompilerInfo
   */
  public static VM_CompiledMethod compile (OPT_CompilationPlan cp) {
    VM_Method method = cp.method;
    OPT_Options options = cp.options;
    checkSupported(method, options);
    try {
      printMethodMessage(method, options);
      OPT_IR ir = cp.execute();
      // Temporary workaround memory retention problems
      if (!cp.irGeneration) {
	cleanIR(ir);
      }
      // if doing analysis only, don't try to return an object
      if (cp.analyzeOnly || cp.irGeneration)
	return null;
      // now that we're done compiling, give the specialization
      // system a chance to eagerly compile any specialized version
      // that are pending.  TODO: use lazy compilation with specialization.
      OPT_SpecializationDatabase.doDeferredSpecializations();
      ir.compiledMethod.compileComplete(ir.MIRInfo.info, ir.MIRInfo.machinecode);
      return ir.compiledMethod;
    } catch (OPT_OptimizingCompilerException e) {
      throw e;
    } catch (Throwable e) {
      fail(e, method);
      return null;
    }
  }

  /**
   * For some unknown reasons this is needed for the GC to free completely 
   * the IR.
   * TODO: Find out why and avoid the overhead of explictly nulling everything
   * My guess is that the problem is that the physical register 
   * (OPT_Registers 0-80)
   * are "globals" and persistient.  This keeps much of the IR reachable 
   * after compilation
   * completes.  If we ever make the opt compiler truly reentrant, 
   * this would be fixed. --dave.
   */
  static void cleanIR (OPT_IR ir) {
    OPT_DefUse.clearDU(ir);
    for (OPT_Instruction instr = ir.firstInstructionInCodeOrder(); instr
        != null;) {
      int numberOperands = instr.getNumberOfOperands();
      OPT_Instruction next = instr.nextInstructionInCodeOrder();
      for (int i = 0; i < numberOperands; i++) {
        OPT_Operand op = instr.getOperand(i);
        instr.putOperand(i, null);
        if (op == null)
          continue;
        op.instruction = null;
        if (op instanceof OPT_RegisterOperand) {
          OPT_RegisterOperand regOp = (OPT_RegisterOperand)op;
          regOp.scratchObject = null;
          regOp.register = null;
        }
      }
      instr.scratchObject = null;
      instr.clearLinks();
      instr = next;
    }
  }

  /**
   * Debugging aid.
   * @param what a string message to print
   */
  public static void report (String what) {
    VM.sysWrite(what + '\n');
  }

  /**
   * Debugging aid.
   * @param what a string message to print
   * @param time a timestamp to print
   */
  public static void report (String what, long time) {
    VM.sysWrite(what);
    if (what.length() < 8)
      VM.sysWrite('\t');
    if (what.length() < 16)
      VM.sysWrite('\t');
    VM.sysWrite('\t' + time + " ms");
  }

  /**
   * Debugging aid to be called before printing the IR
   * @param what a string message to print
   * @param method the method being compiled
   */
  public static void header (String what, VM_Method method) {
    System.out.println("********* START OF:  " + what + "   FOR " + method);
  }

  /**
   * Debugging aid to be called after printing the IR
   * @param what a string message to print
   * @param method the method being compiled
   */
  public static void bottom (String what, VM_Method method) {
    System.out.println("*********   END OF:  " + what + "   FOR " + method);
  }

  /**
   * Print the IR along with a message
   * @param ir
   * @param message
   */
  public static void printInstructions (OPT_IR ir, String message) {
    header(message, ir.method);
    ir.printInstructions();
    bottom(message, ir.method);
  }

  /**
   * Print a message of a method name
   * @param method
   * @param options
   */
  private static void printMethodMessage (VM_Method method, 
                                          OPT_Options options) {
    if (options.PRINT_METHOD || options.PRINT_INLINE_REPORT)
      VM.sysWrite("-methodOpt "+ method.getDeclaringClass() + ' ' 
		  + method.getName() + ' ' 
		  + method.getDescriptor() + " \n");
  }

  /**
   * Abort a compilation with an error.
   * @param e The exception thrown by a compiler phase
   * @param method The method being compiled
   */
  private static void fail (Throwable e, VM_Method method) {
    VM.sysWrite("OPT_Compiler failure during compilation of " 
              + method.toString() + "\n");
    e.printStackTrace();
    throw new OPT_OptimizingCompilerException("OPT_Compiler", 
					      "failure during compilation of", method.toString());
  }

  /**
   * Check whether opt compilation of a particular method is supported.
   * If not, throw a non-fatal run-time exception.
   */
  private static void checkSupported (VM_Method method, OPT_Options options) {
    if (method.getDeclaringClass().isDynamicBridge()) {
      String msg = "Dynamic Bridge register save protocol not implemented";
      throw OPT_MagicNotImplementedException.EXPECTED(msg);
    }
    if (method.getDeclaringClass().isBridgeFromNative()) {
      String msg = "Native Bridge prologue not implemented";
      throw OPT_MagicNotImplementedException.EXPECTED(msg);
    }
    if (method.isNative()) {
      String msg = "OPT compiler is not to be used for JNI stub generation";
      throw new OPT_OperationNotImplementedException(msg);
    }
    if (options.hasEXCLUDE()) {
      String name = 
	method.getDeclaringClass().toString() + "." + method.getName();
      if (options.fuzzyMatchEXCLUDE(name)) {
        throw new OPT_OptimizingCompilerException("method excluded", false);
      }
    }
  }
}
