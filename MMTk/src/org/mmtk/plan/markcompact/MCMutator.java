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
package org.mmtk.plan.markcompact;

import org.mmtk.plan.StopTheWorldMutator;
import org.mmtk.policy.MarkCompactLocal;
import org.mmtk.policy.Space;

import org.mmtk.utility.alloc.Allocator;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior
 * and state for the <i>MC</i> plan, which implements a full-heap
 * mark-compact collector.<p>
 *
 * Specifically, this class defines <i>MC</i> mutator-time allocation
 * and per-mutator thread collection semantics (flushing and restoring
 * per-mutator allocator state).
 *
 * See {@link MC} for an overview of the mark-compact algorithm.<p>
 *
 * FIXME Currently MC does not properly separate mutator and collector
 * behaviors, so some of the collection logic here should really be
 * per-collector thread, not per-mutator thread.
 *
 * @see MC
 * @see MCCollector
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 */
@Uninterruptible public class MCMutator extends StopTheWorldMutator {

  /****************************************************************************
   * Instance fields
   */
  private MarkCompactLocal mc;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public MCMutator() {
    mc = new MarkCompactLocal(MC.mcSpace);
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
   * @param site Allocation site
   * @return The low address of the allocated memory.
   */
  @Inline
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == MC.ALLOC_DEFAULT) {
      return mc.alloc(bytes, align, offset);
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
    if (allocator == MC.ALLOC_DEFAULT)
      MC.mcSpace.initializeHeader(ref);
    else
      super.postAlloc(ref, typeRef, bytes, allocator);
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
    if (space == MC.mcSpace) return mc;
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
  public final void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == MC.PREPARE) {
      super.collectionPhase(phaseId, primary);
      return;
    }

    /* FIXME this needs to be made a per-collector phase */
    if (phaseId == MC.CALCULATE_FP) {
      mc.calculateForwardingPointers();
      return;
    }

    /* FIXME this needs to be made a per-collector phase */
    if (phaseId == MC.COMPACT) {
      mc.compact();
      return;
    }

    if (phaseId == MC.RELEASE) {
      super.collectionPhase(phaseId, primary);
      return;
    }
    super.collectionPhase(phaseId, primary);
  }

}
