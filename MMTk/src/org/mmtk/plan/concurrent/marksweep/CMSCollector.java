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
package org.mmtk.plan.concurrent.marksweep;

import org.mmtk.plan.*;
import org.mmtk.plan.concurrent.ConcurrentCollector;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-collector thread</i> behavior
 * and state for the <i>CMS</i> plan, which implements a full-heap
 * concurrent mark-sweep collector.<p>
 */
@Uninterruptible
public abstract class CMSCollector extends ConcurrentCollector {

  /****************************************************************************
   * Instance fields
   */
  protected final CMSTraceLocal trace;
  protected final MarkSweepLocal ms; // see FIXME at top of this class

  /****************************************************************************
   * Initialization
   */

  /**
   * Constructor
   */
  public CMSCollector() {
    trace = new CMSTraceLocal(global().msTrace);
    ms = new MarkSweepLocal(CMS.msSpace);
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
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == CMS.PREPARE) {
      super.collectionPhase(phaseId, primary);
      ms.prepare();
      trace.prepare();
      return;
    }

    if (phaseId == CMS.START_CLOSURE) {
      trace.processRoots();
      return;
    }

    if (phaseId == CMS.COMPLETE_CLOSURE) {
      trace.completeTrace();
      return;
    }

    if (phaseId == CMS.RELEASE) {
      trace.release();
      super.collectionPhase(phaseId, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /**
   * Return the current trace to be used during concurrent collection.
   */
  protected TraceLocal getConcurrentTrace() {
    return this.trace;
  }

  /**
   * Has all work been completed?
   */
  protected boolean concurrentTraceComplete() {
    return !global().msTrace.hasWork();
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as an <code>MS</code> instance. */
  @Inline
  private static CMS global() {
    return (CMS) VM.activePlan.global();
  }

  /** @return The current trace instance. */
  public final TraceLocal getCurrentTrace() {
    return trace;
  }
}
