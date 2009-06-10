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

import org.mmtk.plan.Plan;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.heap.FreeListPageResource;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.Treadmill;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Each instance of this class corresponds to one explicitly managed
 * large object space.
 */
@Uninterruptible
public final class LargeObjectSpace extends BaseLargeObjectSpace {

  /****************************************************************************
   *
   * Class variables
   */
  public static final int LOCAL_GC_BITS_REQUIRED = 2;
  public static final int GLOBAL_GC_BITS_REQUIRED = 0;
  private static final Word MARK_BIT = Word.one(); // ...01
  private static final Word NURSERY_BIT = Word.fromIntZeroExtend(2); // ...10
  private static final Word LOS_BIT_MASK = Word.fromIntZeroExtend(3); // ...11

  /****************************************************************************
   *
   * Instance variables
   */
  private Word markState;
  private boolean inNurseryGC;
  private final Treadmill treadmill;

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
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param vmRequest An object describing the virtual memory requested.
   */
  public LargeObjectSpace(String name, int pageBudget, VMRequest vmRequest) {
    super(name, pageBudget, vmRequest);
    treadmill = new Treadmill(LOG_BYTES_IN_PAGE, true);
    markState = Word.zero();
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
  public void prepare(boolean fullHeap) {
    if (fullHeap) {
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(treadmill.fromSpaceEmpty());
      }
      markState = MARK_BIT.minus(markState);
    }
    treadmill.flip(fullHeap);
    inNurseryGC = !fullHeap;
  }

  /**
   * A new collection increment has completed.  For the mark-sweep
   * collector this means we can perform the sweep phase.
   */
  public void release(boolean fullHeap) {
    // sweep the large objects
    sweepLargePages(true);                // sweep the nursery
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(treadmill.nurseryEmpty());
    if (fullHeap) sweepLargePages(false); // sweep the mature space
  }

  /**
   * Sweep through the large pages, releasing all superpages on the
   * "from space" treadmill.
   */
  private void sweepLargePages(boolean sweepNursery) {
    while (true) {
      Address cell = sweepNursery ? treadmill.popNursery() : treadmill.pop();
      if (cell.isZero()) break;
      release(getSuperPage(cell));
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(sweepNursery ? treadmill.nurseryEmpty() : treadmill.fromSpaceEmpty());
  }

  /**
   * Release a group of pages that were allocated together.
   *
   * @param first The first page in the group of pages that were
   * allocated together.
   */
  @Inline
  public void release(Address first) {
    ((FreeListPageResource) pr).releasePages(first);
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
   * @param trace The trace being conducted.
   * @param object The object to be traced.
   * @return The object (there is no object forwarding in this
   * collector, so we always return the same object: this could be a
   * void method but for compliance to a more general interface).
   */
  @Inline
  public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object) {
    boolean nurseryObject = isInNursery(object);
    if (!inNurseryGC || nurseryObject) {
      if (testAndMark(object, markState)) {
        internalMarkObject(object, nurseryObject);
        trace.processNode(object);
      }
    }
    return object;
  }

  /**
   * @param object The object in question
   * @return True if this object is known to be live (i.e. it is marked)
   */
   @Inline
   public boolean isLive(ObjectReference object) {
    return testMarkBit(object, markState);
  }

  /**
   * An object has been marked (identifiged as live).  Large objects
   * are added to the to-space treadmill, while all other objects will
   * have a mark bit set in the superpage header.
   *
   * @param object The object which has been marked.
   */
  @Inline
  private void internalMarkObject(ObjectReference object, boolean nurseryObject) {

    Address cell = VM.objectModel.objectStartRef(object);
    Address node = Treadmill.midPayloadToNode(cell);
    treadmill.copy(node, nurseryObject);
  }

  /****************************************************************************
   *
   * Header manipulation
   */

  /**
   * Perform any required initialization of the GC portion of the header.
   *
   * @param object the object ref to the storage to be initialized
   * @param alloc is this initialization occuring due to (initial) allocation
   * (true) or due to copying (false)?
   */
  @Inline
  public void initializeHeader(ObjectReference object, boolean alloc) {
    Word oldValue = VM.objectModel.readAvailableBitsWord(object);
    Word newValue = oldValue.and(LOS_BIT_MASK.not()).or(markState);
    if (alloc) newValue = newValue.or(NURSERY_BIT);
    if (Plan.NEEDS_LOG_BIT_IN_HEADER) newValue = newValue.or(Plan.UNLOGGED_BIT);
    VM.objectModel.writeAvailableBitsWord(object, newValue);
    Address cell = VM.objectModel.objectStartRef(object);
    treadmill.addToTreadmill(Treadmill.midPayloadToNode(cell), alloc);
  }

  /**
   * Atomically attempt to set the mark bit of an object.  Return true
   * if successful, false if the mark bit was already set.
   *
   * @param object The object whose mark bit is to be written
   * @param value The value to which the mark bit will be set
   */
  @Inline
  private boolean testAndMark(ObjectReference object, Word value) {
    Word oldValue, markBit;
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      markBit = oldValue.and(inNurseryGC ? LOS_BIT_MASK : MARK_BIT);
      if (markBit.EQ(value)) return false;
    } while (!VM.objectModel.attemptAvailableBits(object, oldValue,
                                                  oldValue.and(LOS_BIT_MASK.not()).or(value)));
    return true;
  }

  /**
   * Return true if the mark bit for an object has the given value.
   *
   * @param object The object whose mark bit is to be tested
   * @param value The value against which the mark bit will be tested
   * @return True if the mark bit for the object has the given value.
   */
  @Inline
  private boolean testMarkBit(ObjectReference object, Word value) {
    return VM.objectModel.readAvailableBitsWord(object).and(MARK_BIT).EQ(value);
  }

  /**
   * Return true if the object is in the logical nursery
   *
   * @param object The object whose status is to be tested
   * @return True if the object is in the logical nursery
   */
  @Inline
  private boolean isInNursery(ObjectReference object) {
     return VM.objectModel.readAvailableBitsWord(object).and(NURSERY_BIT).EQ(NURSERY_BIT);
  }

  /**
   * Return the size of the per-superpage header required by this
   * system.  In this case it is just the underlying superpage header
   * size.
   *
   * @return The size of the per-superpage header required by this
   * system.
   */
  @Inline
  protected int superPageHeaderSize() {
    return Treadmill.headerSize();
  }

  /**
   * Return the size of the per-cell header for cells of a given class
   * size.
   *
   * @return The size of the per-cell header for cells of a given class
   * size.
   */
  @Inline
  protected int cellHeaderSize() {
    return 0;
  }

  /**
   * This is the treadmill used by the large object space.
   *
   * Note that it depends on the specific local in use whether this
   * is being used.
   *
   * @return The treadmill associated with this large object space.
   */
  public Treadmill getTreadmill() {
    return this.treadmill;
  }
}
