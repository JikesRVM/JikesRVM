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
package org.mmtk.plan.generational.copying;

import org.mmtk.plan.generational.GenMutator;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-mutator thread</i> behavior and state for
 * the <code>GenCopy</code> two-generational copying collector.<p>
 *
 * Specifically, this class defines mutator-time semantics specific to the
 * mature generation (<code>GenMutator</code> defines nursery semantics).
 * In particular the mature space allocator is defined (for mutator-time
 * allocation into the mature space via pre-tenuring), and the mature space
 * per-mutator thread collection time semantics are defined (rebinding
 * the mature space allocator).<p>
 *
 * @see GenCopy for a description of the <code>GenCopy</code> algorithm.
 *
 * @see GenCopy
 * @see GenCopyCollector
 * @see GenMutator
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 */
@Uninterruptible
public class GenCopyMutator extends GenMutator {
  /******************************************************************
   * Instance fields
   */

  /**
   * The allocator for the copying mature space (the mutator may
   * "pretenure" objects into this space otherwise used only by
   * the collector)
   */
  private CopyLocal mature;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public GenCopyMutator() {
    mature = new CopyLocal();
  }

  /**
   * Called before the MutatorContext is used, but after the context has been
   * fully registered and is visible to collection.
   */
  public void initMutator(int id) {
    super.initMutator(id);
    mature.rebind(GenCopy.toSpace());
  }

  /****************************************************************************
   *
   * Mutator-time allocation
   */

  /**
   * Allocate space (for an object) in the specified space
   *
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @param allocator The allocator to allocate from
   * @param site Allocation site
   * @return The address of the first byte of the allocated region
   */
  @Inline
  public final Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == GenCopy.ALLOC_MATURE) {
      return mature.alloc(bytes, align, offset);
    }
    return super.alloc(bytes, align, offset, allocator, site);
  }

  /**
   * Perform post-allocation initialization of an object
   *
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param allocator The allocator to allocate from
   * @param bytes The size of the space allocated (in bytes)
   */
  @Inline
  public final void postAlloc(ObjectReference object, ObjectReference typeRef,
      int bytes, int allocator) {
    // nothing to be done
    if (allocator == GenCopy.ALLOC_MATURE) return;
    super.postAlloc(object, typeRef, bytes, allocator);
  }

  /**
   * Return the allocator instance associated with a space
   * <code>space</code>, for this plan instance.  This exists
   * to support {@link org.mmtk.plan.MutatorContext#getOwnAllocator(Allocator)}.
   *
   * @see org.mmtk.plan.MutatorContext#getOwnAllocator(Allocator)
   * @param space The space for which the allocator instance is desired.
   * @return The allocator instance associated with this plan instance
   * which is allocating into <code>space</code>, or <code>null</code>
   * if no appropriate allocator can be established.
   */
  public final Allocator getAllocatorFromSpace(Space space) {
    if (space == GenCopy.matureSpace0 || space == GenCopy.matureSpace1) return mature;
    return super.getAllocatorFromSpace(space);
  }


  /*****************************************************************************
   *
   * Collection
   */

  /**
   * Execute a per-mutator collection phase.
   *
   * @param phaseId The phase to execute.
   * @param primary True if this thread should peform local single-threaded
   * actions.
   */
  public void collectionPhase(short phaseId, boolean primary) {
    if (global().traceFullHeap()) {
      if (phaseId == GenCopy.PREPARE) {
        super.collectionPhase(phaseId, primary);
        if (global().gcFullHeap) mature.rebind(GenCopy.toSpace());
        return;
      }
    }

    super.collectionPhase(phaseId, primary);
  }

  /*****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as a <code>GenCopy</code> instance. */
  private static GenCopy global() {
    return (GenCopy) VM.activePlan.global();
  }

}
