/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Uninterruptible;
import java.util.*;

/**
 * A VM_YieldCounterListener samples yield points, and
 * notifies an Organizer when a threshold is reached.
 *
 * In effect, this class provides a way to "wake up" an infrequent
 * service periodically.
 *
 * @author Stephen Fink
 * @modified Peter Sweeney
 */
class VM_YieldCounterListener extends VM_NullListener implements VM_Uninterruptible {

  /**
   * Constructor
   *
   * @param yieldThreshold  the threshold of when to call organizer
   */
  public VM_YieldCounterListener(int yieldThreshold) {
    this.yieldThreshold = yieldThreshold;
  }

  /** 
   * This method is called when its time to record that a 
   * yield point has occurred.
   * @param whereFrom Was this a yieldpoint in a PROLOGUE, BACKEDGE, or
   *             EPILOGUE?
   */
  public void update(int whereFrom) {
     nYields++;
     if (nYields >= yieldThreshold) {
	synchronized(this) {
	  // now that we're in a critical section, double-check that
	  // another thread has not yet processed this threshold reached event.
     	  if (nYields >= yieldThreshold) {
	    passivate();
            notifyOrganizer();
            totalYields += nYields;
            nYields = 0;
	  }
        }
     }
  }

  public void report() {
     VM.sysWriteln("Yield points counted: ", totalYields);
  }

  private int yieldThreshold;
  private int nYields = 0;
  private int totalYields = 0;
}
