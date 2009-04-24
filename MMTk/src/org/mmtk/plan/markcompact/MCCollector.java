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

import org.mmtk.plan.*;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-collector thread</i> behavior
 * and state for the <i>MC</i> plan, which implements a full-heap
 * mark-compact collector.<p>
 *
 * Specifically, this class defines <i>MC</i> collection behavior
 * (through <code>trace</code> and the <code>collectionPhase</code>
 * method), and collection-time allocation.<p>
 *
 * @see MC for an overview of the mark-compact algorithm.<p>
 *
 * FIXME Currently MC does not properly separate mutator and collector
 * behaviors, so some of the collection logic in MCMutator should
 * really be per-collector thread, not per-mutator thread.
 *
 * @see MC
 * @see MCMutator
 * @see StopTheWorldCollector
 * @see CollectorContext
 */
@Uninterruptible public class MCCollector extends StopTheWorldCollector {

  private static final boolean TRACE_MARK = false;
  private static final boolean TRACE_FORWARD = true;

  /****************************************************************************
   * Instance fields
   */

  private final MCMarkTraceLocal markTrace;
  private final MCForwardTraceLocal forwardTrace;
  private boolean currentTrace;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public MCCollector() {
    markTrace = new MCMarkTraceLocal(global().markTrace);
    forwardTrace = new MCForwardTraceLocal(global().forwardTrace);
  }


  /****************************************************************************
   *
   * Collection-time allocation
   */

  /**
   * Allocate space for copying an object (this method <i>does not</i>
   * copy the object, it only allocates space)
   *
   * @param original A reference to the original object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @return The address of the first byte of the allocated region
   */
  @Inline
  public Address allocCopy(ObjectReference original, int bytes,
      int align, int offset, int allocator) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(allocator == MC.ALLOC_IMMORTAL);

    return immortal.alloc(bytes, align, offset);
  }

  /**
   * Perform any post-copy actions.
   *
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  @Inline
  public void postCopy(ObjectReference object, ObjectReference typeRef,
      int bytes, int allocator) {
    MC.immortalSpace.initializeHeader(object);
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
  public final void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == MC.PREPARE) {
      currentTrace = TRACE_MARK;
      super.collectionPhase(phaseId, primary);
      markTrace.prepare();
      return;
    }

    if (phaseId == MC.CLOSURE) {
      markTrace.completeTrace();
      return;
    }

    if (phaseId == MC.RELEASE) {
      markTrace.release();
      super.collectionPhase(phaseId, primary);
      return;
    }

    if (phaseId == MC.PREPARE_FORWARD) {
      currentTrace = TRACE_FORWARD;
      super.collectionPhase(MC.PREPARE, primary);
      forwardTrace.prepare();
      return;
    }

    if (phaseId == MC.FORWARD_CLOSURE) {
      forwardTrace.completeTrace();
      return;
    }

    if (phaseId == MC.RELEASE_FORWARD) {
      forwardTrace.release();
      super.collectionPhase(MC.RELEASE, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The current trace instance. */
  public final TraceLocal getCurrentTrace() {
    if (currentTrace == TRACE_MARK) {
      return markTrace;
    } else {
      return forwardTrace;
    }
  }

  /** @return The active global plan as an <code>MC</code> instance. */
  @Inline
  private static MC global() {
    return (MC) VM.activePlan.global();
  }
}
