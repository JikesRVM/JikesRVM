/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.opt.*;
import com.ibm.JikesRVM.classloader.VM_NativeMethod;
import com.ibm.JikesRVM.classloader.VM_NormalMethod;

/**
 * Use optimizing compiler to compile code at run time.
 *
 * @author Stephen Fink
 * @author David Grove
 */
public class VM_RuntimeCompiler extends VM_RuntimeOptCompilerInfrastructure {

  private static String[] earlyArgs = new String[0];

  public static void boot() {
    VM.sysWrite("VM_RuntimeCompiler: boot (opt compiler)\n");
    VM_RuntimeOptCompilerInfrastructure.boot(); 
    // NOTE: VM_RuntimeOptCompilerInfrastructure.boot() has set compilerEnabled to true
    for (int i=0; i<earlyArgs.length; i++) {
      processCommandLineArg(earlyArgs[i]);
    }
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
	VM.sysExit(VM.exitStatusBogusCommandLineArg);
      }
    } else {
      String[] tmp = new String[earlyArgs.length+1];
      for (int i=0; i<earlyArgs.length; i++) {
	tmp[i] = earlyArgs[i];
      }
      earlyArgs = tmp;
      earlyArgs[earlyArgs.length-1] = arg;
    }
  }

  // tries to compile the passed method with the OPT_Compiler.
  // if this fails we use the fallback compiler (baseline for now)
  public static VM_CompiledMethod compile(VM_NormalMethod method) {
    if (!compilerEnabled                          // opt compiler isn't initialized yet
	|| method.isClassInitializer()            // will only run once: don't bother optimizing
	|| VM_Thread.getCurrentThread().hardwareExceptionRegisters.inuse // exception in progress. can't use opt compiler: it uses exceptions and runtime doesn't support multiple pending (undelivered) exceptions [--DL]
	) {
      return fallback(method);
    } else {
      if (!preloadChecked) {
	preloadChecked = true;			// prevent subsequent calls
	if (options.PRELOAD_CLASS != null) {
	  compilationInProgress = true;		// use baseline during preload
	  try {
	    OPT_Compiler.preloadSpecialClass(options);
	  } finally {
	    compilationInProgress = false;
	  }
	}
      }
      return VM_RuntimeOptCompilerInfrastructure.optCompileWithFallBack(method);
    }
  }

  public static VM_CompiledMethod compile(VM_NativeMethod method) {
    return jniCompile(method);
  }

}
