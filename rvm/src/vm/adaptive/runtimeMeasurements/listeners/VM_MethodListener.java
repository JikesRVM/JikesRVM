/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_Synchronization;

/**
 * A VM_MethodListener defines a listener to collect method invocation samples.
 *
 * Samples are collected in a buffer.  
 * When sampleSize samples have been collected they are either processed by 
 * the listener directly or the listener's organizer is activated to process them.
 *  
 * Defines update's interface to be a compiled method identifier, CMID.
 * 
 * @author Matthew Arnold
 * @author Stephen Fink
 * @author Dave Grove
 * @author Michael Hind
 * @author Peter Sweeney
 */
abstract class VM_MethodListener extends VM_Listener 
  implements VM_Uninterruptible {

  /**
   * Number of samples to be gathered before they are processed 
   */
  protected int sampleSize;  
  
  /**
   * Number of samples taken so far
   */
  protected int numSamples;
  
  /**
   * The sample buffer
   * Key Invariant: samples.length >= sampleSize
   */
  protected int[] samples;
  
  /**
   * Is this listener supposed to notify its organizer when thresholdReached?
   */
  protected boolean notifyOrganizer;
  

  /**
   * @param sampleSize the initial sampleSize for the listener
   * @param notifyOrganizer should the listener notify an organizer
   *                    when its threshold is reached?
   */
  public VM_MethodListener(int sampleSize, boolean notifyOrganizer) {
    this.sampleSize = sampleSize;
    this.notifyOrganizer = notifyOrganizer;
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
  public final void update(int cmid, int callerCmid, int whereFrom) {
    if (VM.UseEpilogueYieldPoints) {
      // Use epilogue yieldpoints.  We increment one sample
      // for every yieldpoint.  On a prologue, we count the caller.
      // On backedges and epilogues, we count the current method.
      if (whereFrom == VM_Thread.PROLOGUE) {
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
      if (whereFrom == VM_Thread.PROLOGUE) {
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
   * @return the sample number that was inserted or -1, if we were unlucky
   */
  private void recordSample(int CMID) {  
    // reserve the next available slot
    int idx = VM_Synchronization.fetchAndAdd(this, VM_Entrypoints.methodListenerNumSamplesField.getOffset(), 1);
    // make sure it is valid
    if (idx < sampleSize) {
      samples[idx] = CMID;
    }
    if (idx+1 == sampleSize) {
      // The last sample. 
      if (notifyOrganizer) {
	activateOrganizer(); // organizer will process
      } else { // must process them ourself
	passivate();
	processSamples();
	reset();
	activate();
      }
    }
  }

  /**
   * process the buffer of samples
   */
  public abstract void processSamples(); 


  /**
   * Reset the buffer to prepare to take more samples.
   */
  public void reset() {
    numSamples = 0;
  }

  /**
   * updates the sample size for this listener
   * doesn't worry about any samples in the current buffer
   * @param newSampleSize the new sample size value to use, 
   */
  public final void setSampleSize(int newSampleSize) throws VM_PragmaInterruptible {
    sampleSize = newSampleSize; 
    if (sampleSize > samples.length) {
      samples = new int[newSampleSize];
      numSamples = 0;
    }
  }

  /**
   * @return the current sample size/threshold value
   */
  public final int getSampleSize() { return sampleSize; }

  /**
   * @return the buffer of samples
   */
  public final int[] getSamples() { return samples; }

  /**
   * @return how many samples have been taken
   */
  public final int getSamplesTaken() { return numSamples; }

  /**
   * @return how many samples in the array returned by getSamples
   *         are valid (min(getSamplesTaken(), getSampleSize())).
   */
  public final int getNumSamples() {
    int x = getSamplesTaken();
    int y = getSampleSize();
    return (x < y) ? x : y;
  }
} 
