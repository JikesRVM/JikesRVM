/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;
import java.util.*;
import java.io.*;

/**
 * Collection of static methods to assist with adaptive inlining.
 *
 * @author Stephen Fink
 * @modified Michael Hind
 * @modified Peter F. Sweeney
 * @modified Matthew Arnold
 * @modified Dave Grove
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
    VM_Controller.dcg = new VM_PartialCallGraph(options.AI_SEED_MULTIPLIER * (1/options.AI_CONTROL_POINT)); 
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

  public static boolean belowSizeThresholds(VM_MethodReference target, double targetWeight) {
    // TODO: this is complete crap.
    VM.sysWriteln("\tBOGUS IMPL OF belowSizeThresholds");
    VM_Method m = target.peekResolvedMethod();
    return m != null && (m instanceof VM_NormalMethod) && ((VM_NormalMethod)m).inlinedSizeEstimate() < 100;
  }

  
}
