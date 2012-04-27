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
package org.mmtk.plan;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * TODO: Documentation.
 */
@Uninterruptible
public abstract class ParallelCollector extends CollectorContext {

  /****************************************************************************
   * Instance fields
   */

  /** The group that this collector context is running in (may be null) */
  protected ParallelCollectorGroup group;

  /** Last group trigger index (see CollectorContextGroup) */
  int lastTriggerCount;

  /** The index of this thread in the collector context group. */
  int workerOrdinal;

  /****************************************************************************
   * Collection.
   */

  /**
   * {@inheritDoc}
   */
  @Override
  @Unpreemptible
  public void run() {
    while(true) {
      park();
      collect();
    }
  }

  /** Perform a single garbage collection */
  public void collect() {
    VM.assertions.fail("Collector has not implemented collectionPhase");
  }

  /**
   * Perform a (local) collection phase.
   *
   * @param phaseId The unique phase identifier
   * @param primary Should this thread be used to execute any single-threaded
   * local operations?
   */
  public void collectionPhase(short phaseId, boolean primary) {
    VM.assertions.fail("Collector has not implemented collectionPhase");
  }

  /**
   * @return The current trace instance.
   */
  public TraceLocal getCurrentTrace() {
    VM.assertions.fail("Collector has not implemented getCurrentTrace");
    return null;
  }

  /**
   * Park this thread into the group, waiting for a request.
   */
  public final void park() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(this.group != null);
    group.park(this);
  }

  @Override
  public int parallelWorkerCount() {
    return group.activeWorkerCount();
  }

  @Override
  public int parallelWorkerOrdinal() {
    return workerOrdinal;
  }

  @Override
  public int rendezvous() {
    return group.rendezvous();
  }
}
