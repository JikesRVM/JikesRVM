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
package org.mmtk.plan.concurrent.marksweep;

import org.mmtk.plan.*;
import org.mmtk.plan.concurrent.ConcurrentMutator;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.policy.Space;

import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior
 * and state for the <i>CMS</i> plan, which implements a full-heap
 * concurrent mark-sweep collector.<p>
 *
 * FIXME The SegregatedFreeList class (and its descendants such as
 * MarkSweepLocal) does not properly separate mutator and collector
 * behaviors, so the ms field below should really not exist in
 * this class as there is no collection-time allocation in this
 * collector.
 *
 * @see CMS
 * @see CMSCollector
 * @see StopTheWorldMutator
 * @see MutatorContext
 */
@Uninterruptible
public class CMSMutator extends ConcurrentMutator {

  /****************************************************************************
   * Instance fields
   */
  private MarkSweepLocal ms;
  private TraceWriteBuffer remset;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public CMSMutator() {
    ms = new MarkSweepLocal(CMS.msSpace);
    remset = new TraceWriteBuffer(global().msTrace);
  }

  /****************************************************************************
   *
   * Mutator-time allocation
   */

  /**
   * Allocate memory for an object. This class handles the default allocator
   * from the mark sweep space, and delegates everything else to the
   * superclass.
   */
  @Inline
  @Override
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == CMS.ALLOC_DEFAULT) {
      return ms.alloc(bytes, align, offset);
    }
    return super.alloc(bytes, align, offset, allocator, site);
  }

  /**
   * Perform post-allocation actions.  Initialize the object header for
   * objects in the mark-sweep space, and delegate to the superclass for
   * other objects.
   */
  @NoInline
  @Override
  public void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) {
    switch (allocator) {
      case CMS.ALLOC_DEFAULT: CMS.msSpace.initializeHeader(ref, true); break;
      default:
        super.postAlloc(ref, typeRef, bytes, allocator);
        break;
    }
  }

  @Override
  public Allocator getAllocatorFromSpace(Space space) {
    if (space == CMS.msSpace) return ms;
    return super.getAllocatorFromSpace(space);
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * {@inheritDoc}
   */
  @Override
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == CMS.PREPARE) {
      super.collectionPhase(phaseId, primary);
      ms.prepare();
      return;
    }

    if (phaseId == CMS.RELEASE) {
      ms.release();
      super.collectionPhase(phaseId, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  @Override
  public void flushRememberedSets() {
    remset.flush();
    ms.flush();
  }

  /****************************************************************************
   *
   * Write and read barriers.
   */

  /**
   * {@inheritDoc}
   */
  @Override
  protected void checkAndEnqueueReference(ObjectReference ref) {
    if (ref.isNull()) return;
    if (barrierActive) {
      if (!ref.isNull()) {
        if      (Space.isInSpace(CMS.MARK_SWEEP, ref)) CMS.msSpace.traceObject(remset, ref);
        else if (Space.isInSpace(CMS.IMMORTAL,   ref)) CMS.immortalSpace.traceObject(remset, ref);
        else if (Space.isInSpace(CMS.LOS,        ref)) CMS.loSpace.traceObject(remset, ref);
        else if (Space.isInSpace(CMS.NON_MOVING, ref)) CMS.nonMovingSpace.traceObject(remset, ref);
        else if (Space.isInSpace(CMS.SMALL_CODE, ref)) CMS.smallCodeSpace.traceObject(remset, ref);
        else if (Space.isInSpace(CMS.LARGE_CODE, ref)) CMS.largeCodeSpace.traceObject(remset, ref);
      }
    }

    if (VM.VERIFY_ASSERTIONS) {
      if (!ref.isNull() && !Plan.gcInProgress()) {
        if      (Space.isInSpace(CMS.MARK_SWEEP, ref)) VM.assertions._assert(CMS.msSpace.isLive(ref));
        else if (Space.isInSpace(CMS.IMMORTAL,   ref)) VM.assertions._assert(CMS.immortalSpace.isLive(ref));
        else if (Space.isInSpace(CMS.LOS,        ref)) VM.assertions._assert(CMS.loSpace.isLive(ref));
        else if (Space.isInSpace(CMS.NON_MOVING, ref)) VM.assertions._assert(CMS.nonMovingSpace.isLive(ref));
        else if (Space.isInSpace(CMS.SMALL_CODE, ref)) VM.assertions._assert(CMS.smallCodeSpace.isLive(ref));
        else if (Space.isInSpace(CMS.LARGE_CODE, ref)) VM.assertions._assert(CMS.largeCodeSpace.isLive(ref));
      }
    }
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as a <code>Gen</code> instance. */
  @Inline
  private static CMS global() {
    return (CMS) VM.activePlan.global();
  }
}
