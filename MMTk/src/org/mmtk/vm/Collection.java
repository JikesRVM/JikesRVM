/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package org.mmtk.vm;

import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;

import org.vmmagic.pragma.*;

/**
 *
 *
 */
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
   * Externally triggered garbage collection (eg call to System.gc())
   */
  public static final int EXTERNAL_GC_TRIGGER = 1;

  /**
   * Resource triggered garbage collection.  For example, an
   * allocation request would take the number of pages in use beyond
   * the number available. 
   */
  public static final int RESOURCE_GC_TRIGGER = 2;

  /**
   * Internally triggered garbage collection.  For example, the memory
   * manager attempting another collection after the first failed to
   * free space.
   */
  public static final int INTERNAL_GC_TRIGGER = 3;

  /**
   * The number of garbage collection trigger reasons.
   */
  public static final int TRIGGER_REASONS = 4;

  /** Short descriptions of the garbage collection trigger reasons. */
  private static final String[] triggerReasons = {
    "unknown",
    "external request",
    "resource exhaustion",
    "internal request"
  };


  /**
   * The percentage threshold for throwing an OutOfMemoryError.  If,
   * after a garbage collection, the amount of memory used as a
   * percentage of the available heap memory exceeds this percentage
   * the memory manager will throw an OutOfMemoryError.
   */
  public static final double OUT_OF_MEMORY_THRESHOLD = 0.98;

  /**
   * Triggers a collection.
   * 
   * @param why the reason why a collection was triggered.  0 to
   *          <code>TRIGGER_REASONS - 1</code>.
   */
  @Interruptible
  public abstract void triggerCollection(int why); 

  /**
   * Triggers a collection without allowing for a thread switch. This is needed
   * for Merlin lifetime analysis used by trace generation
   * 
   * @param why the reason why a collection was triggered.  0 to
   *          <code>TRIGGER_REASONS - 1</code>.
   */
  public abstract void triggerCollectionNow(int why);

  /**
   * Trigger an asynchronous collection, checking for memory
   * exhaustion first.
   */
  public abstract void triggerAsyncCollection();

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
}
