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
import org.mmtk.utility.heap.FreeListPageResource;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.DoublyLinkedList;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Each instance of this class corresponds to one explicitly managed
 * large object space.
 */
@Uninterruptible
public final class ExplicitLargeObjectSpace extends BaseLargeObjectSpace {

  /****************************************************************************
   *
   * Instance variables
   */
  private final DoublyLinkedList cells;

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
  public ExplicitLargeObjectSpace(String name, VMRequest vmRequest) {
    super(name, vmRequest);
    cells = new DoublyLinkedList(LOG_BYTES_IN_PAGE, true);
  }


  /****************************************************************************
   *
   * Collection
   */

  /**
   * Prepare for a new collection increment.
   */
  public void prepare() {
  }

  /**
   * A new collection increment has completed.
   */
  public void release() {
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

  /**
   * Perform any required initialization of the GC portion of the header.
   *
   * @param object the object ref to the storage to be initialized
   * @param alloc is this initialization occuring due to (initial) allocation
   * (true) or due to copying (false)?
   */
  @Inline
  public void initializeHeader(ObjectReference object, boolean alloc) {
    Address cell = VM.objectModel.objectStartRef(object);
    cells.add(DoublyLinkedList.midPayloadToNode(cell));
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
    return object;
  }

  /**
   * @param object The object in question
   * @return True if this object is known to be live (i.e. it is marked)
   */
  @Inline
  public boolean isLive(ObjectReference object) {
    return true;
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
    return DoublyLinkedList.headerSize();
  }

  /**
   * Return the size of the per-cell header for cells of a given class
   * size.
   *
   * @return The size of the per-cell header for cells of a given class
   * size.
   */
  @Inline
  protected int cellHeaderSize() { return 0; }

  /**
   * Sweep through all the objects in this space.
   *
   * @param sweeper The sweeper callback to use.
   */
  @Inline
  public void sweep(Sweeper sweeper) {
    Address cell = cells.getHead();
    while (!cell.isZero()) {
      Address next = cells.getNext(cell);
      ObjectReference obj = VM.objectModel.getObjectFromStartAddress(cell.plus(DoublyLinkedList.headerSize()));
      if (sweeper.sweepLargeObject(obj)) {
        free(obj);
      }
      cell = next;
    }
  }

  /**
   * Free an object
   *
   * @param object The object to be freed.
   */
  @Inline
  public void free(ObjectReference object) {
    Address cell = getSuperPage(VM.objectModel.refToAddress(object));
    cells.remove(cell);
    release(cell);
  }

  /**
   * A callback used to perform sweeping of the large object space.
   */
  @Uninterruptible
  public abstract static class Sweeper {
    public abstract boolean sweepLargeObject(ObjectReference object);
  }
}
