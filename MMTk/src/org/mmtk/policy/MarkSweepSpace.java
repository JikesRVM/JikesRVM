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
package org.mmtk.policy;

import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.options.MarkSweepMarkBits;
import org.mmtk.utility.options.EagerCompleteSweep;
import org.mmtk.utility.Constants;

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
@Uninterruptible public final class MarkSweepSpace extends Space
  implements Constants {

  /****************************************************************************
   * 
   * Class variables
   */
  /** highest bit bits we may use */
  private static final int MAX_BITS = 4;
 
  /* mark bits */
  private static final int COUNT_BASE = 0;
  public static final int DEFAULT_MARKCOUNT_BITS = 2;
  public static final int MAX_MARKCOUNT_BITS = MAX_BITS;
  private static final Word MARK_COUNT_INCREMENT = Word.one().lsh(COUNT_BASE);
  private static final Word MARK_COUNT_MASK = Word.one().lsh(MAX_MARKCOUNT_BITS).minus(Word.one()).lsh(COUNT_BASE);
  private static final Word MARK_BITS_MASK = Word.one().lsh(MAX_BITS).minus(Word.one());
  
  /* header requirements */
  public static final int LOCAL_GC_BITS_REQUIRED = MAX_BITS;
  public static final int GLOBAL_GC_BITS_REQUIRED = 0;
  public static final int GC_HEADER_WORDS_REQUIRED = 0;

  
  /****************************************************************************
   * 
   * Instance variables
   */
  private Word markState = Word.one();
  private Word allocState = Word.zero();
  private boolean inMSCollection;

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
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param start The start address of the space in virtual memory
   * @param bytes The size of the space in virtual memory, in bytes
   */
  public MarkSweepSpace(String name, int pageBudget, Address start, 
                        Extent bytes) {
    super(name, false, false, start, bytes);
    pr = new FreeListPageResource(pageBudget, this, start, extent, MarkSweepLocal.META_DATA_PAGES_PER_REGION);
  }

  /**
   * Construct a space of a given number of megabytes in size.<p>
   * 
   * The caller specifies the amount virtual memory to be used for
   * this space <i>in megabytes</i>.  If there is insufficient address
   * space, then the constructor will fail.
   * 
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param mb The size of the space in virtual memory, in megabytes (MB)
   */
  public MarkSweepSpace(String name, int pageBudget, int mb) {
    super(name, false, false, mb);
    pr = new FreeListPageResource(pageBudget, this, start, extent, MarkSweepLocal.META_DATA_PAGES_PER_REGION);
  }

  /**
   * Construct a space that consumes a given fraction of the available
   * virtual memory.<p>
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>as a fraction of the total available</i>.  If there
   * is insufficient address space, then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param frac The size of the space in virtual memory, as a
   * fraction of all available virtual memory
   */
  public MarkSweepSpace(String name, int pageBudget, float frac) {
    super(name, false, false, frac);
    pr = new FreeListPageResource(pageBudget, this, start, extent, MarkSweepLocal.META_DATA_PAGES_PER_REGION);
  }

  /**
   * Construct a space that consumes a given number of megabytes of
   * virtual memory, at either the top or bottom of the available
   * virtual memory.
   * 
   * The caller specifies the amount virtual memory to be used for
   * this space <i>in megabytes</i>, and whether it should be at the
   * top or bottom of the available virtual memory.  If the request
   * clashes with existing virtual memory allocations, then the
   * constructor will fail.
   * 
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param mb The size of the space in virtual memory, in megabytes (MB)
   * @param top Should this space be at the top (or bottom) of the
   * available virtual memory.
   */
  public MarkSweepSpace(String name, int pageBudget, int mb, boolean top) {
    super(name, false, false, mb, top);
    pr = new FreeListPageResource(pageBudget, this, start, extent, MarkSweepLocal.META_DATA_PAGES_PER_REGION);
  }

  /**
   * Construct a space that consumes a given fraction of the available
   * virtual memory, at either the top or bottom of the available
   *          virtual memory.
   *
   * The caller specifies the amount virtual memory to be used for
   * this space <i>as a fraction of the total available</i>, and
   * whether it should be at the top or bottom of the available
   * virtual memory.  If the request clashes with existing virtual
   * memory allocations, then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param frac The size of the space in virtual memory, as a
   * fraction of all available virtual memory
   * @param top Should this space be at the top (or bottom) of the
   * available virtual memory.
   */
  public MarkSweepSpace(String name, int pageBudget, float frac, boolean top) {
    super(name, false, false, frac, top);
    pr = new FreeListPageResource(pageBudget, this, start, extent, MarkSweepLocal.META_DATA_PAGES_PER_REGION);
  }

  /****************************************************************************
   * 
   * Collection
   */

  /**
   * Prepare for a new collection increment.  For the mark-sweep
   * collector we must flip the state of the mark bit between
   * collections.
   */
  public void prepare() {
    if (MarkSweepLocal.HEADER_MARK_BITS) {
      allocState = markState;
      markState = deltaMarkState(true);
    } else {
      MarkSweepLocal.zeroLiveBits(start, ((FreeListPageResource) pr).getHighWater());
    }
    inMSCollection = true;
  }

  /**
   * A new collection increment has completed.  For the mark-sweep
   * collector this means we can perform the sweep phase.
 */
  public void release() {
    inMSCollection = false;
  }

  /**
   * Return true if this mark-sweep space is currently being collected.
   * 
   * @return True if this mark-sweep space is currently being collected.
   */
  @Inline
  public boolean inMSCollection() { 
    return inMSCollection;
  }

  /**
   * Release an allocated page or pages
   * 
   * @param start The address of the start of the page or pages
   */
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
  @Inline
  public ObjectReference traceObject(TraceLocal trace,
                                           ObjectReference object) { 
    if (MarkSweepLocal.HEADER_MARK_BITS) {
      if (testAndMark(object, markState)) {
        MarkSweepLocal.liveBlock(object);
        trace.enqueue(object);
      }
    } else {
      if (MarkSweepLocal.liveObject(object)) {
        trace.enqueue(object);
      }
    }
    return object;
  }

  /**
   * 
   * @param object The object in question
   * @return True if this object is known to be live (i.e. it is marked)
   */
  @Inline
  public boolean isLive(ObjectReference object) { 
    if (MarkSweepLocal.HEADER_MARK_BITS) {
	return testMarkState(object, markState);
    } else {
      return MarkSweepLocal.isLiveObject(object);
    }
  }
  
  /**
   * Get the current mark state
   * 
   * @return The current mark state.
   */
  @Inline
  public Word getMarkState() { 
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(markState.and(MARK_COUNT_MASK.not()).isZero());
    return markState;
  }
  
  /**
   * Get the previous mark state.
   *  
   * @return The previous mark state.
   */
  @Inline
  public Word getPreviousMarkState() { 
    return deltaMarkState(false);
  }

  /**
   * Return the mark state incremented or decremented by one.
   * 
   * @param increment If true, then return the incremented value else return the decremented value
   * @return the mark state incremented or decremented by one.
   */
  private Word deltaMarkState(boolean increment) {
    Word mask = Word.fromIntZeroExtend((1 << Options.markSweepMarkBits.getValue()) - 1).lsh(COUNT_BASE);
    Word rtn = increment ? markState.plus(MARK_COUNT_INCREMENT) : markState.minus(MARK_COUNT_INCREMENT);
    rtn = rtn.and(mask);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(markState.and(MARK_COUNT_MASK.not()).isZero());
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
    if (MarkSweepLocal.HEADER_MARK_BITS) {
      if (majorGC) MarkSweepLocal.liveBlock(object);
    } else {
      MarkSweepLocal.liveObject(object);
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
    if (MarkSweepLocal.HEADER_MARK_BITS)
      if (alloc) 
        writeAllocState(object);	
      else
        writeMarkState(object);
   }

  /**
   * Atomically attempt to set the mark bit of an object.  Return true
   * if successful, false if the mark bit was already set.
   *
   * @param object The object whose mark bit is to be written
   * @param value The value to which the mark bits will be set
   */
  @Inline
  private static boolean testAndMark(ObjectReference object, Word value) { 
    Word oldValue, markBits;
    oldValue = VM.objectModel.readAvailableBitsWord(object);
    markBits = oldValue.and(MARK_BITS_MASK);
    if (markBits.EQ(value)) return false;
    VM.objectModel.writeAvailableBitsWord(object, oldValue.and(MARK_BITS_MASK.not()).or(value));
    return true;
  }

  /**
   * Return true if the mark count for an object has the given value.
   * 
   * @param object The object whose mark bit is to be tested
   * @param value The value against which the mark bit will be tested
   * @return True if the mark bit for the object has the given value.
   */
  @Inline
  public static boolean testMarkState(ObjectReference object, Word value) { 
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(value.and(MARK_COUNT_MASK.not()).isZero());
    return VM.objectModel.readAvailableBitsWord(object).and(MARK_COUNT_MASK).EQ(value);
  }

  /**
   * Write the allocState into the mark state fields of an object non-atomically.
   * This is appropriate for allocation time initialization.
   * 
   * @param object The object whose mark state is to be written
   */
  @Inline
  private void writeAllocState(ObjectReference object) {
    Word oldValue = VM.objectModel.readAvailableBitsWord(object);
    Word newValue = oldValue.and(MARK_BITS_MASK.not()).or(allocState);
    VM.objectModel.writeAvailableBitsWord(object, newValue);
  }
  
  /**
   * Write the markState into the mark state fields of an object non-atomically.
   * This is appropriate for collection time initialization.
   * 
   * @param object The object whose mark state is to be written
   */
  @Inline
  private void writeMarkState(ObjectReference object) {
    Word oldValue = VM.objectModel.readAvailableBitsWord(object);
    Word newValue = oldValue.and(MARK_BITS_MASK.not()).or(markState);
    VM.objectModel.writeAvailableBitsWord(object, newValue);
  }
}
