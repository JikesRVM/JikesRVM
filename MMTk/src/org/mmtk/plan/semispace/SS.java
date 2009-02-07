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
package org.mmtk.plan.semispace;

import org.mmtk.policy.CopySpace;
import org.mmtk.policy.Space;
import org.mmtk.plan.*;
import org.mmtk.utility.heap.VMRequest;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements a simple semi-space collector. See the Jones
 * & Lins GC book, section 2.2 for an overview of the basic
 * algorithm. This implementation also includes a large object space
 * (LOS), and an uncollected "immortal" space.<p>
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  Instances of Plan map 1:1 to "kernel
 * threads" (aka CPUs).  Thus instance
 * methods allow fast, unsychronized access to Plan utilities such as
 * allocation and collection.  Each instance rests on static resources
 * (such as memory and virtual memory resources) which are "global"
 * and therefore "static" members of Plan.  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance proprties of this plan.
 */
@Uninterruptible
public class SS extends StopTheWorld {
  /** Fraction of available virtual memory available to each semispace */
  private static final float SEMISPACE_VIRT_MEM_FRAC = 0.30f;

  /****************************************************************************
   *
   * Class variables
   */

  /** True if allocating into the "higher" semispace */
  public static boolean hi = false; // True if allocing to "higher" semispace

  /** One of the two semi spaces that alternate roles at each collection */
  public static final CopySpace copySpace0 = new CopySpace("ss0", DEFAULT_POLL_FREQUENCY, false, VMRequest.create());
  public static final int SS0 = copySpace0.getDescriptor();

  /** One of the two semi spaces that alternate roles at each collection */
  public static final CopySpace copySpace1 = new CopySpace("ss1", DEFAULT_POLL_FREQUENCY, true, VMRequest.create());
  public static final int SS1 = copySpace1.getDescriptor();

  public final Trace ssTrace;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class variables
   */
  public static final int ALLOC_SS = Plan.ALLOC_DEFAULT;

  public static final int SCAN_SS = 0;

  /**
   * Constructor
   */
  public SS() {
    ssTrace = new Trace(metaDataSpace);
  }

  /**
   * @return The to space for the current collection.
   */
  @Inline
  public static CopySpace toSpace() {
    return hi ? copySpace1 : copySpace0;
  }

  /**
   * @return The from space for the current collection.
   */
  @Inline
  public static CopySpace fromSpace() {
    return hi ? copySpace0 : copySpace1;
  }


  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase
   */
  @Inline
  public void collectionPhase(short phaseId) {
    if (phaseId == SS.PREPARE) {
      hi = !hi; // flip the semi-spaces
      // prepare each of the collected regions
      copySpace0.prepare(hi);
      copySpace1.prepare(!hi);
      ssTrace.prepare();
      super.collectionPhase(phaseId);
      return;
    }
    if (phaseId == CLOSURE) {
      ssTrace.prepare();
      return;
    }
    if (phaseId == SS.RELEASE) {
      // release the collected region
      fromSpace().release();

      super.collectionPhase(phaseId);
      return;
    }

    super.collectionPhase(phaseId);
  }

  /****************************************************************************
   *
   * Accounting
   */

  /**
   * Return the number of pages reserved for copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for copying.
   */
  public final int getCollectionReserve() {
    // we must account for the number of pages required for copying,
    // which equals the number of semi-space pages reserved
    return toSpace().reservedPages() + super.getCollectionReserve();
  }

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  This is <i>exclusive of</i> space reserved for
   * copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  public int getPagesUsed() {
    return super.getPagesUsed() + toSpace().reservedPages();
  }

  /**
   * Return the number of pages available for allocation, <i>assuming
   * all future allocation is to the semi-space</i>.
   *
   * @return The number of pages available for allocation, <i>assuming
   * all future allocation is to the semi-space</i>.
   */
  public final int getPagesAvail() {
    return(super.getPagesAvail()) >> 1;
  }

  /**
   * Calculate the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   *
   * @return the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   */
  public int getPagesRequired() {
    return super.getPagesRequired() + (toSpace().requiredPages() << 1);
  }

  /**
   * @see org.mmtk.plan.Plan#willNeverMove
   *
   * @param object Object in question
   * @return True if the object will never move
   */
  @Override
  public boolean willNeverMove(ObjectReference object) {
    if (Space.isInSpace(SS0, object) || Space.isInSpace(SS1, object))
      return false;
    return super.willNeverMove(object);
  }

  /**
   * Register specialized methods.
   */
  @Interruptible
  protected void registerSpecializedMethods() {
    TransitiveClosure.registerSpecializedScan(SCAN_SS, SSTraceLocal.class);
    super.registerSpecializedMethods();
  }
}
