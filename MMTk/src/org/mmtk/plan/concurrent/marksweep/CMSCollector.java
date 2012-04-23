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
package org.mmtk.plan.concurrent.marksweep;

import org.mmtk.plan.*;
import org.mmtk.plan.concurrent.ConcurrentCollector;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-collector thread</i> behavior
 * and state for the <i>CMS</i> plan, which implements a full-heap
 * concurrent mark-sweep collector.<p>
 */
@Uninterruptible
public class CMSCollector extends ConcurrentCollector {

  /****************************************************************************
   * Instance fields
   */
  protected final CMSTraceLocal trace;

  /****************************************************************************
   * Initialization
   */

  /**
   * Constructor
   */
  public CMSCollector() {
    trace = new CMSTraceLocal(global().msTrace);
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
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == CMS.PREPARE) {
      super.collectionPhase(phaseId, primary);
      trace.prepare();
      return;
    }

    if (phaseId == CMS.CLOSURE) {
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
   * Has all work been completed?
   */
  @Override
  protected boolean concurrentTraceComplete() {
    if (!global().msTrace.hasWork()) {
      return true;
    }
    return false;
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
  @Override
  public final TraceLocal getCurrentTrace() {
    return trace;
  }
}
