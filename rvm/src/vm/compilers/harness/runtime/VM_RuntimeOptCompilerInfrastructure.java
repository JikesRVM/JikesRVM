/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/* A place to put code common to all runtime compilers that use the OPT
 * compiler, such as the adaptive and optimizing-only runtime compilers.
 *
 * @author Michael Hind
 * @author Dave Grove
 */
class VM_RuntimeOptCompilerInfrastructure extends VM_RuntimeCompilerInfrastructure {
  
  // is the opt compiler usable?
  protected static boolean compilerEnabled;  
  
  // is opt compiler currently in use?
  // This flag is used to detect/avoid recursive opt compilation.
  // We assume that thread-level synchronization is handled externally
  // (currently by acquring VM_ClassLoader.lock).
  // Therefore at most one thread can be reading/writing compilationInProgress
  // and no further synchronization is required here.
  // NOTE: This code can be quite subtle, so please be absolutely sure
  // you know what you're doing before modifying it!!!
  protected static boolean compilationInProgress; 
  
  // Cache objects needed to cons up compilation plans
  protected static OPT_Options options;
  protected static OPT_OptimizationPlanElement[] optimizationPlan;

  // Perform intitialization for OPT compiler
  static void boot() throws OPT_OptimizingCompilerException {
    options = new OPT_Options();
    setNoCacheFlush(options);
    OPT_Compiler.init(options);
    optimizationPlan = OPT_OptimizationPlanner.createOptimizationPlan(options);

    // when we reach here the OPT compiler is enabled.
    compilerEnabled = true;
  }

  static void initializeMeasureCompilation() {
    VM_Callbacks.addExitMonitor(new VM_RuntimeCompilerInfrastructure());

    // send a message to all phases letting them update themselves
    OPT_OptimizationPlanner.initializeMeasureCompilation();
  }


  // attempt to compile the passed method with the OPT_Compiler.
  // Don't handle OPT_OptimizingCompilerExceptions (leave it up to caller to decide what to do)
  // Precondition: compilationInProgress "lock" has been acquired
  private static VM_CompiledMethod optCompile(VM_Method method, OPT_CompilationPlan plan) throws OPT_OptimizingCompilerException {
    if (VM.VerifyAssertions) VM.assert(compilationInProgress, "Failed to acquire compilationInProgress \"lock\"");
    
    Timer timer = null; // Only used if VM.MeasureCompilation 
    if (VM.MeasureCompilation) {
      timer = new Timer();
      timer.start();
    }
      
    VM_CompiledMethod cm = OPT_Compiler.compile(plan);
      
    if (VM.MeasureCompilation) {
      timer.finish();
      record(OPT_COMPILER, method, cm, timer);
    }

    return cm;
  }
  

  // These methods are safe to invoke from VM_RuntimeCompiler.compile
  // tries to compile the passed method with the OPT_Compiler.  If
  // this fails we will use the quicker compiler (baseline for now)
  // The following is carefully crafted to avoid (infinte) recursive opt compilation
  // for all combinations of bootimages & lazy/eager compilation.
  // Be absolutely sure you know what you're doing before changing it !!!
  static VM_CompiledMethod optCompileWithFallBack(VM_Method method) {
    if (compilationInProgress) {
      return fallback(method);
    } else {
      try {
	compilationInProgress = true;
	// Use a default compilation plan.
	OPT_CompilationPlan plan = new OPT_CompilationPlan(method, optimizationPlan, null, options);

	return optCompileWithFallBackInternal(method, plan);
      } finally {
	compilationInProgress = false;
      }
    }
  }
  static VM_CompiledMethod optCompileWithFallBack(VM_Method method, OPT_CompilationPlan plan) {
    if (compilationInProgress) {
      return fallback(method);
    } else {
      try {
	compilationInProgress = true;
	return optCompileWithFallBackInternal(method, plan);
      } finally {
	compilationInProgress = false;
      }
    }
  }
  //-#if RVM_WITH_SPECIALIZATION
  static VM_CompiledMethod optCompileWithFallBack(VM_Method method, OPT_SpecializationGraphNode context) {
    if (compilationInProgress) {
      return fallback(method);
    } else {
      try {
	compilationInProgress = true;
	// Use a default compilation plan.
	OPT_CompilationPlan plan = new OPT_CompilationPlan(method, optimizationPlan, null, options, context);
	return optCompileWithFallBackInternal(method, plan);
      } finally {
	compilationInProgress = false;
      }
    }
  }
  //-#endif
  private static VM_CompiledMethod optCompileWithFallBackInternal(VM_Method method, OPT_CompilationPlan plan) {
    try {
      return optCompile(method, plan);
    } catch (OPT_OptimizingCompilerException e) {
      String msg = "VM_RuntimeCompiler: can't optimize \"" + method + "\" (error was: " + e + "): reverting to baseline compiler\n"; 
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
      return fallback(method);
    } 
  }


  // tries to compile the passed method with the OPT_Compiler.
  // It will install the new compiled method via replaceCompiledMethod if sucessful.
  // Returns the CMID of the new method if successful, -1 if the recompilation failed.
  // NOTE: the recompile method should never be invoked via VM_RuntimeCompiler.compile;
  // it does not have sufficient guards against recursive recompilation.
  static int recompileWithOpt(OPT_CompilationPlan plan) {
    if (compilationInProgress) {
      return -1;
    } else {
      try {
	compilationInProgress = true;
	VM_CompiledMethod cm = optCompile(plan.method, plan);
	try {
	  plan.method.replaceCompiledMethod(cm);
	} catch (Throwable e) {
	  String msg = "Failure in VM_Method.replaceCompiledMethod (via recompileWithOpt): while replacing \"" + plan.method + "\" (error was: " + e + ")\n"; 
	  if (plan.options.ERRORS_FATAL) {
	    e.printStackTrace();
	    VM.sysFail(msg);
	  } else {
	    VM.sysWrite(msg);
	  }
	  return -1;
	}
	return cm.getId();
      } catch (OPT_OptimizingCompilerException e) {
	String msg = "Optimizing compiler (via recompileWithOpt): can't optimize \"" + plan.method + "\" (error was: " + e + ")\n"; 
	if (e.isFatal && plan.options.ERRORS_FATAL) {
	  e.printStackTrace();
	  VM.sysFail(msg);
	} else {
	  VM.sysWrite(msg);
	}
	return -1;
      } finally {
	compilationInProgress = false;
      }
    }
  }

  // Wrapper class for those callers who don't want to make optimization plans
  //
  static int recompileWithOpt(VM_Method method) {
    OPT_CompilationPlan plan = new OPT_CompilationPlan(method, optimizationPlan, null, options);
    return recompileWithOpt(plan);
  }

  // This method is safe to invoke from VM_RuntimeCompiler.compile
  protected static VM_CompiledMethod fallback(VM_Method method) {
    // call the inherited method "baselineCompile"
    return baselineCompile(method);
  }

  static VM_GCMapIterator createGCMapIterator(int[] registerLocations) {
    return new VM_OptGCMapIterator(registerLocations);
  }

  // Detect if we're running on a uniprocessor and optimize accordingly.
  // One needs to call this method each time a command line argument is changed
  // and each time an OPT_Options object is created.
  static void setNoCacheFlush(OPT_Options options) {
    if (options.DETECT_UNIPROCESSOR) {
      if (VM.sysCall0(VM_BootRecord.the_boot_record.sysNumProcessorsIP) == 1) {
	options.NO_CACHE_FLUSH = true;
      }
    }
  }
  
  // A hook called from VM_CompilationProfiler to allow in depth compiler subsystem reports
  static void detailedCompilationReport(boolean explain) {
    // If/when the baseline compiler gets these, invoke them here.
    
    // Get the opt's report
    VM_Type theType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("LOPT_OptimizationPlanner;"));
    if (theType.asClass().isInitialized()) {
      OPT_OptimizationPlanner.generateOptimizingCompilerSubsystemReport(explain);
    } else {
      VM.sysWrite("\n\tNot generating Optimizing Compiler SubSystem Report because \n");
      VM.sysWrite("\tthe opt compiler was never invoked.\n\n");
    }
  }
}
