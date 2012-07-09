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
package org.jikesrvm.compilers.opt.driver;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * An element in the opt compiler's optimization plan.
 * <p>
 * NOTE: Instances of subclasses of this class are
 *       held in OptimizationPlanner.masterPlan
 *       and thus represent global state.
 *       It is therefore incorrect for any per-compilation
 *       state to be stored in an instance field of
 *       one of these objects.
 * <p>
 * TODO: refactor the optimization plan elements and compiler phases
 */
public abstract class OptimizationPlanElement {

  /**
   * Determine, possibly by consulting the passed options object,
   * if this optimization plan element should be performed.
   *
   * @param options The Options object for the current compilation.
   * @return {@code true} if the plan element should be performed.
   */
  public abstract boolean shouldPerform(OptOptions options);

  /**
   * Do the work represented by this element in the optimization plan.
   * The assumption is that the work will modify the IR in some way.
   *
   * @param ir The IR object to work with.
   */
  public abstract void perform(IR ir);

  /**
   * @return a String which is the name of the phase.
   */
  public abstract String getName();

  /**
   * This method is called to initialize the optimization plan support
   *  measuring compilation.
   */
  public abstract void initializeForMeasureCompilation();

  /**
   * Generate (to the sysWrite stream) a report of the
   * time spent performing this element of the optimization plan.
   *
   * @param indent Number of spaces to indent report.
   * @param timeCol Column number of time portion of report.
   * @param totalTime Total opt compilation time in seconds.
   */
  public abstract void reportStats(int indent, int timeCol, double totalTime);

  /**
   * Report the elapsed time spent in the PlanElement
   * @return time spend in the plan (in ms)
   */
  public abstract double elapsedTime();

  /**
   * Helper function for <code> reportStats </code>
   */
  protected void prettyPrintTime(double time, double totalTime) {
    int t = (int) time;
    if (t < 1000000) {
      VM.sysWrite(" ");
    }
    if (t < 100000) {
      VM.sysWrite(" ");
    }
    if (t < 10000) {
      VM.sysWrite(" ");
    }
    if (t < 1000) {
      VM.sysWrite(" ");
    }
    if (t < 100) {
      VM.sysWrite(" ");
    }
    if (t < 10) {
      VM.sysWrite(" ");
    }
    VM.sysWrite(t);
    if (time / totalTime > 0.10) {
      VM.sysWrite("    ");
    } else {
      VM.sysWrite("     ");
    }
    VM.sysWrite(time / totalTime * 100, 2);
    VM.sysWrite("%");
  }
}
