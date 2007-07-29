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

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.runtime.VM_Time;
import org.jikesrvm.scheduler.VM_Scheduler;

/**
 * An element in the opt compiler's optimization plan
 * that consists of a single OPT_CompilerPhase.
 *
 * <p> NOTE: Instances of this class are
 *       held in <code> OPT_OptimizationPlanner.masterPlan </code>
 *       and thus represent global state.
 *       It is therefore incorrect for any per-compilation
 *       state to be stored in an instance field of
 *       one of these objects.
 */
public final class OPT_OptimizationPlanAtomicElement extends OPT_OptimizationPlanElement {
  /**
   * The phase to be performed.
   */
  private OPT_CompilerPhase myPhase;
  /**
   * Accumulated nanoseconds spent in the element.
   */
  long phaseNanos;

  /**
   * Counters to be used by myPhase to gather phase specific stats.
   */
  double counter1, counter2;

  /**
   * Create a plan element corresponding to a particular compiler phase.
   * @param   p
   */
  public OPT_OptimizationPlanAtomicElement(OPT_CompilerPhase p) {
    myPhase = p;
    p.setContainer(this);
  }

  /**
   * Update this phase to support the measuring of compilation
   */
  public void initializeForMeasureCompilation() {
    counter1 = 0;
    counter2 = 0;
  }

  /**
   * Determine, possibly by consulting the passed options object,
   * if this optimization plan element should be performed.
   *
   * @param options The OPT_Options object for the current compilation.
   * @return true if the plan element should be performed.
   */
  public boolean shouldPerform(OPT_Options options) {
    return myPhase.shouldPerform(options);
  }

  /**
   * Do the work represented by this element in the optimization plan.
   * The assumption is that the work will modify the IR in some way.
   *
   * @param ir The OPT_IR object to work with.
   */
  public void perform(OPT_IR ir) {
    long start = 0;
    if (VM.MeasureCompilation && VM.runningVM) {
      start = VM_Scheduler.getCurrentThread().accumulateNanos();
    }
    OPT_CompilerPhase cmpPhase = myPhase.newExecution(ir);
    cmpPhase.setContainer(this);
    cmpPhase.performPhase(ir);
    if (VM.MeasureCompilation && VM.runningVM) {
      long end = VM_Scheduler.getCurrentThread().accumulateNanos();
      phaseNanos += end - start;
    }
  }

  /**
   * @return a String which is the name of the phase.
   */
  public String getName() {
    return myPhase.getName();
  }

  /**
   * Generate (to the sysWrite stream) a report of the
   * time spent performing this element of the optimization plan.
   *
   * @param indent Number of spaces to indent report.
   * @param timeCol Column number of time portion of report.
   * @param totalTime Total opt compilation time in ms.
   */
  public void reportStats(int indent, int timeCol, double totalTime) {
    if (phaseNanos == 0) return;
    int curCol = 0;
    for (curCol = 0; curCol < indent; curCol++) {
      VM.sysWrite(" ");
    }
    String name = myPhase.getName();
    int namePtr = 0;
    while (curCol < timeCol && namePtr < name.length()) {
      VM.sysWrite(name.charAt(namePtr));
      namePtr++;
      curCol++;
    }
    while (curCol < timeCol) {
      VM.sysWrite(" ");
      curCol++;
    }
    double myTime = VM_Time.nanosToMillis(phaseNanos);
    prettyPrintTime(myTime, totalTime);
    myPhase.reportAdditionalStats();
    VM.sysWriteln();
  }

  /**
   * Report the total time spent executing the PlanElement
   * @return time spend in the plan (in ms)
   */
  public double elapsedTime() {
    return VM_Time.nanosToMillis(phaseNanos);
  }
}
