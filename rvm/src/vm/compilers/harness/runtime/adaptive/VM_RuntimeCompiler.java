/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.opt.*;
import com.ibm.JikesRVM.adaptive.*;

/**
 * The adaptive version of the runtime compiler.
 * 
 * @author Matthew Arnold
 * @author Dave Grove
 * @author Michael Hind
 */
public class VM_RuntimeCompiler extends VM_RuntimeOptCompilerInfrastructure {
  public static OPT_InlineOracle offlineInlineOracle;
  private static String[] earlyArgs = new String[0];

  public static void boot() { 
    VM.sysWrite("VM_RuntimeCompiler: boot (adaptive compilation)\n");

    VM_RuntimeOptCompilerInfrastructure.boot(); 
    // NOTE: VM_RuntimeOptCompilerInfrastructure.boot() has set compilerEnabled to true
    for (int i=0; i<earlyArgs.length; i++) {
      processCommandLineArg(earlyArgs[i]);
    }
  }
  
  public static void processCommandLineArg(String arg) {
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
	String[] tmp = new String[earlyArgs.length+1];
	for (int i=0; i<earlyArgs.length; i++) {
	  tmp[i] = earlyArgs[i];
	}
	earlyArgs = tmp;
	earlyArgs[earlyArgs.length-1] = arg;
      }
    } else {
      VM_BaselineCompiler.processCommandLineArg("-X:aos:irc", arg);
    }
  }
  
  // This will be called by the classLoader when we need to compile a method
  // for the first time.
  public static VM_CompiledMethod compile(VM_Method method) {
    if (method.isNative()) {
      return jniCompile(method);
    } 
    
    VM_CompiledMethod cm;
    if (!VM_Controller.enabled) {
      // System still early in boot process; compile with baseline compiler
      cm = baselineCompile(method);
      VM_ControllerMemory.incrementNumBase();
    } else {
      if (!preloadChecked) {
	preloadChecked = true;			// prevent subsequent calls
	// N.B. This will use irc options
	if (VM_BaselineCompiler.options.PRELOAD_CLASS != null) {
	  compilationInProgress = true;		// use baseline during preload
	  // Other than when boot options are requested (processed during preloadSpecialClass
	  // It is hard to communicate options for these special compilations. Use the 
	  // default options and at least pick up the verbose if requested for base/irc
	  OPT_Options tmpoptions = (OPT_Options)options.clone();
	  tmpoptions.PRELOAD_CLASS = VM_BaselineCompiler.options.PRELOAD_CLASS;
	  tmpoptions.PRELOAD_AS_BOOT = VM_BaselineCompiler.options.PRELOAD_AS_BOOT;
	  if (VM_BaselineCompiler.options.PRINT_METHOD) {
	    tmpoptions.PRINT_METHOD = true;
	  } else {
	    tmpoptions = options;
	  }
          OPT_Compiler.preloadSpecialClass(tmpoptions);
	  compilationInProgress = false;
	}
      }
      if (VM_Controller.options.optOnly()) {
	if (// will only run once: don't bother optimizing
	    method.isClassInitializer() || 
	    // exception in progress. can't use opt compiler: 
            // it uses exceptions and runtime doesn't support 
            // multiple pending (undelivered) exceptions [--DL]
	    VM_Thread.getCurrentThread().hardwareExceptionRegisters.inuse) {
	  // compile with baseline compiler
	  cm = baselineCompile(method);
          VM_ControllerMemory.incrementNumBase();
	} else { // compile with opt compiler
	  VM_AOSInstrumentationPlan instrumentationPlan = 
	    new VM_AOSInstrumentationPlan(VM_Controller.options, method);
	  OPT_CompilationPlan compPlan = 
	    new OPT_CompilationPlan(method, optimizationPlan, 
				    instrumentationPlan, options);
	  if (offlineInlineOracle != null) {
	    compPlan.setInlineOracle(offlineInlineOracle);
	  }
	  cm = optCompileWithFallBack(method, compPlan);
	}
      } else {
	// compile with baseline compiler
	cm = baselineCompile(method);
	VM_ControllerMemory.incrementNumBase();
      }
    }
    return cm;
  }
}
