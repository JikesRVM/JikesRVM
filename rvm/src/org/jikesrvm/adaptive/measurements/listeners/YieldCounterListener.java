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
import org.vmmagic.pragma.Uninterruptible;

/**
 * A YieldCounterListener samples yield points, and
 * notifies an Organizer when a threshold is reached.
 * <p>
 * In effect, this class provides a way to "wake up" an infrequent
 * service periodically.
 */
@Uninterruptible
public final class YieldCounterListener extends NullListener {

  /**
   * Constructor
   *
   * @param yieldThreshold  the threshold of when to call organizer
   */
  public YieldCounterListener(int yieldThreshold) {
    this.yieldThreshold = yieldThreshold;
  }

  @Override
  public void update(int whereFrom) {
    if (VM.VerifyAssertions) VM._assert(AosEntrypoints.yieldCountListenerNumYieldsField!=null);
    int yp = Synchronization.fetchAndAdd(this, AosEntrypoints.yieldCountListenerNumYieldsField.getOffset(), 1) + 1;
    if (yp == yieldThreshold) {
      totalYields += yp;
      activateOrganizer();
    }
  }

  @Override
  public void report() {
    VM.sysWriteln("Yield points counted: ", totalYields);
  }

  /**
   * No-op.
   */
  @Override
  public void reset() { }

  private int yieldThreshold;
  @SuppressWarnings({"unused", "UnusedDeclaration", "CanBeFinal"})
// Accessed via EntryPoints
  private int numYields = 0;
  private int totalYields = 0;
}
