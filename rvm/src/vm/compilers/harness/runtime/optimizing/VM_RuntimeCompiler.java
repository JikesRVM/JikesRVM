/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.opt.*;

import java.util.*;

/**
 * Use optimizing compiler to compile code at run time.
 *
 * @author Stephen Fink
 * @author David Grove
 */
public class VM_RuntimeCompiler extends VM_RuntimeOptCompilerInfrastructure {

  public static void boot() {
    VM.sysWrite("VM_RuntimeCompiler: boot (opt compiler)\n");
    try {
      VM_RuntimeOptCompilerInfrastructure.boot(); 
      Iterator i = earlyArgs.iterator();
      while (i.hasNext()) processCommandLineArg( (String) i.next() );
    } catch (OPT_OptimizingCompilerException e) {
      String msg = "VM_RuntimeCompiler: OPT_Compiler failed during initialization: "+e+"\n";
      if (e.isFatal && options.ERRORS_FATAL) {
	e.printStackTrace();
	VM.sysFail(msg);
      } else {
	VM.sysWrite(msg);
      }
    }
  }


  private static final HashSet earlyArgs = new HashSet(5);

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
	earlyArgs.add( arg );
    }
  }

  // tries to compile the passed method with the OPT_Compiler.
  // if this fails we use the fallback compiler (baseline for now)
  static VM_CompiledMethod compile(VM_Method method) {
    if (method.isNative()) {
      return jniCompile(method);
    } 

    if (!compilerEnabled                          // opt compiler isn't initialized yet
	|| !VM_Scheduler.allProcessorsInitialized // gc system isn't fully up: reduce memory load
	|| method.isClassInitializer()            // will only run once: don't bother optimizing
	|| VM_Thread.getCurrentThread().hardwareExceptionRegisters.inuse // exception in progress. can't use opt compiler: it uses exceptions and runtime doesn't support multiple pending (undelivered) exceptions [--DL]
	) {
      return fallback(method);
    } else {
      if (!preloadChecked) {
	preloadChecked = true;			// prevent subsequent calls
	if (options.PRELOAD_CLASS != null) {
	  compilationInProgress = true;		// use baseline during preload
          OPT_Compiler.preloadSpecialClass( options );
	  compilationInProgress = false;
	}
      }
      return VM_RuntimeOptCompilerInfrastructure.optCompileWithFallBack(method);
    }
  }

}
