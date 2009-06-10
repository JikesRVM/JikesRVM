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
package org.mmtk.policy;

import org.mmtk.utility.alloc.SegregatedFreeListLocal;
import org.mmtk.utility.Constants;

import org.vmmagic.pragma.*;

/**
 * This class implements unsynchronized (local) elements of a
 * mark-sweep collector.  Allocation is via the segregated free list
 * (@see SegregatedFreeList).  Marking is done using both a bit in
 * each header's object word, and a mark bitmap.  Sweeping is
 * performed lazily.<p>
 *
 * A free list block is a contiguous region of memory containing cells
 * of a single size class, and is a construct of the
 * SegregatedFreeList.  This class extends the block to include a mark
 * bitmap.  During the mark phase, if an object is encountered with
 * the mark bit in its header unset, it is set and the mark bit in the
 * block header corresponding to that object is set.  The rationale
 * behind this approach is that testing (and setting) the mark bit in
 * the object header is cheap, while using a bitmap makes sweeping
 * more efficient.  This approach maximizes the speed of the common
 * case when marking, while also allowing for fast sweeping, with
 * minimal space overhead (2 bits per object).
 *
 * @see org.mmtk.utility.alloc.SegregatedFreeList
 * @see MarkSweepSpace
 */
@Uninterruptible
public final class MarkSweepLocal extends SegregatedFreeListLocal<MarkSweepSpace> implements Constants {

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param space The mark-sweep space to which this allocator
   * instances is bound.
   */
  public MarkSweepLocal(MarkSweepSpace space) {
    super(space);
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Prepare for a collection. If paranoid, perform a sanity check.
   */
  public void prepare() {
    flush();
  }

  /**
   * Finish up after a collection.
   */
  public void release() {}
}
