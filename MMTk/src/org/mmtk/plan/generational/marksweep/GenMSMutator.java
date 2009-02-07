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
package org.mmtk.plan.generational.marksweep;

import org.mmtk.plan.generational.*;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.Allocator;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior and state for
 * the <code>GenMS</code> two-generational copying collector.<p>
 *
 * Specifically, this class defines mutator-time semantics specific to the
 * mature generation (<code>GenMutator</code> defines nursery semantics).
 * In particular the mature space allocator is defined (for mutator-time
 * allocation into the mature space via pre-tenuring), and the mature space
 * per-mutator thread collection time semantics are defined (rebinding
 * the mature space allocator).<p>
 *
 * See {@link GenMS} for a description of the <code>GenMS</code> algorithm.
 *
 * @see GenMS
 * @see GenMSCollector
 * @see GenMutator
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 */
@Uninterruptible
public class GenMSMutator extends GenMutator {
  /******************************************************************
   * Instance fields
   */

  /**
   * The allocator for the mark-sweep mature space (the mutator may
   * "pretenure" objects into this space which is otherwise used
   * only by the collector)
   */
  private final MarkSweepLocal mature;


  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public GenMSMutator() {
    mature = new MarkSweepLocal(GenMS.msSpace);
  }

  /****************************************************************************
   *
   * Mutator-time allocation
   */

  /**
   * Allocate memory for an object.
   *
   * @param bytes The number of bytes required for the object.
   * @param align Required alignment for the object.
   * @param offset Offset associated with the alignment.
   * @param allocator The allocator associated with this request.
   * @param site Allocation site
   * @return The low address of the allocated memory.
   */
  @Inline
  public final Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == GenMS.ALLOC_MATURE) {
      return mature.alloc(bytes, align, offset);
    }
    return super.alloc(bytes, align, offset, allocator, site);
  }

  /**
   * Perform post-allocation actions.  For many allocators none are
   * required.
   *
   * @param ref The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  @Inline
  public final void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) {
    if (allocator == GenMS.ALLOC_MATURE) {
      GenMS.msSpace.initializeHeader(ref, true);
    } else {
      super.postAlloc(ref, typeRef, bytes, allocator);
    }
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
    if (space == GenMS.msSpace) return mature;
    return super.getAllocatorFromSpace(space);
  }

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a per-mutator collection phase.
   *
   * @param phaseId Collection phase to perform
   * @param primary Is this thread to do the one-off thread-local tasks
   */
  @NoInline
  public void collectionPhase(short phaseId, boolean primary) {
    if (global().traceFullHeap()) {
      if (phaseId == GenMS.PREPARE) {
        super.collectionPhase(phaseId, primary);
        if (global().gcFullHeap) mature.prepare();
        return;
      }

      if (phaseId == GenMS.RELEASE) {
        if (global().gcFullHeap) mature.release();
        super.collectionPhase(phaseId, primary);
        return;
      }
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as a <code>GenMS</code> instance. */
  @Inline
  private static GenMS global() {
    return (GenMS) VM.activePlan.global();
  }
}
