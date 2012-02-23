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
package org.jikesrvm.adaptive.measurements.organizers;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.controller.HotMethodRecompilationEvent;
import org.jikesrvm.adaptive.measurements.RuntimeMeasurements;
import org.jikesrvm.adaptive.measurements.listeners.MethodListener;
import org.jikesrvm.adaptive.util.AOSLogging;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.NonMoving;

/**
 * An organizer for method listener information.
 * <p>
 * This organizer is designed to work well with non-decayed
 * cumulative method samples.  The basic idea is that each time
 * the sampling threshold is reached we update the accumulated method
 * sample data with the new data and then notify the controller of all
 * methods that were sampled in the current window.
 */
@NonMoving
public final class MethodSampleOrganizer extends Organizer {

  /**
   *  Filter out all opt-compiled methods that were compiled
   * at this level or higher.
   */
  private int filterOptLevel;

  /**
   * @param filterOptLevel   filter out all opt-compiled methods that
   *                         were compiled at this level or higher
   */
  public MethodSampleOrganizer(int filterOptLevel) {
    this.filterOptLevel = filterOptLevel;
  }

  /**
   * Initialization: set up data structures and sampling objects.
   */
  @Override
  public void initialize() {
    int numSamples = Controller.options.METHOD_SAMPLE_SIZE * RVMThread.availableProcessors;
    if (Controller.options.mlCBS()) {
      numSamples *= VM.CBSMethodSamplesPerTick;
    }
    MethodListener methodListener = new MethodListener(numSamples);
    listener = methodListener;
    listener.setOrganizer(this);

    if (Controller.options.mlTimer()) {
      RuntimeMeasurements.installTimerMethodListener(methodListener);
    } else if (Controller.options.mlCBS()) {
      RuntimeMeasurements.installCBSMethodListener(methodListener);
    } else {
      if (VM.VerifyAssertions) VM._assert(false, "Unexpected value of method_listener_trigger");
    }
  }

  /**
   * Method that is called when the sampling threshold is reached
   */
  void thresholdReached() {
    AOSLogging.logger.organizerThresholdReached();

    int numSamples = ((MethodListener) listener).getNumSamples();
    int[] samples = ((MethodListener) listener).getSamples();

    // (1) Update the global (cumulative) sample data
    Controller.methodSamples.update(samples, numSamples);

    // (2) Remove duplicates from samples buffer.
    //     NOTE: This is a dirty trick and may be ill-advised.
    //     Rather than copying the unique samples into a different buffer
    //     we treat samples as if it was a scratch buffer.
    //     NOTE: This is worse case O(numSamples^2) but we expect a
    //     significant number of duplicates, so it's probably better than
    //     the other obvious alternative (sorting samples).
    int uniqueIdx = 1;
    outer:
    for (int i = 1; i < numSamples; i++) {
      int cur = samples[i];
      for (int j = 0; j < uniqueIdx; j++) {
        if (cur == samples[j]) continue outer;
      }
      samples[uniqueIdx++] = cur;
    }

    // (3) For all samples in 0...uniqueIdx, if the method represented by
    //     the sample is compiled at an opt level below filterOptLevel
    //     then report it to the controller.
    for (int i = 0; i < uniqueIdx; i++) {
      int cmid = samples[i];
      double ns = Controller.methodSamples.getData(cmid);
      CompiledMethod cm = CompiledMethods.getCompiledMethod(cmid);
      if (cm != null) {         // not already obsoleted
        int compilerType = cm.getCompilerType();

        // Enqueue it unless it's either a trap method or already opt
        // compiled at filterOptLevel or higher.
        if (!(compilerType == CompiledMethod.TRAP ||
              (compilerType == CompiledMethod.OPT &&
               (((OptCompiledMethod) cm).getOptLevel() >= filterOptLevel)))) {
          HotMethodRecompilationEvent event = new HotMethodRecompilationEvent(cm, ns);
          Controller.controllerInputQueue.insert(ns, event);
          AOSLogging.logger.controllerNotifiedForHotness(cm, ns);
        }
      }
    }
  }
}
