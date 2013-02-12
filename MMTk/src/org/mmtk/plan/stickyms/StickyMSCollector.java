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
package org.mmtk.plan.stickyms;

import org.mmtk.plan.*;
import org.mmtk.plan.marksweep.MSCollector;
import org.mmtk.plan.marksweep.MSTraceLocal;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-collector thread</i> behavior
 * and state for the <i>StickMS</i> plan, which implements a generational
 * sticky mark bits mark-sweep collector.<p>
 *
 * Specifically, this class defines <i>StickyMS</i> collection behavior
 * (through <code>trace</code> and the <code>collectionPhase</code>
 * method).<p>
 *
 * @see StickyMS for an overview of the algorithm.<p>
 * @see StickyMSMutator
 * @see StopTheWorldCollector
 * @see CollectorContext
 * @see Phase
 */
@Uninterruptible
public class StickyMSCollector extends MSCollector {

  /****************************************************************************
   * Instance fields
   */

  /**
   *
   */
  private StickyMSNurseryTraceLocal nurseryTrace;

  /****************************************************************************
   * Initialization
   */

  /**
   * Constructor
   */
  public StickyMSCollector() {
    ObjectReferenceDeque modBuffer = new ObjectReferenceDeque("mod buffer", global().modPool);
    fullTrace = new  MSTraceLocal(global().msTrace, modBuffer);
    nurseryTrace = new StickyMSNurseryTraceLocal(global().msTrace, modBuffer);
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
    boolean collectWholeHeap = global().collectWholeHeap;

    if (phaseId == StickyMS.PREPARE) {
      currentTrace = collectWholeHeap ? (TraceLocal) fullTrace : (TraceLocal) nurseryTrace;
      global().modPool.prepareNonBlocking();  /* always do this */
    }

    if (!collectWholeHeap) {
      if (phaseId == StickyMS.PREPARE) {
        nurseryTrace.prepare();
        return;
      }

      if (phaseId == StickyMS.ROOTS) {
        VM.scanning.computeStaticRoots(currentTrace);
        VM.scanning.computeGlobalRoots(currentTrace);
        return;
      }

      if (phaseId == StickyMS.CLOSURE) {
        nurseryTrace.completeTrace();
        return;
      }

      if (phaseId == StickyMS.RELEASE) {
        nurseryTrace.release();
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

  /** @return The active global plan as an <code>MS</code> instance. */
  @Inline
  private static StickyMS global() {
    return (StickyMS) VM.activePlan.global();
  }
}
