/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * A VM_MethodListener defines a listener to collect method invocation samples.
 *
 * Samples are collected in a buffer.  
 * When sampleSize samples have been collected, thresholdReached is called.
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

  /*
   * Number of samples to be processed before calling thresholdReached
   */
  protected int sampleSize;  
  
  /*
   * Next available index in the sample array
   */
  protected int nextIndex;
  
  /*
   * Offset of nextIndex field, needed for fetchAndAdd synchronization
   */
  static final int nextIndexOffset = 
    VM.getMember("LVM_MethodListener;", "nextIndex", "I").getOffset();
  
  /*
   * Number of samples taken so far
   */
  protected int numSamples;
  
  /*
   * Offset of numSamples field, needed for fetchAndAdd synchronization
   */
  static final int numSamplesOffset = 
    VM.getMember("LVM_MethodListener;", "numSamples", "I").getOffset();
  
  /*
   * The sample buffer
   * Key Invariant: samples.length >= sampleSize
   */
  protected int[] samples;
  
  /*
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
   * This method is called when a time based sample occurs.
   * It parameter "cmid" represents the compiled method ID of the method
   * which was executing at the time of the sample.  This method
   * bumps the counter and checks whether a threshold is reached.
   * <p>
   * NOTE: There can be multiple threads executing this method at the 
   *       same time. We attempt to ensure that the resulting race conditions
   *       are safely handled, but make no guarentee that every sample is
   *       actually recorded. We do try to make it somewhat likely that 
   *       thresholdReached is called exactly once when numSamples reaches 
   *       sampleSize, but there are still no guarentees.
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
	  int sampleNumber = recordSample(callerCmid);
	  checkSampleSize(sampleNumber);
        } // nothing to insert
      } else { 
        // loop backedge or epilogue.  
	int sampleNumber = recordSample(cmid);
	checkSampleSize(sampleNumber);
      }
    } else {
      // Original scheme: No epilogue yieldpoints.  We increment two samples
      // for every yieldpoint.  On a prologue, we count both the caller
      // and callee.  On backedges, we count the current method twice.
      if (whereFrom == VM_Thread.PROLOGUE) {
        // Increment both for this method and the caller
	int sampleNumber = recordSample(cmid);
	if (callerCmid != -1) {
	  sampleNumber = recordSample(callerCmid);
	}
	checkSampleSize(sampleNumber);
      } else { 
        // loop backedge.  We're only called once, so need to take
        // two samples to avoid penalizing methods with loops.
	int sampleNumber = recordSample(cmid);
	sampleNumber = recordSample(cmid);
	checkSampleSize(sampleNumber);
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
   *
   * NOTE: if we are unlucky that we didn't get to insert the sample (because
   *  there was a slot when we started, but another thread stole it from us)
   *  we simply don't insert the sample.
   */
  private int recordSample(int CMID) {  
    // reserved the next available slot
    int idx = VM_Synchronization.fetchAndAdd(this, nextIndexOffset, 1);
    // make sure it is valid
    if (idx < sampleSize) {
      samples[idx] = CMID;

      // sampleNumber has the value before we incremented, add one to 
      // determine which sample we were
      int sampleNumber =  VM_Synchronization.fetchAndAdd(this, 
						     numSamplesOffset, 1) + 1;
      return sampleNumber;
    } else {
      return -1;
    }
  }

  /**
   * This method checks to see if the parameter passed was the last sample
   * If so, it passivates this listener and reports that the threshold has
   * been reached.
   *
   * @param sampleNumber the sample number was just taken 
   *      valid samples will be in [1..sampleSize]
   *      invalid sample will be -1 
   */
  private void checkSampleSize(int sampleNumber) {
    if (sampleNumber == sampleSize) { 
      passivate();
      thresholdReached();
    }
  }

  /**
   * When the threshold is reached either notify our organizer or
   * handle it ourselves by processing the samples, resetting, and 
   * activating ourselves again.
   */
  public void thresholdReached() {
    int numSamples = getNumSamples();
    for (int i=0; i<numSamples; i++) {
      int id = samples[i];
    }

    if (notifyOrganizer) {
      notifyOrganizer();
    } else {
      processSamples();
      reset();
      activate();
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
    nextIndex = 0;
    numSamples = 0;
  }


  /**
   * updates the sample size for this listener
   * doesn't worry about any samples in the current buffer
   * @param newSampleSize the new sample size value to use, 
   */
  public final void setSampleSize(int newSampleSize) {
    sampleSize = newSampleSize; 
    if (sampleSize > samples.length) {
      samples = new int[newSampleSize];
      nextIndex = 0;
      numSamples = 0;
    }
  }

  /**
   * @return the current sample size/threshold value
   */
  public final int getSampleSize() { return numSamples; }

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
    return Math.min(getSamplesTaken(), getSampleSize());
  }
} 
