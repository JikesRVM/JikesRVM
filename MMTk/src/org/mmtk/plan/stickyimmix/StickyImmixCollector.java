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
package org.mmtk.plan.stickyimmix;

import org.mmtk.plan.*;
import org.mmtk.plan.immix.Immix;
import org.mmtk.plan.immix.ImmixCollector;
import org.mmtk.plan.immix.ImmixDefragTraceLocal;
import org.mmtk.plan.immix.ImmixTraceLocal;
import org.mmtk.policy.immix.CollectorLocal;
import org.mmtk.utility.alloc.ImmixAllocator;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-collector thread</i> behavior
 * and state for the <i>StickMS</i> plan, which implements a generational
 * sticky mark bits immix collector.<p>
 *
 * Specifically, this class defines <i>StickyMS</i> collection behavior
 * (through <code>trace</code> and the <code>collectionPhase</code>
 * method).<p>
 *
 * @see StickyImmix for an overview of the algorithm.<p>
 * @see StickyImmixMutator
 * @see StopTheWorldCollector
 * @see CollectorContext
 * @see Phase
 */
@Uninterruptible
public class StickyImmixCollector extends ImmixCollector {

  /****************************************************************************
   * Instance fields
   */
  private StickyImmixNurseryTraceLocal nurseryTrace;
  private final ImmixAllocator nurseryCopy;

  /****************************************************************************
   * Initialization
   */

  /**
   * Constructor
   */
  public StickyImmixCollector() {
    ObjectReferenceDeque modBuffer = new ObjectReferenceDeque("mod buffer", global().modPool);
    fastTrace = new ImmixTraceLocal(global().immixTrace, modBuffer);
    defragTrace = new ImmixDefragTraceLocal(global().immixTrace, modBuffer);
    nurseryTrace = new StickyImmixNurseryTraceLocal(global().immixTrace, modBuffer);
    immix = new CollectorLocal(StickyImmix.immixSpace);
    nurseryCopy = new ImmixAllocator(Immix.immixSpace, true, true);
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
  @Override
  @Inline
  public final void collectionPhase(short phaseId, boolean primary) {
    boolean collectWholeHeap = global().collectWholeHeap;

    if (phaseId == StickyImmix.PREPARE) {
      global().modPool.prepareNonBlocking();  /* always do this */
    }

    if (!collectWholeHeap) {
      if (phaseId == StickyImmix.PREPARE) {
        currentTrace = (TraceLocal) nurseryTrace;
        immix.prepare(false);
        nurseryTrace.prepare();
        nurseryCopy.reset();
        copy.reset();
        return;
      }

      if (phaseId == StickyImmix.ROOTS) {
        VM.scanning.computeStaticRoots(currentTrace);
        VM.scanning.computeGlobalRoots(currentTrace);
        return;
      }

      if (phaseId == StickyImmix.CLOSURE) {
        nurseryTrace.completeTrace();
        return;
      }

      if (phaseId == StickyImmix.RELEASE) {
        nurseryTrace.release();
        immix.release(false);
        global().modPool.reset();
        return;
      }
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as an <code>StickyImmix</code> instance. */
  private static StickyImmix global() {
    return (StickyImmix) VM.activePlan.global();
  }
}
