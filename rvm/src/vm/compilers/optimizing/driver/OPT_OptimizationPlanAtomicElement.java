/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.OPT_IR;

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
 *
 * @author Steve Fink
 * @author Dave Grove
 * @author Michael Hind
 */
public final class OPT_OptimizationPlanAtomicElement extends 
                                          OPT_OptimizationPlanElement {
  /**
   * The phase to be performed.
   */
  private OPT_CompilerPhase myPhase;
  /**
   * Accumulated cycles spent in the element.
   */
  protected long cycles;

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
    myPhase.container = this;
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
      start = VM_Thread.getCurrentThread().accumulateCycles();
    }
    myPhase.newExecution(ir).performPhase(ir);
    if (VM.MeasureCompilation && VM.runningVM) {
      long end = VM_Thread.getCurrentThread().accumulateCycles();
      cycles += end - start;
    }
  }

  /**
   * Generate (to the sysWrite stream) a report of the
   * time spent performing this element of the optimization plan. 
   *
   * @param indent Number of spaces to indent report.
   * @param timeCol Column number of time portion of report.
   * @param totalTime Total opt compilation time in seconds.
   */
  public void reportStats(int indent, int timeCol, double totalTime) {
    if (cycles == 0) return;
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
    double myTime = VM_Time.cyclesToMillis(cycles) / 1000;
    prettyPrintTime(myTime, totalTime);
    myPhase.reportAdditionalStats();
    VM.sysWriteln();
  }

  /**
   * Report the total time spent executing the PlanElement
   * @return time spend in the plan (in seconds)
   */
  public double elapsedTime() {
    return VM_Time.cyclesToMillis(cycles) / 1000;
  }
}
