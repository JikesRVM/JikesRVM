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
   *       thresholdReached is called exactly once when samplesTaken reaches 
   *       sampleSize, but there are still no guarentees.
   *
   * @param cmid the compiled method ID to update
   * @param callerCmid a compiled method id for the caller, -1 if none
   * @param whereFrom Was this a yieldpoint in a PROLOGUE, BACKEDGE, or
   *         EPILOGUE?
   */
  public final void update(int cmid, int callerCmid, int whereFrom) {

	if (callerCmid != -1) {
      if (VM_ClassLoader.getCompiledMethod(callerCmid) == null) {
	VM.sysWrite("MethodListener.update: callerCmid (");
	VM.sysWrite(callerCmid, false);
	VM.sysWrite(") is null, exiting\n");
	VM.sysWrite("samplesTaken: ");
	VM.sysWrite(samplesTaken, false);
	VM.sysWrite("\n");
	throw new RuntimeException();
      }
	}

      if (VM_ClassLoader.getCompiledMethod(cmid) == null) {
	VM.sysWrite("MethodListener.update: cmid (");
	VM.sysWrite(cmid, false);
	VM.sysWrite(") is null, exiting\n");
	VM.sysWrite("samplesTaken: ");
	VM.sysWrite(samplesTaken, false);
	VM.sysWrite("\n");
	throw new RuntimeException();
      }


    int idx;
    if (!VM.UseEpilogueYieldPoints) {
      // Original scheme: No epilogue yieldpoints.  We increment two samples
      // for every yieldpoint.  On a prologue, we count both the caller
      // and callee.  On backedges, we count the current method twice.
      if (whereFrom == VM_Thread.PROLOGUE) {
        // Increment both for this method and the caller
        idx = ++samplesTaken; // ++ is important. --dave
        if (idx <= sampleSize) {
	  samples[idx-1] = cmid;
        }
        idx = ++samplesTaken; // ++ is important. --dave
        if (idx <= sampleSize && callerCmid != -1) {
	  samples[idx-1] = callerCmid;
        }
      } else { 
        // loop backedge.  We're only called once, so need to take
        // two samples to avoid penalizing methods with loops.
        idx = ++samplesTaken; // ++ is important. --dave
        if (idx <= sampleSize) {
	  samples[idx-1] = cmid;
        }
        idx = ++samplesTaken; // ++ is important. --dave
        if (idx <= sampleSize) {
	  samples[idx-1] = cmid;
        }
      }
    } else {
      // New scheme: Use epilogue yieldpoints.  We increment one sample
      // for every yieldpoint.  On a prologue, we count the caller.
      // On backedges and epilogues, we count the current method.
      if (whereFrom == VM_Thread.PROLOGUE) {
        idx = ++samplesTaken; // ++ is important. --dave
        if (idx <= sampleSize && callerCmid != -1) {
	  samples[idx-1] = callerCmid;
        }
      } else { 
        // loop backedge or epilogue.  
        idx = ++samplesTaken; // ++ is important. --dave
        if (idx <= sampleSize) {
	  samples[idx-1] = cmid;
        }
      }
    }

    if (idx >= sampleSize) { 
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
      if (VM_ClassLoader.getCompiledMethod(id) == null) {
	VM.sysWrite("MethodListener.thresholdReached: Found a cmid (");
	VM.sysWrite(id, false);
	VM.sysWrite(") with a null compiled method, exiting\n");
	VM.sysWrite("sample index: ");
	VM.sysWrite(i, false);
	VM.sysWrite("\n");
	throw new RuntimeException();
      }
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
    samplesTaken = 0;
  }


  /**
   * updates the sample size for this listener
   * @param newSampleSize the new sample size value to use, 
   */
  public final void setSampleSize(int newSampleSize) {
    sampleSize = newSampleSize; 
    if (sampleSize > samples.length) {
      samples = new int[newSampleSize];
      samplesTaken = 0;
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
  public final int getSamplesTaken() { return samplesTaken; }
 

  /**
   * @return how many samples in the array returned by getSamples
   *         are valid (min(getSamplesTaken(), getSampleSize())).
   */
  public final int getNumSamples() {
    return Math.min(getSamplesTaken(), getSampleSize());
  }


  // Number of samples to be processed before calling thresholdReached.
  protected int sampleSize;  

  // Number of samples taken so far
  protected int samplesTaken;

  // The sample buffer
  // Key Invariant: samples.length >= sampleSize
  protected int[] samples;

  // Is this listener supposed to notify its organizer when thresholdReached?
  protected boolean notifyOrganizer;
} 
