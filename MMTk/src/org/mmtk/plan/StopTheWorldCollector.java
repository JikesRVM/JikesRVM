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
 * This class (and its sub-classes) implement <i>per-collector thread</i>
 * behavior and state.
 *
 * MMTk assumes that the VM instantiates instances of CollectorContext
 * in thread local storage (TLS) for each thread participating in
 * collection.  Accesses to this state are therefore assumed to be
 * low-cost during mutator time.<p>
 *
 * @see CollectorContext
 */
@Uninterruptible
public abstract class StopTheWorldCollector extends SimpleCollector {

  /****************************************************************************
   *
   * Collection.
   */

  /** Perform garbage collection */
  public void collect() {
    Phase.beginNewPhaseStack(Phase.scheduleComplex(global().collection));
  }

  /** Perform some concurrent garbage collection */
  @Unpreemptible
  public final void concurrentCollect() {
    VM.assertions.fail("concurrentCollect called on StopTheWorld collector");
  }

  /**
   * Perform some concurrent collection work.
   *
   * @param phaseId The unique phase identifier
   */
  public void concurrentCollectionPhase(short phaseId) {
    VM.assertions.fail("concurrentCollectionPhase triggered on StopTheWorld collector");
  }

  /****************************************************************************
   *
   * Miscellaneous.
   */

  /** @return The active global plan as a <code>StopTheWorld</code> instance. */
  @Inline
  private static StopTheWorld global() {
    return (StopTheWorld) VM.activePlan.global();
  }
}
