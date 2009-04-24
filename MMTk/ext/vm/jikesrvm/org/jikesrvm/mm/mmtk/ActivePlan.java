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
package org.jikesrvm.mm.mmtk;

import org.mmtk.plan.Plan;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.PlanConstraints;
import org.mmtk.utility.Log;

import org.jikesrvm.mm.mminterface.Selected;
import org.jikesrvm.scheduler.RVMThread;

import org.vmmagic.pragma.*;

/**
 * This class contains interfaces to access the current plan, plan local and
 * plan constraints instances.
 */
@Uninterruptible public final class ActivePlan extends org.mmtk.vm.ActivePlan {

  private static SynchronizedCounter mutatorCounter = new SynchronizedCounter();

  /** @return The active Plan instance. */
  @Inline
  public Plan global() {
    return Selected.Plan.get();
  }

  /** @return The active PlanConstraints instance. */
  @Inline
  public PlanConstraints constraints() {
    return Selected.Constraints.get();
  }

  /** @return The number of registered CollectorContext instances. */
  @Inline
  public int collectorCount() {
    return RVMThread.numProcessors;
  }

  /** @return The active CollectorContext instance. */
  @Inline
  public CollectorContext collector() {
    return Selected.Collector.get();
  }

  /** @return The active MutatorContext instance. */
  @Inline
  public MutatorContext mutator() {
    return Selected.Mutator.get();
  }

  /** @return The log for the active thread */
  public Log log() {
    return Selected.Mutator.get().getLog();
  }

  /** Reset the mutator iterator */
  public void resetMutatorIterator() {
    mutatorCounter.reset();
  }

  /**
   * Return the next <code>MutatorContext</code> in a
   * synchronized iteration of all mutators.
   *
   * @return The next <code>MutatorContext</code> in a
   *  synchronized iteration of all mutators, or
   *  <code>null</code> when all mutators have been done.
   */
  public MutatorContext getNextMutator() {
    for (;;) {
      int idx = mutatorCounter.increment();
      if (idx >= RVMThread.numThreads) {
        return null;
      } else {
        RVMThread t=RVMThread.threads[idx];
        if (t.activeMutatorContext) {
          return t;
        }
      }
    }
  }
}
