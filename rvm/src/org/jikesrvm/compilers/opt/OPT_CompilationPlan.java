/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.ir.OPT_IR;

/*
 * OPT_CompilationPlan.java
 *
 *
 * An instance of this class acts instructs the optimizing
 * compiler how to compile the specified method.
 */
public final class OPT_CompilationPlan {
  /**
   * The method to be compiled.
   */
  public VM_NormalMethod method;

  public VM_NormalMethod getMethod() {
    return method;
  }

  /**
   * The specialized parameters to use in place of those defined in method.
   */
  public VM_TypeReference[] params;

  /**
   * The OPT_OptimizationPlanElements to be invoked during compilation.
   */
  public OPT_OptimizationPlanElement[] optimizationPlan;
  /**
   * The instrumentation plan for the method.
   */
  public OPT_InstrumentationPlan instrumentationPlan;
  /**
   * The oracle to be consulted for all inlining decisions.
   */
  public OPT_InlineOracle inlinePlan;
  /**
   * The OPT_Options object that contains misc compilation control data
   */
  public OPT_Options options;

  /**
   * Whether this compilation is for analysis only?
   */
  public boolean analyzeOnly;

  public boolean irGeneration;

  /**
   * Construct a compilation plan
   *
   * @param m    The VM_NormalMethod representing the source method to be compiled
   * @param pms  The specialized parameters to use in place of those defined in method
   * @param op   The optimization plan to be executed on m
   * @param mp   The instrumentation plan to be executed on m
   * @param opts The OPT_Options to be used for compiling m
   */
  public OPT_CompilationPlan(VM_NormalMethod m, VM_TypeReference[] pms, OPT_OptimizationPlanElement[] op, OPT_InstrumentationPlan mp,
                             OPT_Options opts) {
    method = m;
    params = pms;
    inlinePlan = new OPT_DefaultInlineOracle();
    optimizationPlan = op;
    instrumentationPlan = mp;
    options = opts;
  }

  /**
   * Construct a compilation plan
   *
   * @param m    The VM_NormalMethod representing the source method to be compiled
   * @param op   The optimization plan to be executed on m
   * @param mp   The instrumentation plan to be executed on m
   * @param opts The OPT_Options to be used for compiling m
   */
  public OPT_CompilationPlan(VM_NormalMethod m, OPT_OptimizationPlanElement[] op, OPT_InstrumentationPlan mp,
                             OPT_Options opts) {
    this(m, null, op, mp, opts);
  }

  /**
   * Construct a compilation plan
   * @param m    The VM_NormalMethod representing the source method to be compiled
   * @param op   A single optimization pass to execute on m
   * @param mp   The instrumentation plan to be executed on m
   * @param opts The OPT_Options to be used for compiling m
   */
  public OPT_CompilationPlan(VM_NormalMethod m, OPT_OptimizationPlanElement op, OPT_InstrumentationPlan mp,
                             OPT_Options opts) {
    this(m, new OPT_OptimizationPlanElement[]{op}, mp, opts);
  }

  /**
   * Set the inline oracle
   */
  public void setInlineOracle(OPT_InlineOracle o) {
    inlinePlan = o;
  }

  /**
   * Execute a compilation plan by executing each element
   * in the optimization plan.
   */
  public OPT_IR execute() {
    OPT_IR ir = new OPT_IR(method, this);

    // If there is instrumentation to perform, do some initialization
    if (instrumentationPlan != null) {
      instrumentationPlan.initInstrumentation(method);
    }
    for (OPT_OptimizationPlanElement element : optimizationPlan) {
      element.perform(ir);
    }
    // If instrumentation has occured, perform some
    // cleanup/finalization.  NOTE: This code won't execute when
    // compilation fails with an exception.  TODO: Figure out
    // whether this matters.
    if (instrumentationPlan != null) {
      instrumentationPlan.finalizeInstrumentation(method);
    }

    return ir;
  }
}
