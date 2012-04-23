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

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.Constants;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Each instance of this class corresponds to one *space*.
 * Each of the instance methods of this class may be called by any
 * thread (i.e. synchronization must be explicit in any instance or
 * class method).  This contrasts with the MarkSweepLocal, where
 * instances correspond to *plan* instances and therefore to kernel
 * threads.  Thus unlike this class, synchronization is not necessary
 * in the instance methods of MarkSweepLocal.
 */
@Uninterruptible
public final class ExplicitFreeListSpace extends SegregatedFreeListSpace implements Constants {

  /****************************************************************************
   *
   * Class variables
   */
  public static final int LOCAL_GC_BITS_REQUIRED = 0;
  public static final int GLOBAL_GC_BITS_REQUIRED = 0;
  public static final int GC_HEADER_WORDS_REQUIRED = 0;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * The caller specifies the region of virtual memory to be used for
   * this space.  If this region conflicts with an existing space,
   * then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param vmRequest An object describing the virtual memory requested.
   */
  public ExplicitFreeListSpace(String name, VMRequest vmRequest) {
    super(name, 0, vmRequest);
  }

  /**
   * Should SegregatedFreeListSpace manage a side bitmap to keep track of live objects?
   */
  @Override
  @Inline
  protected boolean maintainSideBitmap() {
    return true;
  }

  /**
   * Do we need to preserve free lists as we move blocks around.
   */
  @Override
  @Inline
  protected boolean preserveFreeList() {
    return false;
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Prepare the next block in the free block list for use by the free
   * list allocator.  In the case of lazy sweeping this involves
   * sweeping the available cells.  <b>The sweeping operation must
   * ensure that cells are pre-zeroed</b>, as this method must return
   * pre-zeroed cells.
   *
   * @param block The block to be prepared for use
   * @param sizeClass The size class of the block
   * @return The address of the first pre-zeroed cell in the free list
   * for this block, or zero if there are no available cells.
   */
  @Override
  protected Address advanceToBlock(Address block, int sizeClass) {
    return makeFreeList(block, sizeClass);
  }

  /**
   * Notify that a new block has been installed. This is to ensure that
   * appropriate collection state can be initialized for the block
   *
   * @param block The new block
   * @param sizeClass The block's sizeclass.
   */
  @Override
  protected void notifyNewBlock(Address block, int sizeClass) {
    clearLiveBits(block, sizeClass);
  }

  /**
   * Free an object.
   *
   * @param object The object to be freed.
   */
  @Inline
  public void free(ObjectReference object) {
    clearLiveBit(object);
  }

  /**
   * Prepare for a new collection increment.
   */
  public void prepare() {
    flushAvailableBlocks();
  }

  /**
   * A new collection increment has completed.
   */
  public void release() {
    sweepConsumedBlocks(true);
  }

  /**
   * Release an allocated page or pages
   *
   * @param start The address of the start of the page or pages
   */
  @Override
  @Inline
  public void release(Address start) {
    ((FreeListPageResource) pr).releasePages(start);
  }

  /****************************************************************************
   *
   * Object processing and tracing
   */

  /**
   * Trace a reference to an object under a mark sweep collection
   * policy.  If the object header is not already marked, mark the
   * object in either the bitmap or by moving it off the treadmill,
   * and enqueue the object for subsequent processing. The object is
   * marked as (an atomic) side-effect of checking whether already
   * marked.
   *
   * @param object The object to be traced.
   * @return The object (there is no object forwarding in this
   * collector, so we always return the same object: this could be a
   * void method but for compliance to a more general interface).
   */
  @Override
  @Inline
  public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object) {
    return object;
  }

  /**
   *
   * @param object The object in question
   * @return True if this object is known to be live (i.e. it is marked)
   */
  @Override
  @Inline
  public boolean isLive(ObjectReference object) {
    return liveBitSet(object);
  }
}
