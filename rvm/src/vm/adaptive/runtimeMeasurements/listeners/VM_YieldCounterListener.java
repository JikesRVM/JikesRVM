/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.*;

import org.vmmagic.pragma.*;

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
class VM_YieldCounterListener extends VM_NullListener implements Uninterruptible {

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
    int yp = VM_Synchronization.fetchAndAdd(this, VM_Entrypoints.yieldCountListenerNumYieldsField.getOffset(), 1) + 1;
    if (yp == yieldThreshold) {
      totalYields += yp;
      numYields = 0;
      activateOrganizer();
    }
  }

  public void report() {
     VM.sysWriteln("Yield points counted: ", totalYields);
  }

  public final void reset() { }

  private int yieldThreshold;
  private int numYields = 0;
  private int totalYields = 0;
}
