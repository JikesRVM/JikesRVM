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
package org.mmtk.plan.marksweep;

import org.mmtk.plan.*;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-collector thread</i> behavior
 * and state for the <i>MS</i> plan, which implements a full-heap
 * mark-sweep collector.<p>
 *
 * Specifically, this class defines <i>MS</i> collection behavior
 * (through <code>trace</code> and the <code>collectionPhase</code>
 * method).<p>
 *
 * @see MS for an overview of the mark-sweep algorithm.<p>
 *
 * @see MS
 * @see MSMutator
 * @see StopTheWorldCollector
 * @see CollectorContext
 */
@Uninterruptible
public class MSCollector extends StopTheWorldCollector {

  /****************************************************************************
   * Instance fields
   */
  protected MSTraceLocal fullTrace = new MSTraceLocal(global().msTrace, null);;
  protected TraceLocal currentTrace = fullTrace;


  /****************************************************************************
   * Collection
   */

  /**
   * Perform a per-collector collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param primary Perform any single-threaded activities using this thread.
   */
  @Inline
  @Override
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == MS.PREPARE) {
      super.collectionPhase(phaseId, primary);
      fullTrace.prepare();
      return;
    }

    if (phaseId == MS.CLOSURE) {
      fullTrace.completeTrace();
      return;
    }

    if (phaseId == MS.RELEASE) {
      fullTrace.release();
      super.collectionPhase(phaseId, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }


  /****************************************************************************
   * Miscellaneous
   */

  /** @return The active global plan as an <code>MS</code> instance. */
  @Inline
  private static MS global() {
    return (MS) VM.activePlan.global();
  }

  /** @return The current trace instance. */
  @Override
  public final TraceLocal getCurrentTrace() {
    return currentTrace;
  }
}
