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
 * per-mutator allocator state).<p>
 *
 * See {@link MC} for an overview of the mark-compact algorithm.<p>
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
  private final MarkCompactLocal mc;

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
   * {@inheritDoc}<p>
   *
   * This class handles the default allocator from the mark sweep space,
   * and delegates everything else to the superclass.
   */
  @Override
  @Inline
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == MC.ALLOC_DEFAULT) {
      return mc.alloc(bytes, align, offset);
    }
    return super.alloc(bytes, align, offset, allocator, site);
  }

  /**
   * {@inheritDoc}<p>
   *
   * Initialize the object header for objects in the mark-sweep space,
   * and delegate to the superclass for other objects.
   */
  @Override
  @Inline
  public void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) {
    if (allocator == MC.ALLOC_DEFAULT)
      MC.mcSpace.initializeHeader(ref);
    else
      super.postAlloc(ref, typeRef, bytes, allocator);
  }

  @Override
  public Allocator getAllocatorFromSpace(Space space) {
    if (space == MC.mcSpace) return mc;
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
  public final void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == MC.PREPARE) {
      mc.prepare();
      super.collectionPhase(phaseId, primary);
      return;
    }

    if (phaseId == MC.RELEASE) {
      super.collectionPhase(phaseId, primary);
      return;
    }
    super.collectionPhase(phaseId, primary);
  }

  /**
   * Flush the pages this mutator has allocated back to the global
   * dirty page list, where the collectors can find them.
   */
  @Override
  public void flush() {
    super.flush();
    mc.flush();
  }



}
