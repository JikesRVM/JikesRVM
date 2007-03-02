/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.adaptive;

import com.ibm.jikesrvm.*;

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
@Uninterruptible
final class VM_YieldCounterListener extends VM_NullListener {

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
      activateOrganizer();
    }
  }

  public void report() {
     VM.sysWriteln("Yield points counted: ", totalYields);
  }

  public final void reset() { }

  private int yieldThreshold;
  @SuppressWarnings({"unused", "UnusedDeclaration","CanBeFinal"})// Accessed via VM_EntryPoints
  private int numYields = 0;
  private int totalYields = 0;
}
