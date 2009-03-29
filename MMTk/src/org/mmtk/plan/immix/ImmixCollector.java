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
package org.mmtk.plan.immix;

import org.mmtk.plan.*;
import org.mmtk.policy.ImmortalLocal;
import org.mmtk.policy.immix.CollectorLocal;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.alloc.ImmixAllocator;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements <i>per-collector thread</i> behavior
 * and state for the <i>Immix</i> plan, which implements a full-heap
 * immix collector.<p>
 *
 * Specifically, this class defines <i>Immix</i> collection behavior
 * (through <code>fastTrace</code> and the <code>collectionPhase</code>
 * method).<p>
 *
 * @see Immix for an overview of the immix algorithm.<p>
 *
 * FIXME The SegregatedFreeList class (and its decendents such as
 * MarkSweepLocal) does not properly separate mutator and collector
 * behaviors, so the immix field below should really not exist in
 * this class as there is no collection-time allocation in this
 * collector.
 *
 * @see Immix
 * @see org.mmtk.policy.immix.MutatorLocal
 * @see StopTheWorldCollector
 * @see CollectorContext
 * @see Phase
 */
@Uninterruptible
public class ImmixCollector extends StopTheWorldCollector {

  /****************************************************************************
   * Instance fields
   */
   protected ImmixTraceLocal fastTrace;
   protected ImmixDefragTraceLocal defragTrace;
   protected CollectorLocal immix;
   protected final ImmixAllocator copy;
   protected final BumpPointer immortal;
   protected TraceLocal currentTrace;

  /****************************************************************************
   * Initialization
   */

  /**
   * Constructor
   */
  public ImmixCollector() {
    fastTrace = new ImmixTraceLocal(global().immixTrace, null);
    defragTrace = new ImmixDefragTraceLocal(global().immixTrace, null);
    immix = new CollectorLocal(Immix.immixSpace);
    copy = new ImmixAllocator(Immix.immixSpace, true, true);
    immortal = new ImmortalLocal(Plan.immortalSpace);
  }

 /****************************************************************************
  *
  * Collection-time allocation
  */

 /**
  * Allocate space for copying an object (this method <i>does not</i>
  * copy the object, it only allocates space)
  *
  * @param original A reference to the original object
  * @param bytes The size of the space to be allocated (in bytes)
  * @param align The requested alignment.
  * @param offset The alignment offset.
  * @return The address of the first byte of the allocated region
  */
  @Inline
  public Address allocCopy(ObjectReference original, int bytes,
      int align, int offset, int allocator) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(bytes <= Plan.MAX_NON_LOS_COPY_BYTES);
      VM.assertions._assert(allocator == Immix.ALLOC_DEFAULT);
    }
    return copy.alloc(bytes, align, offset);
  }

 /**
  * Perform any post-copy actions.
  *
  * @param object The newly allocated object
  * @param typeRef the type reference for the instance being created
  * @param bytes The size of the space to be allocated (in bytes)
  */
  @Inline
  public void postCopy(ObjectReference object, ObjectReference typeRef,
      int bytes, int allocator) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(allocator == Immix.ALLOC_DEFAULT);
    Immix.immixSpace.postCopy(object, bytes, true);

    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(getCurrentTrace().isLive(object));
      VM.assertions._assert(getCurrentTrace().willNotMoveInCurrentCollection(object));
    }
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a per-collector collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param primary Perform any single-threaded activities using this thread.
   */
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {

    if (phaseId == Immix.PREPARE) {
      super.collectionPhase(phaseId, primary);
      currentTrace = Immix.immixSpace.inImmixDefragCollection() ? defragTrace : fastTrace;
      immix.prepare(true);
      currentTrace.prepare();
      copy.reset();
      return;
    }

    if (phaseId == Immix.CLOSURE) {
      currentTrace.completeTrace();
      return;
    }

    if (phaseId == Immix.RELEASE) {
      currentTrace.release();
      immix.release(true);
      super.collectionPhase(phaseId, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as an <code>Immix</code> instance. */
  @Inline
  private static Immix global() {
    return (Immix) VM.activePlan.global();
  }

  /** @return The current fastTrace instance. */
  @Inline
  public final TraceLocal getCurrentTrace() {
    return currentTrace;
  }
}
