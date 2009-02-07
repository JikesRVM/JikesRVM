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
package org.mmtk.plan.copyms;

import org.mmtk.plan.StopTheWorldMutator;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.policy.Space;

import org.mmtk.utility.alloc.Allocator;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior
 * and state for the <i>CopyMS</i> plan.<p>
 *
 * Specifically, this class defines <i>CopyMS</i> mutator-time
 * allocation into the nursery and mature space (through pre-tenuring).
 * Per-mutator thread collection semantics are also defined (flushing
 * and restoring per-mutator allocator state).
 *
 * @see CopyMS
 * @see CopyMSCollector
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 */
@Uninterruptible
public class CopyMSMutator extends StopTheWorldMutator {

  /****************************************************************************
   * Instance fields
   */

  private final MarkSweepLocal mature;
  private final CopyLocal nursery;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public CopyMSMutator() {
    mature = new MarkSweepLocal(CopyMS.msSpace);
    nursery = new CopyLocal(CopyMS.nurserySpace);
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
    if (allocator == CopyMS.ALLOC_DEFAULT)
      return nursery.alloc(bytes, align, offset);
    if (allocator == CopyMS.ALLOC_MS)
      return mature.alloc(bytes, align, offset);

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
  @SuppressWarnings({"UnnecessaryReturnStatement"})
  @Inline
  public void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) {
    if (allocator == CopyMS.ALLOC_DEFAULT)
      return;
    else if (allocator == CopyMS.ALLOC_MS)
      CopyMS.msSpace.initializeHeader(ref, true);
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
    if (space == CopyMS.nurserySpace) return nursery;
    if (space == CopyMS.msSpace) return mature;
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
   * @param primary Use this thread for single-threaded local activities.
   */
  @Inline
  public final void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == CopyMS.PREPARE) {
      super.collectionPhase(phaseId, primary);
      mature.prepare();
      return;
    }

    if (phaseId == CopyMS.RELEASE) {
      nursery.reset();
      mature.release();
      super.collectionPhase(phaseId, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

}
