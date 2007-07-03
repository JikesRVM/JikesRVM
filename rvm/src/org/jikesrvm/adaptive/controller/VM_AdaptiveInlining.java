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
package org.jikesrvm.adaptive.controller;

import org.jikesrvm.adaptive.database.callgraph.VM_PartialCallGraph;
import org.jikesrvm.adaptive.measurements.VM_RuntimeMeasurements;
import org.jikesrvm.adaptive.measurements.listeners.VM_CallDensityListener;
import org.jikesrvm.adaptive.util.VM_AOSOptions;

/**
 * Collection of static methods to assist with adaptive inlining.
 */
public class VM_AdaptiveInlining {

  /**
   * A listener that tracks and can report the call density
   * of the program (fraction of yieldpoints that are taken
   * at prologue/epilogues).
   */
  private static final VM_CallDensityListener callDensityListener = new VM_CallDensityListener();

  /**
   * Set parameters.
   * Must be called after parsing command-line.
   */
  static void boot(VM_AOSOptions options) {
    // create and register the dcg as a decayable object
    // Give it an initial seed weight that approximates the old step
    // function for edge hotness.  The intent is that early on
    // (until decay decreases this initial weight), we are conservative in
    // marking an edge as hot.
    VM_Controller.dcg = new VM_PartialCallGraph(options.AI_SEED_MULTIPLIER * (1 / options.AI_CONTROL_POINT));
    VM_RuntimeMeasurements.registerDecayableObject(VM_Controller.dcg);

    // Track call density: fraction of timer interrupts taken in prologue/epilogue
    VM_RuntimeMeasurements.installTimerNullListener(callDensityListener);
    callDensityListener.activate();

    if (options.GATHER_PROFILE_DATA) {
      VM_RuntimeMeasurements.registerReportableObject(VM_Controller.dcg);
    }
  }

  public static double adjustedWeight(double weight) {
    return weight / (VM_Controller.dcg.getTotalEdgeWeights()) * callDensityListener.callDensity();
  }
}
