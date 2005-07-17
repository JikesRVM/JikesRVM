/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.*;

/**
 * Runtime system support for using invocation counters in baseline 
 * compiled code to select methods for optimizing recompilation
 * by the adaptive system.  Bypasses the normal controller logic:
 * If an invocation counter trips, then the method is enqueued for
 * recompilation at a default optimization level.
 * 
 * @author Dave Grove
 * @modified Peter Sweeney 8/2003 to process command line arguments
 */
public final class VM_InvocationCounts {

  private static int[] counts;
  private static boolean[] processed;

  public static synchronized void allocateCounter(int id) {
    if (counts == null) {
      counts = new int[id+500];
      processed = new boolean[counts.length];
    }
    if (id >= counts.length) {
      int newSize = counts.length*2;
      if (newSize <= id) newSize = id+500;
      int[] tmp = new int[newSize];
      System.arraycopy(counts, 0, tmp, 0, counts.length);
      boolean tmp2[] = new boolean[newSize];
      System.arraycopy(processed, 0, tmp2, 0, processed.length);
      VM_Magic.sync();
      counts = tmp;
      processed = tmp2;
    }
    counts[id] = VM_Controller.options.INVOCATION_COUNT_THRESHOLD;
  }

  /**
   * Called from baseline compiled code when a method's invocation counter
   * becomes negative and thus must be handled
   */
  static synchronized void counterTripped(int id) {
    counts[id] = 0x7fffffff; // set counter to max int to avoid lots of redundant calls.
    if (processed[id]) return;
    processed[id] = true;
    VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(id);
    if (cm == null) return;
    if (VM.VerifyAssertions) VM._assert(cm.getCompilerType() == VM_CompiledMethod.BASELINE);
    VM_NormalMethod m = (VM_NormalMethod)cm.getMethod();
    OPT_CompilationPlan compPlan = new OPT_CompilationPlan(m, _optPlan, null, _options);
    VM_ControllerPlan cp = new VM_ControllerPlan(compPlan, VM_Controller.controllerClock, 
                                                 id, 2.0, 2.0, 2.0); // 2.0 is a bogus number....
    cp.execute();
  }

  /**
   * Create the compilation plan according to the default set 
   * of <optimization plan, options> pairs
   */
  public static OPT_CompilationPlan createCompilationPlan(VM_NormalMethod method) {
    return new OPT_CompilationPlan(method, _optPlan, null, _options);
  }

  public static OPT_CompilationPlan createCompilationPlan(VM_NormalMethod method, VM_AOSInstrumentationPlan instPlan) {
    return new OPT_CompilationPlan(method, _optPlan, instPlan, _options);
  }

  /**
   *  Initialize the recompilation strategy.
   *
   *  Note: This uses the command line options to set up the
   *  optimization plans, so this must be run after the command line
   *  options are available.  
   */
  static void init() {
    createOptimizationPlan();
    VM_BaselineCompiler.options.INVOCATION_COUNTERS=true;
  }

  private static  OPT_OptimizationPlanElement[] _optPlan;
  private static OPT_Options _options;
  /**
   * Create the default set of <optimization plan, options> pairs
   * Process optimizing compiler command line options.
   */
  static void createOptimizationPlan() {
    _options = new OPT_Options();

    int optLevel = VM_Controller.options.INVOCATION_COUNT_OPT_LEVEL;
    String[] optCompilerOptions = VM_Controller.getOptCompilerOptions();
    _options.setOptLevel(optLevel);
    VM_RecompilationStrategy.processCommandLineOptions(_options,optLevel,optLevel,optCompilerOptions);
    _optPlan = OPT_OptimizationPlanner.createOptimizationPlan(_options);
  }

}
