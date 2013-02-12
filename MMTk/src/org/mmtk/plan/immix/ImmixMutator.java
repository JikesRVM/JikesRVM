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
package org.mmtk.plan.immix;

import org.mmtk.plan.*;
import org.mmtk.policy.Space;
import org.mmtk.policy.immix.MutatorLocal;

import org.mmtk.utility.alloc.Allocator;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior
 * and state for the <i>Immix</i> plan, which implements a full-heap
 * immix collector.<p>
 *
 * Specifically, this class defines <i>Immix</i> mutator-time allocation
 * and per-mutator thread collection semantics (flushing and restoring
 * per-mutator allocator state).<p>
 *
 * @see Immix
 * @see org.mmtk.policy.immix.CollectorLocal
 * @see StopTheWorldMutator
 * @see MutatorContext
 */
@Uninterruptible
public class ImmixMutator extends StopTheWorldMutator {

  /****************************************************************************
   * Instance fields
   */
  protected final MutatorLocal immix;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public ImmixMutator() {
    immix = new org.mmtk.policy.immix.MutatorLocal(Immix.immixSpace, false);
  }

  /****************************************************************************
   *
   * MutatorLocal-time allocation
   */

  /**
   * {@inheritDoc}<p>
   *
   * This class handles the default allocator from the mark sweep space,
   * and delegates everything else to the superclass.
   */
  @Override
  @Inline
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == Immix.ALLOC_DEFAULT)
      return immix.alloc(bytes, align, offset);
    return super.alloc(bytes, align, offset, allocator, site);
  }

  /**
   * {@inheritDoc}
   *
   * Initialize the object header for objects in the mark-sweep space,
   * and delegate to the superclass for other objects.
   */
  @Override
  @Inline
  public void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) {
    if (allocator == Immix.ALLOC_DEFAULT)
      Immix.immixSpace.postAlloc(ref, bytes);
    else
      super.postAlloc(ref, typeRef, bytes, allocator);
  }

  @Override
  public Allocator getAllocatorFromSpace(Space space) {
    if (space == Immix.immixSpace) return immix;  // FIXME is it not a problem that we have a 2:1 mapping?
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

    if (phaseId == Immix.PREPARE) {
      super.collectionPhase(phaseId, primary);
      immix.prepare();
      return;
    }

    if (phaseId == Immix.RELEASE) {
      immix.release();
      super.collectionPhase(phaseId, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }
}
