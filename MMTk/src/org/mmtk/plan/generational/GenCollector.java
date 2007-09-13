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
package org.mmtk.plan.generational;

import org.mmtk.plan.*;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.sanitychecker.SanityCheckerLocal;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This abstract class implements <i>per-collector thread</i>
 * behavior and state for <i>generational copying collectors</i>.<p>
 *
 * Specifically, this class defines nursery collection behavior (through
 * <code>nurseryTrace</code> and the <code>collectionPhase</code> method).
 * Per-collector thread remset consumers are instantiated here (used by
 * sub-classes).
 *
 * @see Gen
 * @see GenMutator
 * @see StopTheWorldCollector
 * @see CollectorContext
 */
@Uninterruptible public abstract class GenCollector extends StopTheWorldCollector {

  /*****************************************************************************
   * Instance fields
   */

  protected final GenNurseryTraceLocal nurseryTrace;

  // remembered set consumers
  protected final AddressDeque remset;
  protected final AddressPairDeque arrayRemset;

  // Sanity checking
  private GenSanityCheckerLocal sanityChecker;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * Note that the collector is a consumer of remsets, while the
   * mutator is a producer.  The <code>GenMutator</code> class is
   * responsible for construction of the WriteBuffer (producer).
   * @see GenMutator
   */
  public GenCollector() {
    arrayRemset = new AddressPairDeque(global().arrayRemsetPool);
    remset = new AddressDeque("remset", global().remsetPool);
    nurseryTrace = new GenNurseryTraceLocal(global().nurseryTrace, this);
    sanityChecker = new GenSanityCheckerLocal();
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a per-collector collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param primary Use this thread for single-threaded local activities.
   */
  @NoInline
  public void collectionPhase(short phaseId, boolean primary) {

    if (phaseId == Gen.PREPARE) {
      global().arrayRemsetPool.prepare();
      global().remsetPool.prepare();
      nurseryTrace.prepare();
      return;
    }

    if (phaseId == StopTheWorld.ROOTS) {
      if (!Gen.USE_STATIC_WRITE_BARRIER || global().traceFullHeap()) {
        VM.scanning.computeStaticRoots(getCurrentTrace());
      }
      VM.scanning.computeThreadRoots(getCurrentTrace());
      return;
    }

    if (phaseId == Gen.BOOTIMAGE_ROOTS) {
      if (global().traceFullHeap()) {
        super.collectionPhase(phaseId, primary);
      }
      return;
    }

    if (phaseId == Gen.CLOSURE) {
      if (!global().gcFullHeap) {
        nurseryTrace.completeTrace();
      }
      return;
    }

    if (phaseId == Gen.RELEASE) {
      if (!global().traceFullHeap()) {
        nurseryTrace.release();
        global().arrayRemsetPool.reset();
        global().remsetPool.reset();
      }
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as a <code>Gen</code> instance. */
  @Inline
  private static Gen global() {
    return (Gen) VM.activePlan.global();
  }

  public final TraceLocal getCurrentTrace() {
    if (global().traceFullHeap()) return getFullHeapTrace();
    return nurseryTrace;
  }

  /** @return Return the current sanity checker. */
  public SanityCheckerLocal getSanityChecker() {
    return sanityChecker;
  }

  /** @return The trace to use when collecting the mature space */
  public abstract TraceLocal getFullHeapTrace();

}
