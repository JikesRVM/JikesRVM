/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.adaptive.recompilation;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.controller.ControllerPlan;
import org.jikesrvm.adaptive.controller.RecompilationStrategy;
import org.jikesrvm.adaptive.recompilation.instrumentation.AOSInstrumentationPlan;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.baseline.BaselineCompiler;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanner;
import org.jikesrvm.runtime.Magic;

/**
 * Runtime system support for using invocation counters in baseline
 * compiled code to select methods for optimizing recompilation
 * by the adaptive system.  Bypasses the normal controller logic:
 * If an invocation counter trips, then the method is enqueued for
 * recompilation at a default optimization level.
 */
public final class InvocationCounts {

  private static int[] counts;
  private static boolean[] processed;

  public static synchronized void allocateCounter(int id) {
    if (counts == null) {
      counts = new int[id + 500];
      processed = new boolean[counts.length];
    }
    if (id >= counts.length) {
      int newSize = counts.length * 2;
      if (newSize <= id) newSize = id + 500;
      int[] tmp = new int[newSize];
      System.arraycopy(counts, 0, tmp, 0, counts.length);
      boolean[] tmp2 = new boolean[newSize];
      System.arraycopy(processed, 0, tmp2, 0, processed.length);
      Magic.sync();
      counts = tmp;
      processed = tmp2;
    }
    counts[id] = Controller.options.INVOCATION_COUNT_THRESHOLD;
  }

  /**
   * Called from baseline compiled code when a method's invocation counter
   * becomes negative and thus must be handled
   */
  static synchronized void counterTripped(int id) {
    counts[id] = 0x7fffffff; // set counter to max int to avoid lots of redundant calls.
    if (processed[id]) return;
    processed[id] = true;
    CompiledMethod cm = CompiledMethods.getCompiledMethod(id);
    if (cm == null) return;
    if (VM.VerifyAssertions) VM._assert(cm.getCompilerType() == CompiledMethod.BASELINE);
    NormalMethod m = (NormalMethod) cm.getMethod();
    CompilationPlan compPlan = new CompilationPlan(m, _optPlan, null, _options);
    ControllerPlan cp =
        new ControllerPlan(compPlan, Controller.controllerClock, id, 2.0, 2.0, 2.0); // 2.0 is a bogus number....
    cp.execute();
  }

  /**
   * Create the compilation plan according to the default set
   * of <optimization plan, options> pairs
   */
  public static CompilationPlan createCompilationPlan(NormalMethod method) {
    return new CompilationPlan(method, _optPlan, null, _options);
  }

  public static CompilationPlan createCompilationPlan(NormalMethod method, AOSInstrumentationPlan instPlan) {
    return new CompilationPlan(method, _optPlan, instPlan, _options);
  }

  /**
   *  Initialize the recompilation strategy.
   *
   *  Note: This uses the command line options to set up the
   *  optimization plans, so this must be run after the command line
   *  options are available.
   */
  public static void init() {
    createOptimizationPlan();
    BaselineCompiler.options.INVOCATION_COUNTERS = true;
  }

  private static OptimizationPlanElement[] _optPlan;
  private static OptOptions _options;

  /**
   * Create the default set of <optimization plan, options> pairs
   * Process optimizing compiler command line options.
   */
  static void createOptimizationPlan() {
    _options = new OptOptions();

    int optLevel = Controller.options.INVOCATION_COUNT_OPT_LEVEL;
    String[] optCompilerOptions = Controller.getOptCompilerOptions();
    _options.setOptLevel(optLevel);
    RecompilationStrategy.processCommandLineOptions(_options, optLevel, optLevel, optCompilerOptions);
    _optPlan = OptimizationPlanner.createOptimizationPlan(_options);
  }

}
