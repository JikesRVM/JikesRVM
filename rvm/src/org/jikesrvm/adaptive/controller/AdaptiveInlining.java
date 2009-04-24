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
package org.jikesrvm.adaptive.controller;

import org.jikesrvm.adaptive.database.callgraph.PartialCallGraph;
import org.jikesrvm.adaptive.measurements.RuntimeMeasurements;
import org.jikesrvm.adaptive.measurements.listeners.CallDensityListener;
import org.jikesrvm.adaptive.util.AOSOptions;

/**
 * Collection of static methods to assist with adaptive inlining.
 */
public class AdaptiveInlining {

  /**
   * A listener that tracks and can report the call density
   * of the program (fraction of yieldpoints that are taken
   * at prologue/epilogues).
   */
  private static final CallDensityListener callDensityListener = new CallDensityListener();

  /**
   * Set parameters.
   * Must be called after parsing command-line.
   */
  static void boot(AOSOptions options) {
    // create and register the dcg as a decayable object
    // Give it an initial seed weight that approximates the old step
    // function for edge hotness.  The intent is that early on
    // (until decay decreases this initial weight), we are conservative in
    // marking an edge as hot.
    Controller.dcg = new PartialCallGraph(options.INLINE_AI_SEED_MULTIPLIER * (1 / options.INLINE_AI_HOT_CALLSITE_THRESHOLD));
    RuntimeMeasurements.registerDecayableObject(Controller.dcg);

    // Track call density: fraction of timer interrupts taken in prologue/epilogue
    RuntimeMeasurements.installTimerNullListener(callDensityListener);
    callDensityListener.activate();

    if (options.GATHER_PROFILE_DATA) {
      RuntimeMeasurements.registerReportableObject(Controller.dcg);
    }
  }

  public static double adjustedWeight(double weight) {
    return weight / (Controller.dcg.getTotalEdgeWeights()) * callDensityListener.callDensity();
  }
}
