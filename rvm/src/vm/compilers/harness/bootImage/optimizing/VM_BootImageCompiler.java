/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
/**
 * Use optimizing compiler to build virtual machine boot image.
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
class VM_BootImageCompiler {

  // Identity.
  //
  public static final int COMPILER_TYPE = VM_CompilerInfo.OPT;

  /** 
   * Initialize boot image compiler.
   * @param args command line arguments to the bootimage compiler
   */
  public static void init(String[] args) {
    try {
      options = new OPT_Options();
      VM.sysWrite("VM_BootImageCompiler: init (opt compiler)\n");
	 
      // Writing a boot image is a little bit special.  We're not really 
      // concerned about compile time, but we do care a lot about the quality
      // and stability of the generated code.  Set the options accordingly.

      // Pick an optimization level
      options.setOptLevel(3); 

      // Disable things that we think are a bad idea in this context
      options.EAGER_INLINE = false;        // badly hurts pBOB performance if enabled (25% reduction in TPM).

      // would increase build time by some 25%
      options.GCP = false;
      options.TURN_WHILES_INTO_UNTILS = false;
      options.GCSE=false;
	 
      // Pre-existence based inlining isn't supported for bootimage writing
      // to avoid needing to stick the dependency database in the bootimage
      options.PREEX_INLINE = false;

      // We currently don't know where the JTOC is going to end up
      // while we're compiling at bootimage writing 
      // (not until the image is actually built and we're pickling it
      //  does the current bootimage writer know where the JTOC will end up).
      // TODO: Fix the bootimage writer so that the jtoc can be found at a 
      // known location from the bootrecord.  Possibly by making the bootrecord
      // reachable from a JTOC slot and then making the jtoc be the very first object
      // in the bootimage (cost would be that at booting we would have 1 extra load 
      // to acquire the bootrecord address from its jtoc slot).
      options.FIXED_JTOC = false;

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

      // An unexpected error when building the opt boot image should be fatal
      options.ERRORS_FATAL = true;

      // Allow further customization by the user.
      for (int i = 0, n = args.length; i < n; i++) {
	String arg = args[i];
	if (!options.processAsOption("-X:bc:", arg)) {
	  VM.sysWrite("VM_BootImageCompiler: Unrecognized argument "+arg+"; ignoring\n");
	}
      }

      OPT_Compiler.init(options);

      optimizationPlan = OPT_OptimizationPlanner.createOptimizationPlan(options);

      // Kludge:  The opt complier gets into all sorts of trouble when 
      // inlining is enabled if java.lang.Object isn't fully instantiated 
      // (in particular, instantiating Arrays which requires a clone of 
      // java.lang.Object's TIB and SI leads to recursive baseline compilation).
      // Therefore, we force java.lang.Object to be instantiated early by 
      // calling VM_Class.forName.
      // TODO:  We really want to be be able to replace the baseline compiled
      // version of the methods with an opt compiled method, but doing that is
      // tricky! Several obvious hacks didn't work, so I'm leaving this for 
      // now under the assumption that any important methods of 
      // java.lang.Object will be inlined, and thus opt compiled. --dave
      VM_Class object = VM_Class.forName("java.lang.Object");
      //-#if RVM_WITH_GCTk_ALLOC_ADVICE
      GCTk_AllocAdvice.buildInit(options.ALLOC_ADVICE_FILE);
      //-#endif
      compilerEnabled = true;
    } catch (OPT_OptimizingCompilerException e) {
      String msg = "VM_BootImageCompiler: OPT_Compiler failed during initialization: "+e+"\n";
      if (e.isFatal && options.ERRORS_FATAL) {
	e.printStackTrace();
	VM.sysFail(msg);
      } else {
	VM.sysWrite(msg);
      }
    } catch (VM_ResolutionException e) {
      String msg = "VM_BootImageCompiler: Failure during initialization: "+e+"\n";
      VM.sysFail(msg);
    }
  }
   
  /** 
   * Compile a method.
   * @param method the method to compile
   * @return the compiled method
   */
  public static VM_CompiledMethod compile(VM_Method method) {

    VM_Callbacks.notifyMethodCompile(method, COMPILER_TYPE);
    if (!compilerEnabled) return VM_Compiler.compile(method); // Other half of kludge for java.lang.Object (see above)
    try {
      OPT_CompilationPlan cp = new OPT_CompilationPlan(method, optimizationPlan, null, options);
      return OPT_Compiler.compile(cp);
    }
    catch (OPT_OptimizingCompilerException e) {
      String msg = "VM_BootImageCompiler: can't optimize \"" + method + "\" (error was: " + e + ")\n"; 
      if (e.isFatal && options.ERRORS_FATAL) {
	e.printStackTrace();
	VM.sysFail(msg);
      } else {
	boolean printMsg = true;
	if (e instanceof OPT_MagicNotImplementedException) {
	  printMsg = !((OPT_MagicNotImplementedException)e).isExpected;
	}
	if (printMsg) VM.sysWrite(msg);
      }
      return VM_Compiler.compile(method);
    }
  }

  /**
   * Create stackframe mapper appropriate for this compiler.
   */
  static VM_GCMapIterator createGCMapIterator(int[] registerLocations) {
    return new VM_OptGCMapIterator(registerLocations);
  }

  // only used for java.lang.Object kludge
  private static boolean compilerEnabled; 

  // Cache objects needed to cons up compilation plans
  private static OPT_OptimizationPlanElement[] optimizationPlan;
  private static OPT_Options options;
}
