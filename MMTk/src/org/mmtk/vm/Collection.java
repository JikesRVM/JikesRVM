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
package org.mmtk.vm;

import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;

import org.vmmagic.pragma.*;

@Uninterruptible public abstract class Collection {

  /****************************************************************************
   *
   * Class variables
   */

  /**
   * An unknown GC trigger reason. Signals a logic bug.
   */
  public static final int UNKNOWN_GC_TRIGGER = 0;

  /**
   * Concurrent collection phase trigger.
   */
  public static final int INTERNAL_PHASE_GC_TRIGGER = 1;

  /**
   * Externally triggered garbage collection (eg call to System.gc())
   */
  public static final int EXTERNAL_GC_TRIGGER = 2;

  /**
   * Resource triggered garbage collection.  For example, an
   * allocation request would take the number of pages in use beyond
   * the number available.
   */
  public static final int RESOURCE_GC_TRIGGER = 3;

  /**
   * Internally triggered garbage collection.  For example, the memory
   * manager attempting another collection after the first failed to
   * free space.
   */
  public static final int INTERNAL_GC_TRIGGER = 4;

  /**
   * The number of garbage collection trigger reasons.
   */
  public static final int TRIGGER_REASONS = 5;

  /** Short descriptions of the garbage collection trigger reasons. */
  protected static final String[] triggerReasons = {
    "unknown",
    "concurrent phase",
    "external request",
    "resource exhaustion",
    "internal request"
  };

  /**
   * Triggers a collection.
   *
   * @param why the reason why a collection was triggered.  0 to
   *          <code>TRIGGER_REASONS - 1</code>.
   */
  @Unpreemptible
  public abstract void triggerCollection(int why);

  /**
   * Joins an already requested collection.
   */
  @Unpreemptible
  public abstract void joinCollection();

  /**
   * Trigger an asynchronous collection, checking for memory
   * exhaustion first.
   *
   * @param why the reason why a collection was triggered.  0 to
   *          <code>TRIGGER_REASONS - 1</code>.
   */
  public abstract void triggerAsyncCollection(int why);

  /**
   * The maximum number collection attempts across threads.
   */
  public abstract int maximumCollectionAttempt();

  /**
   * Report that the allocation has succeeded.
   */
  public abstract void reportAllocationSuccess();

  /**
   * Report that a physical allocation has failed.
   */
  public abstract void reportPhysicalAllocationFailed();

  /**
   * Does the VM consider this an emergency alloction, where the normal
   * heap size rules can be ignored.
   */
  public abstract boolean isEmergencyAllocation();

  /**
   * Determine whether a collection cycle has fully completed (this is
   * used to ensure a GC is not in the process of completing, to
   * avoid, for example, an async GC being triggered on the switch
   * from GC to mutator thread before all GC threads have switched.
   *
   * @return True if GC is not in progress.
   */
  public abstract boolean noThreadsInGC();

  /**
   * Prepare a mutator for collection.
   *
   * @param m the mutator to prepare
   */
  public abstract void prepareMutator(MutatorContext m);

  /**
   * Prepare a collector for a collection.
   *
   * @param c the collector to prepare
   */
  public abstract void prepareCollector(CollectorContext c);

  /**
   * Rendezvous with all other processors, returning the rank
   * (that is, the order this processor arrived at the barrier).
   */
  public abstract int rendezvous(int where);

  /** @return The number of active collector threads */
  public abstract int activeGCThreads();

  /**
   * @return The ordinal ID of the running collector thread w.r.t.
   * the set of active collector threads (zero based)
   */
  public abstract int activeGCThreadOrdinal();

  /**
   * Request each mutator flush remembered sets. This method
   * will trigger the flush and then yield until all processors have
   * flushed.
   */
  public abstract void requestMutatorFlush();
}
