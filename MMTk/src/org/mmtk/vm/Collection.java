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
   * Spawn a thread to execute the supplied collector context.
   */
  @Interruptible
  public abstract void spawnCollectorContext(CollectorContext context);

  /**
   * @return The default number of collector threads to use.
   */
  public abstract int getDefaultThreads();

  /**
   * @return The number of active threads.
   *
   */
  public abstract int getActiveThreads();

  /**
   * Block for the garbage collector.
   */
  @Unpreemptible
  public abstract void blockForGC();

  /**
   * Prepare a mutator for collection.
   *
   * @param m the mutator to prepare
   */
  public abstract void prepareMutator(MutatorContext m);

  /**
   * Request each mutator flush remembered sets. This method
   * will trigger the flush and then yield until all processors have
   * flushed.
   */
  public abstract void requestMutatorFlush();

  /**
   * Stop all mutator threads. This is current intended to be run by a single thread.
   *
   * Fixpoint until there are no threads that we haven't blocked. Fixpoint is needed to
   * catch the (unlikely) case that a thread spawns another thread while we are waiting.
   */
  @Unpreemptible
  public abstract void stopAllMutators();

  /**
   * Resume all mutators blocked for GC.
   */
  @Unpreemptible
  public abstract void resumeAllMutators();

  /**
   * Fail with an out of memory error.
   */
  public abstract void outOfMemory();
}
