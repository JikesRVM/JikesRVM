/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_CompiledMethods;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.opt.VM_OptCompiledMethod;

/**
 * An organizer for method listener information. 
 * <p>
 * This organizer is designed to work well with non-decayed 
 * cumulative method samples.  The basic idea is that each time 
 * the sampling threshold is reached we update the accumulated method 
 * sample data with the new data and then notify the controller of all 
 * methods that were sampled in the current window.
 * 
 * @author Dave Grove
 */
final class VM_MethodSampleOrganizer extends VM_Organizer {

  /**
   *  Filter out all opt-compiled methods that were compiled 
   * at this level or higher.
   */
  private int filterOptLevel;

  /**
   * @param listener         the associated listener
   * @param filterOptLevel   filter out all opt-compiled methods that 
   *                         were compiled at this level or higher
   */
  VM_MethodSampleOrganizer(int filterOptLevel) {
    this.filterOptLevel   = filterOptLevel;
    makeDaemon(true);
  }

  /**
   * Initialization: set up data structures and sampling objects.
   */
  public void initialize() {
    if (VM.LogAOSEvents) 
      VM_AOSLogging.methodSampleOrganizerThreadStarted(filterOptLevel);

    int numSamples = VM_Controller.options.METHOD_SAMPLE_SIZE * VM_Scheduler.numProcessors;
    VM_MethodListener methodListener = new VM_MethodListener(numSamples);
    listener = methodListener;
    listener.setOrganizer(this);
    
    VM_RuntimeMeasurements.installTimerMethodListener(methodListener);
  }

  /**
   * Method that is called when the sampling threshold is reached
   */
  void thresholdReached() {
    if (VM.LogAOSEvents) VM_AOSLogging.organizerThresholdReached();

    int numSamples = ((VM_MethodListener)listener).getNumSamples();
    int[] samples = ((VM_MethodListener)listener).getSamples();

    // (1) Update the global (cumulative) sample data
    VM_Controller.methodSamples.update(samples, numSamples);
    
    // (2) Remove duplicates from samples buffer.
    //     NOTE: This is a dirty trick and may be ill-advised.
    //     Rather than copying the unique samples into a different buffer
    //     we treat samples as if it was a scratch buffer.
    //     NOTE: This is worse case O(numSamples^2) but we expect a 
    //     significant number of duplicates, so it's probably better than
    //     the other obvious alternative (sorting samples).
    int uniqueIdx = 1;
  outer:
    for (int i=1; i<numSamples; i++) {
      int cur = samples[i];
      for (int j=0; j<uniqueIdx; j++) {
        if (cur == samples[j]) continue outer;
      }
      samples[uniqueIdx++] = cur;
    }

    // (3) For all samples in 0...uniqueIdx, if the method represented by
    //     the sample is compiled at an opt level below filterOptLevel
    //     then report it to the controller. 
    for (int i=0; i<uniqueIdx; i++) {
      int cmid = samples[i];
      double ns = VM_Controller.methodSamples.getData(cmid);
      VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
      if (cm != null) {         // not already obsoleted
        int compilerType = cm.getCompilerType();

        // Enqueue it unless it's either a trap method or already opt
        // compiled at filterOptLevel or higher.
        if (!(compilerType == VM_CompiledMethod.TRAP ||
              (compilerType == VM_CompiledMethod.OPT && 
               (((VM_OptCompiledMethod)cm).getOptLevel() >= filterOptLevel)))) {
          VM_HotMethodRecompilationEvent event = 
            new VM_HotMethodRecompilationEvent(cm, ns);
          VM_Controller.controllerInputQueue.insert(ns, event);
          if (VM.LogAOSEvents) VM_AOSLogging.controllerNotifiedForHotness(cm, ns);
        }
      }
    }
  }
}
