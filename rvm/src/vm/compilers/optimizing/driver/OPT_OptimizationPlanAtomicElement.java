/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

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
   * Accumulated time spent in the element.
   */
  VM_Stopwatch mySW;

  /**
   * Create a plan element corresponding to a particular compiler phase.
   * @param   p
   */
  OPT_OptimizationPlanAtomicElement (OPT_CompilerPhase p) {
    myPhase = p;
  }

  /**
   * Update this phase to support the measuring of compilation
   */
  public void initializeForMeasureCompilation() {
    mySW = new VM_Stopwatch();
  }

  /**
   * Determine, possibly by consulting the passed options object,
   * if this optimization plan element should be performed.
   * 
   * @param options The OPT_Options object for the current compilation.
   * @return true if the plan element should be performed.
   */
  boolean shouldPerform (OPT_Options options) {
    return  myPhase.shouldPerform(options);
  }

  /**
   * Do the work represented by this element in the optimization plan.
   * The assumption is that the work will modify the IR in some way.
   * 
   * @param ir The OPT_IR object to work with.
   */
  void perform (OPT_IR ir) {
    if (VM.MeasureCompilation && VM.runningVM) {
      mySW.start();
    }
    myPhase.newExecution(ir).performPhase(ir);
    if (VM.MeasureCompilation && VM.runningVM)
      mySW.stop();
  }

  /**
   * Generate (to the sysWrite stream) a report of the
   * time spent performing this element of the optimization plan. 
   *
   * @param indent Number of spaces to indent report.
   * @param timeCol Column number of time portion of report.
   * @param totalTime Total opt compilation time in seconds.
   */
  void reportStats (int indent, int timeCol, double totalTime) {
    if (mySW == null || mySW.count == 0)
      return;
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
    prettyPrintTime(mySW.elapsedTime, totalTime);
  }

  /**
   * Report the total time spent executing the PlanElement
   * @return time spend in the plan (in seconds)
   */
  double elapsedTime () {
    if (mySW == null)
      return  0.0; 
    else 
      return  mySW.elapsedTime;
  }
}
