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
public abstract class CMSMutator extends ConcurrentMutator {

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
   *
   * @param bytes The number of bytes required for the object.
   * @param align Required alignment for the object.
   * @param offset Offset associated with the alignment.
   * @param allocator The allocator associated with this request.
   * @return The low address of the allocated memory.
   */
  @Inline
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == CMS.ALLOC_DEFAULT) {
      return ms.alloc(bytes, align, offset, false);
    }
    return super.alloc(bytes, align, offset, allocator, site);
  }

  /**
   * Perform post-allocation actions.  Initialize the object header for
   * objects in the mark-sweep space, and delegate to the superclass for
   * other objects.
   *
   * @param ref The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  @Inline
  public void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) {
    if (allocator == CMS.ALLOC_DEFAULT)
      CMS.msSpace.initializeHeader(ref, false);
    else
      super.postAlloc(ref, typeRef, bytes, allocator);
  }

  /**
   * Return the space into which an allocator is allocating.  This
   * particular method will match against those spaces defined at this
   * level of the class hierarchy.  Subclasses must deal with spaces
   * they define and refer to superclasses appropriately.
   *
   * @param a An allocator
   * @return The space into which <code>a</code> is allocating, or
   *         <code>null</code> if there is no space associated with
   *         <code>a</code>.
   */
  public Space getSpaceFromAllocator(Allocator a) {
    if (a == ms) return CMS.msSpace;
    return super.getSpaceFromAllocator(a);
  }

  /**
   * Return the allocator instance associated with a space
   * <code>space</code>, for this plan instance.
   *
   * @param space The space for which the allocator instance is desired.
   * @return The allocator instance associated with this plan instance
   * which is allocating into <code>space</code>, or <code>null</code>
   * if no appropriate allocator can be established.
   */
  public Allocator getAllocatorFromSpace(Space space) {
    if (space == CMS.msSpace) return ms;
    return super.getAllocatorFromSpace(space);
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a per-mutator collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param primary Perform any single-threaded activities using this thread.
   */
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == CMS.PREPARE) {
      super.collectionPhase(phaseId, primary);
      ms.prepare();
      return;
    }

    if (phaseId == CMS.RELEASE) {
      ms.releaseCollector();
      ms.releaseMutator(); // FIXME see block comment at top of this class
      super.collectionPhase(phaseId, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /**
   * Flush per-mutator remembered sets into the global remset pool.
   */
  public void flushRememberedSets() {
    remset.flush();
  }

  /****************************************************************************
   *
   * Write and read barriers.
   */

  /**
   * Process a reference that may require being enqueued as part of a concurrent
   * collection.
   *
   * @param ref The reference to check.
   */
  protected void checkAndEnqueueReference(ObjectReference ref) {
    if (barrierActive) {
      if (!ref.isNull()) {
        if      (Space.isInSpace(CMS.MARK_SWEEP, ref)) CMS.msSpace.traceObject(remset, ref);
        else if (Space.isInSpace(CMS.IMMORTAL,   ref)) CMS.immortalSpace.traceObject(remset, ref);
        else if (Space.isInSpace(CMS.PLOS,       ref)) CMS.ploSpace.traceObject(remset, ref);
        else if (Space.isInSpace(CMS.LOS,        ref)) CMS.loSpace.traceObject(remset, ref);
      }
    }
    if (VM.VERIFY_ASSERTIONS) {
      if (!ref.isNull() && !Plan.gcInProgress()) {
        if      (Space.isInSpace(CMS.MARK_SWEEP, ref)) VM.assertions._assert(CMS.msSpace.isLive(ref));
        else if (Space.isInSpace(CMS.PLOS,       ref)) VM.assertions._assert(CMS.ploSpace.isLive(ref));
        else if (Space.isInSpace(CMS.LOS,        ref)) VM.assertions._assert(CMS.loSpace.isLive(ref));
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
