/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Use optimizing compiler to compile code at run time.
 *
 * @author Stephen Fink
 * @author David Grove
 */
class VM_RuntimeCompiler extends VM_RuntimeOptCompilerInfrastructure {

  public static final int COMPILER_TYPE = VM_CompilerInfo.OPT;

  public static void boot() {
    // This is needed before any OPT compilation can occur
    VM.sysWrite("VM_RuntimeCompiler: boot (opt compiler)\n");
    try {
      VM_RuntimeOptCompilerInfrastructure.boot(); 
    }
    catch (OPT_OptimizingCompilerException e) {
      String msg = "VM_RuntimeCompiler: OPT_Compiler failed during initialization: "+e+"\n";
      if (e.isFatal && options.ERRORS_FATAL) {
	e.printStackTrace();
	VM.sysFail(msg);
      } else {
	VM.sysWrite(msg);
      }
      // at this point compilerEnabled flag will remain false, 
      // so we'll use the baseline compiler for all methods
    }
  }

  static void initializeMeasureCompilation() {
    VM_RuntimeOptCompilerInfrastructure.initializeMeasureCompilation(); 
  }


  // This method is called if there are some command-line arguments to be processed.
  // It is not guaranteed to be called.
  public static void processCommandLineArg(String arg) {
    if (compilerEnabled) {
      if (options.processAsOption("-X:irc", arg)) {
	// update the optimization plan to reflect the new command line argument
	setNoCacheFlush(options);
	optimizationPlan = OPT_OptimizationPlanner.createOptimizationPlan(options);
      } else {
	VM.sysWrite("VM_RuntimeCompiler: Unrecognized argument \""+arg+"\" with prefix -X:irc:\n");
	VM.sysExit(1);
      }
    } else {
      VM.sysWrite("VM_RuntimeCompiler: Compiler not enabled; unable to process command line argument: "+arg+"\n");
      VM.sysExit(1);
    }
  }

  // tries to compile the passed method with the OPT_Compiler.
  // if this fails we use the fallback compiler (baseline for now)
  static VM_CompiledMethod compile(VM_Method method) {
    if (!compilerEnabled                          // opt compiler isn't initialized yet
	|| !VM_Scheduler.allProcessorsInitialized // gc system isn't fully up: reduce memory load
	|| method.isClassInitializer()            // will only run once: don't bother optimizing
	|| VM_Thread.getCurrentThread().hardwareExceptionRegisters.inuse // exception in progress. can't use opt compiler: it uses exceptions and runtime doesn't support multiple pending (undelivered) exceptions [--DL]
	|| method.isNative()                      // opt compiler doesn't support compiling the JNI stub needed to invoke native methods
	)
       {
    // VM.sysWrite("VM_RuntimeCompiler: no opt compile for " + method + "\n");
       return fallback(method);
       }
    else
       return VM_RuntimeOptCompilerInfrastructure.optCompileWithFallBack(method);
  }

}
