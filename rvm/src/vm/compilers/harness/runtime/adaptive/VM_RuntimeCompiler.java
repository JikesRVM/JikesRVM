/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$

/**
 *
 * The adaptive version of the runtime compiler.
 * 
 * @author Michael Hind
 * @modified by Matthew Arnold
 */
class VM_RuntimeCompiler extends VM_RuntimeOptCompilerInfrastructure {
  static OPT_InlineOracle offlineInlineOracle;

  static void boot() { 
    VM.sysWrite("VM_RuntimeCompiler: boot (adaptive compilation)\n");

    // initialize the OPT compiler, if any operation throws
    // an exception, we'll let it propagate to our caller because we
    // need both the baseline and OPT compilers to work
    // to perform adaptive compilation.
    // Currently, the baseline compiler does not have a boot method
    VM_RuntimeOptCompilerInfrastructure.boot(); 

  }
  
  static void initializeMeasureCompilation() {
    VM_RuntimeOptCompilerInfrastructure.initializeMeasureCompilation(); 
  }
  
  static void processCommandLineArg(String arg) {
    if (VM_Controller.options !=null  && VM_Controller.options.optOnly()) {
      if (compilerEnabled) {
	if (options.processAsOption("-X:aos:irc", arg)) {
	  // update the optimization plan to reflect the new command line argument
	  setNoCacheFlush(options);
	  optimizationPlan = OPT_OptimizationPlanner.createOptimizationPlan(options);
	} else {
	  VM.sysWrite("VM_RuntimeCompiler (optOnly): Unrecognized argument \""+arg+"\" with prefix -X:aos:irc:\n");
	  VM.sysExit(-1);
	}
      } else {
	VM.sysWrite("VM_RuntimeCompiler: Compiler not enabled; unable to process command line argument: "+arg+"\n");
	VM.sysExit(-1);
      }
    } else {
      VM_BaselineCompiler.processCommandLineArg("-X:aos:irc", arg);
    }
  }
  
  // This will be called by the classLoader when we need to compile a method
  // for the first time.
  static VM_CompiledMethod compile(VM_Method method) {
    VM_CompiledMethod cm;

    if (!VM_Controller.enabled) {
      VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.BASELINE);
      // System still early in boot process; compile with baseline compiler
      cm = baselineCompile(method);
      VM_ControllerMemory.incrementNumBase();
    } else {
      if ( !preloadChecked ) {
	preloadChecked = true;			// prevent subsequent calls
	// N.B. This will use irc options
	if ( VM_BaselineCompiler.options.PRELOAD_CLASS != null ) {
	  compilationInProgress = true;		// use baseline during preload
	  // Other than when boot options are requested (processed during preloadSpecialClass
	  // It is hard to communicate options for these special compilations. Use the 
	  // default options and at least pick up the verbose if requested for base/irc
	  OPT_Options tmpoptions = (OPT_Options)options.clone();
	  tmpoptions.PRELOAD_CLASS = VM_BaselineCompiler.options.PRELOAD_CLASS;
	  tmpoptions.PRELOAD_AS_BOOT = VM_BaselineCompiler.options.PRELOAD_AS_BOOT;
	  if (VM_BaselineCompiler.options.PRINT_METHOD) {
	    tmpoptions.PRINT_METHOD = true;
	  } else 
	    tmpoptions = options;
          OPT_Compiler.preloadSpecialClass( tmpoptions );
	  compilationInProgress = false;
	}
      }
      if (VM_Controller.options.optOnly()) {
	if (// will only run once: don't bother optimizing
	    method.isClassInitializer() || 
	    // exception in progress. can't use opt compiler: 
            // it uses exceptions and runtime doesn't support 
            // multiple pending (undelivered) exceptions [--DL]
	    VM_Thread.getCurrentThread().hardwareExceptionRegisters.inuse ||
	    // opt compiler doesn't support compiling the JNI 
            // stub needed to invoke native methods
	    method.isNative()) {
          VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.BASELINE);
	  // compile with baseline compiler
	  cm = baselineCompile(method);
          VM_ControllerMemory.incrementNumBase();
	} else { // compile with opt compiler
          VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.OPT);
	  // Initialize an instrumentation plan.
	  VM_AOSInstrumentationPlan instrumentationPlan = 
	    new VM_AOSInstrumentationPlan(VM_Controller.options,
					  method);

	  // create a compilation plan
	  OPT_CompilationPlan compPlan = 
	    new OPT_CompilationPlan(method, optimizationPlan, 
				    instrumentationPlan, options);

	  // If we have an inline oracle, use it!
	  if (offlineInlineOracle != null) {
	    compPlan.setInlineOracle(offlineInlineOracle);
	  }

	  cm = optCompileWithFallBack(method, compPlan);
	}
      } else {
          VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.BASELINE);
	  // compile with baseline compiler
	  cm = baselineCompile(method);
          VM_ControllerMemory.incrementNumBase();
      }
    }
    return cm;
  }
}
