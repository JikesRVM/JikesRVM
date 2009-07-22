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
package org.mmtk.harness.vm;

import org.mmtk.harness.Collector;
import org.mmtk.harness.Harness;
import org.mmtk.harness.Mutator;
import org.mmtk.harness.Mutators;
import org.mmtk.harness.exception.OutOfMemory;
import org.mmtk.harness.scheduler.Scheduler;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.Plan;
import org.mmtk.utility.options.Options;

import org.vmmagic.pragma.*;

@Uninterruptible
public class Collection extends org.mmtk.vm.Collection {

  /****************************************************************************
   *
   * Class variables
   */

  /**
   * Triggers a collection.
   *
   * @param why the reason why a collection was triggered.  0 to
   *          <code>TRIGGER_REASONS - 1</code>.
   */
  @Override
  public void triggerCollection(int why) {
    if (Options.verbose.getValue() >= 4) {
      new Exception("Collection trigger: " + triggerReasons[why]).printStackTrace();
    }

    Plan.setCollectionTriggered();

    if (Options.verbose.getValue() >= 1) {
      if (why == EXTERNAL_GC_TRIGGER) {
        System.err.print("[Forced GC]");
      } else if (why == INTERNAL_PHASE_GC_TRIGGER) {
        System.err.print("[Phase GC]");
      }
    }

    Mutator mutator = Mutator.current();
    if (why != EXTERNAL_GC_TRIGGER && why != INTERNAL_PHASE_GC_TRIGGER) {
      mutator.reportCollectionAttempt();
    }

    if (mutator.isOutOfMemory()) throw new OutOfMemory();

    Collector.triggerGC(why);
    Scheduler.waitForGC();

    if (mutator.isOutOfMemory() && !mutator.isPhysicalAllocationFailure()) {
      throw new OutOfMemory();
    }
  }

  /**
   * Joins an already requested collection.
   */
  @Override
  public void joinCollection() {
    while (Plan.isCollectionTriggered()) {
      /* allow a gc thread to run */
      Mutator.current().gcSafePoint();
    }
    Mutator mutator = Mutator.current();
    if (mutator.isOutOfMemory() && !mutator.isPhysicalAllocationFailure()) {
      throw new OutOfMemory();
    }
  }

  /**
   * Trigger an asynchronous collection, checking for memory
   * exhaustion first.
   *
   * @param why the reason why a collection was triggered.  0 to
   *          <code>TRIGGER_REASONS - 1</code>.
   */
  @Override
  public void triggerAsyncCollection(int why) {
    Plan.setCollectionTriggered();
    if (Options.verbose.getValue() >= 1) {
      if (why == INTERNAL_PHASE_GC_TRIGGER) {
        System.err.print("[Async-Phase GC]");
      } else {
        System.err.print("[Async GC]");
      }
    }

    Collector.triggerGC(why);
  }

  /**
   * @return The maximum number of collection attempts across threads.
   */
  @Override
  public int maximumCollectionAttempt() {
      int max = 1;
      for(Mutator m : Mutators.getAll()) {
        int current = m.getCollectionAttempts();
        if (current > max) max = current;
      }
      return max + Collector.getCollectionAttemptBase();
  }

  /**
   * Report that the allocation has succeeded.
   */
  @Override
  public void reportAllocationSuccess() {
    Mutator mutator = Mutator.current();
    mutator.setOutOfMemory(false);
    mutator.clearCollectionAttempts();
    mutator.setPhysicalAllocationFailure(false);
  }

  /**
   * Report that a physical allocation has failed.
   */
  @Override
  public void reportPhysicalAllocationFailed() {
    Mutator.current().setPhysicalAllocationFailure(true);
  }

  /**
   * Does the VM consider this an emergency alloction, where the normal
   * heap size rules can be ignored.
   */
  @Override
  public boolean isEmergencyAllocation() {
    // Not required
    return false;
  }

  /**
   * Determine whether a collection cycle has fully completed (this is
   * used to ensure a GC is not in the process of completing, to
   * avoid, for example, an async GC being triggered on the switch
   * from GC to mutator thread before all GC threads have switched.
   *
   * @return True if GC is not in progress.
   */
  @Override
  public boolean noThreadsInGC() {
    return Scheduler.noThreadsInGC();
  }

  /**
   * Prepare a mutator for collection.
   *
   * @param m the mutator to prepare
   */
  @Override
  public void prepareMutator(MutatorContext m) {
    // Nothing to do
  }

  /**
   * Prepare a collector for a collection.
   *
   * @param c the collector to prepare
   */
  @Override
  public void prepareCollector(CollectorContext c) {
    // Nothing to do
  }

  /**
   * Rendezvous with all other processors, returning the rank
   * (that is, the order this processor arrived at the barrier).
   */
  @Override
  public int rendezvous(int where) {
    return Collector.rendezvous(where);
  }

  /** @return The number of active collector threads */
  @Override
  public int activeGCThreads() {
    return Harness.collectors.getValue();
  }

  /**
   * @return The ordinal ID of the running collector thread w.r.t.
   * the set of active collector threads (zero based)
   */
  @Override
  public int activeGCThreadOrdinal() {
    return Collector.current().getContext().getId();
  }

  /**
   * Ensure all concurrent worker threads are scheduled.
   */
  public void scheduleConcurrentWorkers() {
    Assert.notImplemented();
  }

  /**
   * Request each mutator flush remembered sets. This method
   * will trigger the flush and then yield until all processors have
   * flushed.
   */
  @Override
  public void requestMutatorFlush() {
    Assert.notImplemented();
  }

  /**
   * Possibly yield the current concurrent collector thread. Return
   * true if yielded.
   */
  public boolean yieldpoint() {
    return Mutator.current().gcSafePoint();
  }
}
