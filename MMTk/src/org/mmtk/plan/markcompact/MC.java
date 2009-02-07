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
package org.mmtk.plan.markcompact;

import org.mmtk.plan.*;
import org.mmtk.policy.MarkCompactSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.sanitychecker.SanityChecker;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements the global state of a simple sliding mark-compact
 * collector.
 *
 * FIXME Need algorithmic overview and references.
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities, and divides global and local state
 * into separate class hierarchies.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  There is a single instance of Plan (or the
 * appropriate sub-class), and a 1:1 mapping of PlanLocal to "kernel
 * threads" (aka CPUs).  Thus instance
 * methods of PlanLocal allow fast, unsychronized access to functions such as
 * allocation and collection.
 *
 * The global instance defines and manages static resources
 * (such as memory and virtual memory resources).  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance properties of MMTk plans.
 */
@Uninterruptible public class MC extends StopTheWorld {

  /****************************************************************************
   * Class variables
   */

  public static final MarkCompactSpace mcSpace = new MarkCompactSpace("mc", DEFAULT_POLL_FREQUENCY, VMRequest.create(0.6f));
  public static final int MARK_COMPACT = mcSpace.getDescriptor();

  public static final int SCAN_MARK    = 0;
  public static final int SCAN_FORWARD = 1;

  /* Phases */
  public static final short PREPARE_FORWARD     = Phase.createSimple("fw-prepare");
  public static final short FORWARD_CLOSURE     = Phase.createSimple("fw-closure");
  public static final short RELEASE_FORWARD     = Phase.createSimple("fw-release");

  /* FIXME these two phases need to be made per-collector phases */
  public static final short CALCULATE_FP        = Phase.createSimple("calc-fp");
  public static final short COMPACT             = Phase.createSimple("compact");

  // CHECKSTYLE:OFF

  /**
   * This is the phase that is executed to perform a mark-compact collection.
   *
   * FIXME: Far too much duplication and inside knowledge of StopTheWorld
   */
  public short mcCollection = Phase.createComplex("collection", null,
      Phase.scheduleComplex  (initPhase),
      Phase.scheduleComplex  (rootClosurePhase),
      Phase.scheduleComplex  (refTypeClosurePhase),
      Phase.scheduleComplex  (completeClosurePhase),
      Phase.scheduleMutator  (CALCULATE_FP),
      Phase.scheduleGlobal   (PREPARE_FORWARD),
      Phase.scheduleCollector(PREPARE_FORWARD),
      Phase.scheduleMutator  (PREPARE),
      Phase.scheduleCollector(STACK_ROOTS),
      Phase.scheduleCollector(ROOTS),
      Phase.scheduleGlobal   (ROOTS),
      Phase.scheduleComplex  (forwardPhase),
      Phase.scheduleCollector(FORWARD_CLOSURE),
      Phase.scheduleMutator  (RELEASE),
      Phase.scheduleCollector(RELEASE_FORWARD),
      Phase.scheduleGlobal   (RELEASE_FORWARD),
      Phase.scheduleMutator  (COMPACT),
      Phase.scheduleComplex  (finishPhase));

  // CHECKSTYLE:ON

  /****************************************************************************
   * Instance variables
   */

  public final Trace markTrace;
  public final Trace forwardTrace;

  /**
   * Constructor.
 */
  public MC() {
    markTrace = new Trace(metaDataSpace);
    forwardTrace = new Trace(metaDataSpace);
    collection = mcCollection;
  }

  /*****************************************************************************
   *
   * Collection
   */


  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase to execute.
   */
  @Inline
  public final void collectionPhase(short phaseId) {
    if (phaseId == PREPARE) {
      super.collectionPhase(phaseId);
      markTrace.prepare();
      mcSpace.prepare();
      return;
    }
    if (phaseId == CLOSURE) {
      markTrace.prepare();
      return;
    }
    if (phaseId == RELEASE) {
      markTrace.release();
      mcSpace.release();
      super.collectionPhase(phaseId);
      return;
    }

    if (phaseId == PREPARE_FORWARD) {
      super.collectionPhase(PREPARE);
      forwardTrace.prepare();
      mcSpace.prepare();
      return;
    }
    if (phaseId == RELEASE_FORWARD) {
      forwardTrace.release();
      mcSpace.release();
      super.collectionPhase(RELEASE);
      return;
    }

    super.collectionPhase(phaseId);
  }

  /*****************************************************************************
   *
   * Accounting
   */

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  The superclass accounts for its spaces, we just
   * augment this with the mark-sweep space's contribution.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  public int getPagesUsed() {
    return (mcSpace.reservedPages() + super.getPagesUsed());
  }

  /**
   * Calculate the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   *
   * @return the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   */
  public int getPagesRequired() {
    return super.getPagesRequired() + mcSpace.requiredPages();
  }

  /**
   * @see org.mmtk.plan.Plan#willNeverMove
   *
   * @param object Object in question
   * @return True if the object will never move
   */
  @Override
  public boolean willNeverMove(ObjectReference object) {
    if (Space.isInSpace(MARK_COMPACT, object))
      return false;
    return super.willNeverMove(object);
  }

  /**
   * Return the expected reference count. For non-reference counting
   * collectors this becomes a true/false relationship.
   * @param object The object to check.
   * @param sanityRootRC The number of root references to the object.
   *
   * @return The expected (root excluded) reference count.
   */
  public int sanityExpectedRC(ObjectReference object, int sanityRootRC) {
    Space space = Space.getSpaceForObject(object);

    // Nursery
    if (space == MC.mcSpace) {
      // We are never sure about objects in MC.
      // This is not very satisfying but allows us to use the sanity checker to
      // detect dangling pointers.
      return SanityChecker.UNSURE;
    } else {
      return super.sanityExpectedRC(object, sanityRootRC);
    }
  }

  /**
   * Register specialized methods.
   */
  @Interruptible
  protected void registerSpecializedMethods() {
    TransitiveClosure.registerSpecializedScan(SCAN_MARK, MCMarkTraceLocal.class);
    TransitiveClosure.registerSpecializedScan(SCAN_FORWARD, MCForwardTraceLocal.class);
    super.registerSpecializedMethods();
  }
}
