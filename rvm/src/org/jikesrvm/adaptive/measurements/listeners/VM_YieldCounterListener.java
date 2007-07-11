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
package org.jikesrvm.adaptive.measurements.listeners;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.VM_AosEntrypoints;
import org.jikesrvm.scheduler.VM_Synchronization;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A VM_YieldCounterListener samples yield points, and
 * notifies an Organizer when a threshold is reached.
 *
 * In effect, this class provides a way to "wake up" an infrequent
 * service periodically.
 */
@Uninterruptible
public final class VM_YieldCounterListener extends VM_NullListener {

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
    int yp = VM_Synchronization.fetchAndAdd(this, VM_AosEntrypoints.yieldCountListenerNumYieldsField.getOffset(), 1) + 1;
    if (yp == yieldThreshold) {
      totalYields += yp;
      activateOrganizer();
    }
  }

  public void report() {
    VM.sysWriteln("Yield points counted: ", totalYields);
  }

  public void reset() { }

  private int yieldThreshold;
  @SuppressWarnings({"unused", "UnusedDeclaration", "CanBeFinal"})
// Accessed via VM_EntryPoints
  private int numYields = 0;
  private int totalYields = 0;
}
