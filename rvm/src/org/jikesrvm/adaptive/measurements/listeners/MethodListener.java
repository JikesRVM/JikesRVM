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
package org.jikesrvm.adaptive.measurements.listeners;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.AosEntrypoints;
import org.jikesrvm.scheduler.Synchronization;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A MethodListener defines a listener to collect method invocation samples.
 *
 * Samples are collected in a buffer.
 * When sampleSize samples have been collected
 * the listener's organizer is activated to process them.
 *
 * Defines update's interface to be a compiled method identifier, CMID.
 */
@Uninterruptible
public final class MethodListener extends Listener {

  /**
   * Number of samples to be gathered before they are processed
   */
  int sampleSize;

  /**
   * Number of samples taken so far
   */
  int numSamples;

  /**
   * The sample buffer
   * Key Invariant: samples.length >= sampleSize
   */
  int[] samples;

  /**
   * @param sampleSize the initial sampleSize for the listener
   */
  public MethodListener(int sampleSize) {
    this.sampleSize = sampleSize;
    samples = new int[sampleSize];
  }

  /**
   * This method is called when a sample is taken.
   * It parameter "cmid" represents the compiled method ID of the method
   * which was executing at the time of the sample.  This method
   * bumps the counter and checks whether a threshold is reached.
   * <p>
   * NOTE: There can be multiple threads executing this method at the
   *       same time. We attempt to ensure that the resulting race conditions
   *       are safely handled, but make no guarentee that every sample is
   *       actually recorded.
   *
   * @param cmid the compiled method ID to update
   * @param callerCmid a compiled method id for the caller, -1 if none
   * @param whereFrom Was this a yieldpoint in a PROLOGUE, BACKEDGE, or
   *         EPILOGUE?
   */
  public void update(int cmid, int callerCmid, int whereFrom) {
    if (VM.UseEpilogueYieldPoints) {
      // Use epilogue yieldpoints.  We increment one sample
      // for every yieldpoint.  On a prologue, we count the caller.
      // On backedges and epilogues, we count the current method.
      if (whereFrom == RVMThread.PROLOGUE) {
        // Before getting a sample index, make sure we have something to insert
        if (callerCmid != -1) {
          recordSample(callerCmid);
        } // nothing to insert
      } else {
        // loop backedge or epilogue.
        recordSample(cmid);
      }
    } else {
      // Original scheme: No epilogue yieldpoints.  We increment two samples
      // for every yieldpoint.  On a prologue, we count both the caller
      // and callee.  On backedges, we count the current method twice.
      if (whereFrom == RVMThread.PROLOGUE) {
        // Increment both for this method and the caller
        recordSample(cmid);
        if (callerCmid != -1) {
          recordSample(callerCmid);
        }
      } else {
        // loop backedge.  We're only called once, so need to take
        // two samples to avoid penalizing methods with loops.
        recordSample(cmid);
        recordSample(cmid);
      }
    }
  }

  /**
   * This method records a sample containing the CMID (compiled method ID)
   * passed.  Since multiple threads may be taking samples concurrently,
   * we use fetchAndAdd to distribute indices into the buffer AND to record
   * when a sample is taken.  (Thread 1 may get an earlier index, but complete
   * the insertion after Thread 2.)
   *
   * @param CMID compiled method ID to record
   */
  private void recordSample(int CMID) {
    // reserve the next available slot
    int idx = Synchronization.fetchAndAdd(this, AosEntrypoints.methodListenerNumSamplesField.getOffset(), 1);
    // make sure it is valid
    if (idx < sampleSize) {
      samples[idx] = CMID;
    }
    if (idx + 1 == sampleSize) {
      // The last sample.
      activateOrganizer();
    }
  }

  @Override
  public void report() { }

  @Override
  public void reset() {
    numSamples = 0;
  }

  /**
   * @return the buffer of samples
   */
  public int[] getSamples() { return samples; }

  /**
   * @return how many samples in the array returned by getSamples are valid
   */
  public int getNumSamples() {
    return (numSamples < sampleSize) ? numSamples : sampleSize;
  }
}
