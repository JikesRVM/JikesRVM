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

      OPT_Compiler.setBootOptions( options );

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
