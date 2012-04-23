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
import org.mmtk.utility.options.Options;
import org.mmtk.utility.options.MarkSweepMarkBits;
import org.mmtk.utility.options.EagerCompleteSweep;
import org.mmtk.utility.Constants;
import org.mmtk.utility.HeaderByte;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Each instance of this class corresponds to one mark-sweep *space*.
 * Each of the instance methods of this class may be called by any
 * thread (i.e. synchronization must be explicit in any instance or
 * class method).  This contrasts with the MarkSweepLocal, where
 * instances correspond to *plan* instances and therefore to kernel
 * threads.  Thus unlike this class, synchronization is not necessary
 * in the instance methods of MarkSweepLocal.
 */
@Uninterruptible
public final class MarkSweepSpace extends SegregatedFreeListSpace implements Constants {

  /****************************************************************************
   *
   * Class variables
   */
  /**
   * Select between using mark bits in a side bitmap, or mark bits
   * in the headers of object (or other sub-class scheme), and a single
   * mark bit per block.
   */
  public static final boolean HEADER_MARK_BITS = VM.config.HEADER_MARK_BITS;
  /** highest bit bits we may use */
  private static final int AVAILABLE_LOCAL_BITS = 8 - HeaderByte.USED_GLOBAL_BITS;

  /* mark bits */
  private static final int COUNT_BASE = 0;

  public static final int DEFAULT_MARKCOUNT_BITS = 4;
  public static final int MAX_MARKCOUNT_BITS = AVAILABLE_LOCAL_BITS - COUNT_BASE;
  private static final byte MARK_COUNT_INCREMENT = (byte) (1<<COUNT_BASE);
  private static final byte MARK_COUNT_MASK = (byte) (((1<<MAX_MARKCOUNT_BITS)-1) << COUNT_BASE);

  private static final boolean EAGER_MARK_CLEAR = HeaderByte.NEEDS_UNLOGGED_BIT;

  /* header requirements */
  public static final int LOCAL_GC_BITS_REQUIRED = MAX_MARKCOUNT_BITS;
  public static final int GLOBAL_GC_BITS_REQUIRED = 0;
  public static final int GC_HEADER_WORDS_REQUIRED = 0;


  /****************************************************************************
   *
   * Instance variables
   */
  private byte markState = 1;
  private byte allocState = 0;
  private boolean inMSCollection;
  private static final boolean usingStickyMarkBits = VM.activePlan.constraints().needsLogBitInHeader(); /* are sticky mark bits in use? */
  private boolean isAgeSegregated = false; /* is this space a nursery space? */
  private boolean isAllocAsMarked = false;

  /****************************************************************************
   *
   * Initialization
   */

  static {
    Options.markSweepMarkBits = new MarkSweepMarkBits();
    Options.eagerCompleteSweep = new EagerCompleteSweep();
  }

  /**
   * The caller specifies the region of virtual memory to be used for
   * this space.  If this region conflicts with an existing space,
   * then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param vmRequest An object describing the virtual memory requested.
   */
  public MarkSweepSpace(String name, VMRequest vmRequest) {
    super(name, 0, vmRequest);
    if (usingStickyMarkBits) allocState |= HeaderByte.UNLOGGED_BIT;
  }

  /**
   * This instance will be age-segregated using the sticky mark bits
   * algorithm. Perform appropriate initialization
   */
  public void makeAgeSegregatedSpace() {
    /* we must be using sticky mark bits */
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(usingStickyMarkBits);
    allocState &= ~HeaderByte.UNLOGGED_BIT; /* clear the unlogged bit for nursery allocs */
    isAgeSegregated = true;
  }

  /**
   * Should SegregatedFreeListSpace manage a side bitmap to keep track of live objects?
   */
  @Override
  @Inline
  protected boolean maintainSideBitmap() {
    return !HEADER_MARK_BITS;
  }

  /**
   * Do we need to preserve free lists as we move blocks around.
   */
  @Override
  @Inline
  protected boolean preserveFreeList() {
    return !LAZY_SWEEP;
  }

  /****************************************************************************
   *
   * Allocation
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
    if (HEADER_MARK_BITS) {
      if (inMSCollection) markBlock(block);
    }

    if (LAZY_SWEEP) {
      return makeFreeList(block, sizeClass);
    } else {
      return getFreeList(block);
    }
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
    if (HEADER_MARK_BITS) {
      if (inMSCollection) markBlock(block);
    }
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Prepare for a new collection increment.  For the mark-sweep
   * collector we must flip the state of the mark bit between
   * collections.
   *
   * @param gcWholeMS True if we are going to collect the whole marksweep space
   */
  public void prepare(boolean gcWholeMS) {
    if (HEADER_MARK_BITS && Options.eagerCompleteSweep.getValue()) {
      consumeBlocks();
    } else {
      flushAvailableBlocks();
    }
    if (HEADER_MARK_BITS) {
      if (gcWholeMS) {
        allocState = markState;
        if (usingStickyMarkBits && !isAgeSegregated) /* if true, we allocate as "mature", not nursery */
          allocState |= HeaderByte.UNLOGGED_BIT;
        markState = deltaMarkState(true);
        if (EAGER_MARK_CLEAR)
          clearAllBlockMarks();
      }
    } else {
      zeroLiveBits();
    }
    inMSCollection = true;
  }

  /**
   * A new collection increment has completed.  For the mark-sweep
   * collector this means we can perform the sweep phase.
 */
  public void release() {
    sweepConsumedBlocks(!EAGER_MARK_CLEAR);
    inMSCollection = false;
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

  /**
   * Should the sweep reclaim the cell containing this object. Is this object
   * live. This is only used when maintainSideBitmap is false.
   *
   * @param object The object to query
   * @return True if the cell should be reclaimed
   */
  @Override
  @Inline
  protected boolean isCellLive(ObjectReference object) {
    if (!HEADER_MARK_BITS) {
      return super.isCellLive(object);
    }
    return testMarkState(object);
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
    if (HEADER_MARK_BITS) {
      if (testAndMark(object)) {
        markBlock(object);
        trace.processNode(object);
      }
    } else {
      if (testAndSetLiveBit(object)) {
        trace.processNode(object);
      }
    }
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
    if (HEADER_MARK_BITS) {
      return testMarkState(object);
    } else {
      return liveBitSet(object);
    }
  }

  /**
   * Get the previous mark state.
   *
   * @return The previous mark state.
   */
  @Inline
  public byte getPreviousMarkState() {
    return deltaMarkState(false);
  }

  /**
   * Return the mark state incremented or decremented by one.
   *
   * @param increment If true, then return the incremented value else return the decremented value
   * @return the mark state incremented or decremented by one.
   */
  private byte deltaMarkState(boolean increment) {
    byte mask = (byte) (((1 << Options.markSweepMarkBits.getValue()) - 1)<<COUNT_BASE);
    byte rtn = (byte) (increment ? markState + MARK_COUNT_INCREMENT : markState - MARK_COUNT_INCREMENT);
    rtn &= mask;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert((markState & ~MARK_COUNT_MASK) == 0);
    return rtn;
  }

  /****************************************************************************
   *
   * Header manipulation
   */

  /**
   * Perform any required post allocation initialization
   *
   * @param object the object ref to the storage to be initialized
   */
  @Inline
  public void postAlloc(ObjectReference object) {
    initializeHeader(object, true);
  }

  /**
   * Perform any required post copy (i.e. in-GC allocation) initialization.
   * This is relevant (for example) when MS is used as the mature space in
   * a copying GC.
   *
   * @param object the object ref to the storage to be initialized
   * @param majorGC Is this copy happening during a major gc?
   */
  @Inline
  public void postCopy(ObjectReference object, boolean majorGC) {
    initializeHeader(object, false);
    if (!HEADER_MARK_BITS) {
      testAndSetLiveBit(object);
    }
  }

  /**
   * Perform any required initialization of the GC portion of the header.
   *
   * @param object the object ref to the storage to be initialized
   * @param alloc is this initialization occuring due to (initial) allocation
   * (true) or due to copying (false)?
   */
  @Inline
  public void initializeHeader(ObjectReference object, boolean alloc) {
    if (HEADER_MARK_BITS) {
      byte oldValue = VM.objectModel.readAvailableByte(object);
      byte newValue = (byte) ((oldValue & ~MARK_COUNT_MASK) | (alloc && !isAllocAsMarked ? allocState : markState));
      VM.objectModel.writeAvailableByte(object, newValue);
    } else if (HeaderByte.NEEDS_UNLOGGED_BIT)
      HeaderByte.markAsUnlogged(object);
  }

  /**
   * Atomically attempt to set the mark bit of an object.  Return true
   * if successful, false if the mark bit was already set.
   *
   * @param object The object whose mark bit is to be set
   */
  @Inline
  private boolean testAndMark(ObjectReference object) {
    byte oldValue, markBits, newValue;
    oldValue = VM.objectModel.readAvailableByte(object);
    markBits = (byte) (oldValue & MARK_COUNT_MASK);
    if (markBits == markState) return false;
    newValue = (byte)((oldValue & ~MARK_COUNT_MASK) | markState);
    if (HeaderByte.NEEDS_UNLOGGED_BIT) newValue |= HeaderByte.UNLOGGED_BIT;
    VM.objectModel.writeAvailableByte(object, newValue);
    return true;
  }

  /**
   * Return true if the mark count for an object has the given value.
   *
   * @param object The object whose mark bit is to be tested
   * @return True if the mark bit for the object is set.
   */
  @Inline
  private boolean testMarkState(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert((markState & ~MARK_COUNT_MASK) == 0);
    return (VM.objectModel.readAvailableByte(object) & MARK_COUNT_MASK) == markState;
  }

  public void makeAllocAsMarked() {
    isAllocAsMarked = true;
  }
}
