/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Baseline compiler - platform independent version 
 *   This is the super class of the platform dependent versions
 *
 * @author Janice Shepherd
 */

class VM_BaselineCompiler {


  static VM_BASEOptions options;                   //  Options used during base compiler execution
  static VM_BASEOptions setUpOptions;              //  Holds the options as the command line is being
                                                   //  processed. 

  /**
   * Create a clean version of the two option sets (options for use  and setUpOptions for 
   */
  static void bootOptions() {
    options = new VM_BASEOptions();
    setUpOptions = new VM_BASEOptions();
  }

  /**
   * After all the command line options have been processed, set up the official version of the options
   * that will be used during execution. Do any error checks that are needed.
   */
  static void postBootOptions() {
    // If the user has requested machine code dumps, then force a test of method to print option so
    // extra classes needed to process matching will be loaded and compiled upfront. Thus avoiding getting
    // stuck looping by just asking if we have a match in the middle of compilation. Pick an obsure string
    // for the check.
    if (setUpOptions.PRINT_MACHINECODE) {
      if (setUpOptions.hasMETHOD_TO_PRINT() && setUpOptions.fuzzyMatchMETHOD_TO_PRINT("???")) {
	VM.sysWrite("??? is not a sensible string to specify for method name");
      }
    }
    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    //-#else
    if (setUpOptions.PRELOAD_CLASS != null) {
      VM.sysWrite("Option preload_class should only be used when the optimizing compiler is the runtime");
      VM.sysWrite(" compiler or in an adaptive system\n");
      VM.sysExit(1);
    }
    //-#endif

    options = setUpOptions;   // Switch to the version with the user command line processed
  }

  /**
   * Process a command line argument
   * @param prefix
   * @param arg     Command line argument with prefix stripped off
   */
  static void processCommandLineArg(String prefix, String arg) {
    if (setUpOptions != null) {
      if (setUpOptions.processAsOption(prefix, arg)) {
	return;
      } else {
	VM.sysWrite("VM_BaselineCompiler: Unrecognized argument \""+ arg + "\"\n");
	VM.sysExit(1);
      }
    } else {
	VM.sysWrite("VM_BaselineCompiler: Compiler setUpOptions not enabled; Ignoring argument \""+ arg + "\"\n");
    }

  }
}
