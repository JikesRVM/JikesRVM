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
package org.mmtk.plan.concurrent;

import org.mmtk.plan.Phase;
import org.mmtk.plan.Plan;
import org.mmtk.plan.SimpleCollector;
import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-collector thread</i> behavior
 * and state for a concurrent collector.
 */
@Uninterruptible
public abstract class ConcurrentCollector extends SimpleCollector {

  /****************************************************************************
   * Instance fields
   */

  /****************************************************************************
   * Initialization
   */

  /****************************************************************************
   *
   * Collection
   */

  /** Perform some concurrent garbage collection */
  public final void concurrentCollect() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!Plan.gcInProgress());
    if (Phase.startConcurrentPhase()) {
      /* Can't change while we are 'in' the concurrent phase */
      short phaseId = Phase.getConcurrentPhaseId();
      concurrentCollectionPhase(phaseId);
    }
  }

  public void collect() {
    if (!Phase.isPhaseStackEmpty()) {
      Phase.continuePhaseStack();
    } else {
      Phase.beginNewPhaseStack(Phase.scheduleComplex(global().collection));
    }
  }

  /**
   * Perform a per-collector collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param primary Perform any single-threaded activities using this thread.
   */
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == Concurrent.FLUSH_COLLECTOR) {
      getConcurrentTrace().flush();
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /**
   * Perform some concurrent collection work.
   *
   * @param phaseId The unique phase identifier
   */
  public void concurrentCollectionPhase(short phaseId) {
    if (phaseId == Concurrent.CONCURRENT_COMPLETE_CLOSURE) {
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(!Plan.gcInProgress());
        for(int i=0; i < VM.activePlan.mutatorCount(); i++) {
          VM.assertions._assert(((ConcurrentMutator)VM.activePlan.mutator(i)).barrierActive);
        }
      }
      TraceLocal trace = getConcurrentTrace();
      while(!trace.traceIncrement(100)) {
        /* Check if we should yield */
        if (VM.collection.yieldpoint()) {
          if (resetConcurrentWork) {
            /* We have been preempted by a full collection */
            return;
          }
        }
      }

      if (Phase.completeConcurrentPhase()) {
        /* We are responsible for ensuring termination. */
        if (Options.verbose.getValue() >= 2) Log.writeln("< requesting mutator flush >");
        VM.collection.requestMutatorFlush();

        if (resetConcurrentWork) {
          /* We have been preempted by a full collection */
          return;
        }

        if (Options.verbose.getValue() >= 2) Log.writeln("< mutators flushed >");

        if (concurrentTraceComplete()) {
          Phase.notifyConcurrentPhaseComplete();
        } else {
          Phase.notifyConcurrentPhaseIncomplete();
        }
      }
      return;
    }

    Log.write("Concurrent phase "); Log.write(Phase.getName(phaseId));
    Log.writeln(" not handled.");
    VM.assertions.fail("Concurrent phase not handled!");
  }

  /**
   * Return the current trace to be used during concurrent collection.
   */
  protected abstract TraceLocal getConcurrentTrace();

  /**
   * Has all work been completed?
   */
  protected abstract boolean concurrentTraceComplete();


  /****************************************************************************
   *
   * Miscellaneous.
   */

  /** @return The active global plan as a <code>Concurrent</code> instance. */
  @Inline
  private static Concurrent global() {
    return (Concurrent) VM.activePlan.global();
  }
}
