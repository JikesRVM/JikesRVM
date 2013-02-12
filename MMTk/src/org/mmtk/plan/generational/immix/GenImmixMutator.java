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
package org.mmtk.plan.generational.immix;

import org.mmtk.plan.generational.*;
import org.mmtk.policy.Space;
import org.mmtk.policy.immix.MutatorLocal;
import org.mmtk.utility.alloc.Allocator;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior and state for
 * the <code>GenImmix</code> two-generational copying collector.<p>
 *
 * Specifically, this class defines mutator-time semantics specific to the
 * mature generation (<code>GenMutator</code> defines nursery semantics).
 * In particular the mature space allocator is defined (for mutator-time
 * allocation into the mature space via pre-tenuring), and the mature space
 * per-mutator thread collection time semantics are defined (rebinding
 * the mature space allocator).<p>
 *
 * See {@link GenImmix} for a description of the <code>GenImmix</code> algorithm.
 *
 * @see GenImmix
 * @see GenImmixCollector
 * @see org.mmtk.plan.generational.GenMutator
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 * @see org.mmtk.plan.Phase
 */
@Uninterruptible
public class GenImmixMutator extends GenMutator {

  /******************************************************************
   * Instance fields
   */

  /**
   * The allocator for the mark-sweep mature space (the mutator may
   * "pretenure" objects into this space which is otherwise used
   * only by the collector)
   */
  private final MutatorLocal mature;


  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public GenImmixMutator() {
    mature = new MutatorLocal(GenImmix.immixSpace, false);
  }

  /****************************************************************************
   *
   * Mutator-time allocation
   */

  /**
   * {@inheritDoc}
   */
  @Override
  @Inline
  public final Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == GenImmix.ALLOC_MATURE) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false); // no pretenuring yet
      return mature.alloc(bytes, align, offset);
    }
    return super.alloc(bytes, align, offset, allocator, site);
  }

  @Override
  @Inline
  public final void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) {
    if (allocator == GenImmix.ALLOC_MATURE) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false); // no pretenuring yet
    } else {
      super.postAlloc(ref, typeRef, bytes, allocator);
    }
  }

  @Override
  public Allocator getAllocatorFromSpace(Space space) {
    if (space == GenImmix.immixSpace) return mature;
    return super.getAllocatorFromSpace(space);
  }

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * {@inheritDoc}
   */
  @Override
  @NoInline
  public void collectionPhase(short phaseId, boolean primary) {
    if (global().traceFullHeap()) {
      if (phaseId == GenImmix.PREPARE) {
        super.collectionPhase(phaseId, primary);
        if (global().gcFullHeap) mature.prepare();
        return;
      }

      if (phaseId == GenImmix.RELEASE) {
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

  /** @return The active global plan as a <code>GenImmix</code> instance. */
  @Inline
  private static GenImmix global() {
    return (GenImmix) VM.activePlan.global();
  }
}
