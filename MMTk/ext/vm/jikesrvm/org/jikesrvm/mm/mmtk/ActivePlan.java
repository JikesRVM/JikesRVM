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
import org.mmtk.utility.options.Options;

import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.Selected;
import org.jikesrvm.scheduler.RVMThread;

import org.vmmagic.pragma.*;

/**
 * This class contains interfaces to access the current plan, plan local and
 * plan constraints instances.
 */
@Uninterruptible public final class ActivePlan extends org.mmtk.vm.ActivePlan {

  private static SynchronizedCounter mutatorCounter = new SynchronizedCounter();

  @Override
  @Inline
  public Plan global() {
    return Selected.Plan.get();
  }

  @Override
  @Inline
  public PlanConstraints constraints() {
    return Selected.Constraints.get();
  }

  @Override
  @Inline
  public int collectorCount() {
    return Options.threads.getValue();
  }

  @Override
  @Inline
  public CollectorContext collector() {
    return RVMThread.getCurrentThread().getCollectorContext();
  }

  @Override
  @Inline
  public boolean isMutator() {
    return !RVMThread.getCurrentThread().isCollectorThread();
  }

  @Override
  @Inline
  public MutatorContext mutator() {
    return Selected.Mutator.get();
  }

  @Override
  public Log log() {
    if (VM.runningVM) {
      return Selected.Mutator.get().getLog();
    } else {
      return buildLog;
    }
  }

  /** Log instance used at build time */
  private static final Log buildLog = new Log();

  @Override
  public void resetMutatorIterator() {
    mutatorCounter.reset();
  }

  @Override
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
