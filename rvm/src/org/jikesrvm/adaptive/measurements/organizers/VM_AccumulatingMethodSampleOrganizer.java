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
package org.jikesrvm.adaptive.measurements.organizers;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.VM_Controller;
import org.jikesrvm.adaptive.database.methodsamples.VM_MethodCountData;
import org.jikesrvm.adaptive.measurements.VM_RuntimeMeasurements;
import org.jikesrvm.adaptive.measurements.listeners.VM_MethodListener;
import org.jikesrvm.adaptive.util.VM_AOSLogging;
import org.jikesrvm.scheduler.VM_Scheduler;

/**
 * An organizer for method listener information that
 * simply accumulates the samples into a private
 * VM_MethodCountData instance.
 *
 * This organizer is used to simply gather aggregate sample data and
 * report it.
 */
public final class VM_AccumulatingMethodSampleOrganizer extends VM_Organizer {

  private VM_MethodCountData data;

  public VM_AccumulatingMethodSampleOrganizer() {
    makeDaemon(true);
  }

  /**
   * Initialization: set up data structures and sampling objects.
   */
  public void initialize() {
    data = new VM_MethodCountData();
    int numSamples = VM_Controller.options.METHOD_SAMPLE_SIZE * VM_Scheduler.numProcessors;
    if (VM_Controller.options.mlCBS()) {
      numSamples *= VM.CBSMethodSamplesPerTick;
    }
    VM_MethodListener methodListener = new VM_MethodListener(numSamples);
    listener = methodListener;
    listener.setOrganizer(this);
    if (VM_Controller.options.mlTimer()) {
      VM_RuntimeMeasurements.installTimerMethodListener(methodListener);
    } else if (VM_Controller.options.mlCBS()) {
      VM_RuntimeMeasurements.installCBSMethodListener(methodListener);
    } else {
      if (VM.VerifyAssertions) VM._assert(false, "Unexpected value of method_listener_trigger");
    }
  }

  /**
   * Method that is called when the sampling threshold is reached
   */
  void thresholdReached() {
    VM_AOSLogging.organizerThresholdReached();
    int numSamples = ((VM_MethodListener) listener).getNumSamples();
    int[] samples = ((VM_MethodListener) listener).getSamples();
    data.update(samples, numSamples);
  }

  public void report() {
    VM.sysWrite("\nMethod sampler report");
    if (data != null) data.report();
  }
}
