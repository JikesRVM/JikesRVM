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
package org.mmtk.plan.nogc;

import org.mmtk.plan.Plan;
import org.mmtk.plan.Trace;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.Conversions;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;


/**
 * This class implements the global state of a a simple allocator
 * without a collector.
 */
@Uninterruptible public class NoGC extends Plan {

  /*****************************************************************************
   *
   * Class fields
   */
  public static final ImmortalSpace defSpace
    = new ImmortalSpace("default", DEFAULT_POLL_FREQUENCY, (float) 0.6);
  public static final int DEF = defSpace.getDescriptor();

  /*****************************************************************************
   *
   * Instance fields
   */
  public final Trace trace;

  /**
   * Constructor
   */
  public NoGC() {
    trace = new Trace(metaDataSpace);
  }

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase
   */
  public final void collectionPhase(int phaseId) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    // if (phaseId == PREPARE) {
    // }
    // if (phaseID == RELEASE) {
    // }
    // super.collectionPhase(phaseId);
  }

  /**
   * Poll for a collection
   *
   * @param vmExhausted Virtual Memory range for space is exhausted.
   * @param space The space that caused the poll.
   * @return True if a collection is required.
   */
  public final boolean poll(boolean vmExhausted, Space space) {
    boolean spaceFull = space.reservedPages() >= Conversions.bytesToPages(space.getExtent());
    if (spaceFull || getPagesReserved() > getTotalPages()) {
      VM.assertions.fail("GC Triggered in NoGC Plan due to memory exhaustion.");
    }
    return false;
  }

  /*****************************************************************************
   *
   * Accounting
   */

  /**
   * Return the number of pages used given the pending
   * allocation.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  public int getPagesUsed() {
    return (defSpace.reservedPages() + super.getPagesUsed());
  }

}
