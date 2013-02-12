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

  /**
   * {@inheritDoc}
   */
  @Override
  @Unpreemptible
  public void run() {
    while(true) {
      park();
      if (Plan.concurrentWorkers.isMember(this)) {
        concurrentCollect();
      } else {
        collect();
      }
    }
  }

  private static volatile boolean continueCollecting;

  /** Perform some concurrent garbage collection */
  @Unpreemptible
  public final void concurrentCollect() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!Plan.gcInProgress());
    do {
      short phaseId = Phase.getConcurrentPhaseId();
      concurrentCollectionPhase(phaseId);
    } while (continueCollecting);
  }

  @Override
  public void collect() {
    if (!Phase.isPhaseStackEmpty()) {
      Phase.continuePhaseStack();
    } else {
      Phase.beginNewPhaseStack(Phase.scheduleComplex(global().collection));
    }
  }

  @Override
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == Concurrent.FLUSH_COLLECTOR) {
      getCurrentTrace().processRoots();
      getCurrentTrace().flush();
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /**
   * Perform some concurrent collection work.
   *
   * @param phaseId The unique phase identifier
   */
  @Unpreemptible
  public void concurrentCollectionPhase(short phaseId) {
    if (phaseId == Concurrent.CONCURRENT_CLOSURE) {
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(!Plan.gcInProgress());
      }
      TraceLocal trace = getCurrentTrace();
      while(!trace.incrementalTrace(100)) {
        if (group.isAborted()) {
          trace.flush();
          break;
        }
      }
      if (rendezvous() == 0) {
        continueCollecting = false;
        if (!group.isAborted()) {
          /* We are responsible for ensuring termination. */
          if (Options.verbose.getValue() >= 2) Log.writeln("< requesting mutator flush >");
          VM.collection.requestMutatorFlush();

          if (Options.verbose.getValue() >= 2) Log.writeln("< mutators flushed >");

          if (concurrentTraceComplete()) {
            continueCollecting = Phase.notifyConcurrentPhaseComplete();
          } else {
            continueCollecting = true;
            Phase.notifyConcurrentPhaseIncomplete();
          }
        }
      }
      rendezvous();
      return;
    }

    Log.write("Concurrent phase "); Log.write(Phase.getName(phaseId));
    Log.writeln(" not handled.");
    VM.assertions.fail("Concurrent phase not handled!");
  }

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
