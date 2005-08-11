/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package org.mmtk.vm;

import org.mmtk.plan.PlanLocal;

/**
 * $Id$ 
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 *
 * @version $Revision$
 * @date $Date$
 */
public class Collection {

  /****************************************************************************
   *
   * Class variables
   */

  /** An unknown GC trigger reason.  Signals a logic bug. */ 
  public static final int UNKNOWN_GC_TRIGGER = 0;  
  /** Externally triggered garbage collection (eg call to System.gc())  */
  public static final int EXTERNAL_GC_TRIGGER = 1;
  /** Resource triggered garbage collection.  For example, an
      allocation request would take the number of pages in use beyond
      the number available. */
  public static final int RESOURCE_GC_TRIGGER = 2;
  /**
   * Internally triggered garbage collection.  For example, the memory
   * manager attempting another collection after the first failed to
   * free space.
   */
  public static final int INTERNAL_GC_TRIGGER = 3;
  /** The number of garbage collection trigger reasons. */
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

  /***********************************************************************
   *
   * Initialization
   */

  /**
   * Initialization that occurs at <i>build</i> time.  The values of
   * statics at the completion of this routine will be reflected in
   * the boot image.  Any objects referenced by those statics will be
   * transitively included in the boot image.
   *
   * This is called from MM_Interface.
   */
  public static final void init() {
  }

  /**
   * An enumerator used to forward root objects
   */
  /**
   * Triggers a collection.
   *
   * @param why the reason why a collection was triggered.  0 to
   * <code>TRIGGER_REASONS - 1</code>.
   */
  public static final void triggerCollection(int why) {
  }

  /**
   * Triggers a collection without allowing for a thread switch.  This is needed
   * for Merlin lifetime analysis used by trace generation 
   *
   * @param why the reason why a collection was triggered.  0 to
   * <code>TRIGGER_REASONS - 1</code>.
   */
  public static final void triggerCollectionNow(int why) {
  }

  /**
   * Trigger an asynchronous collection, checking for memory
   * exhaustion first.
   */
  public static final void triggerAsyncCollection()
    {
  }

  /**
   * Determine whether a collection cycle has fully completed (this is
   * used to ensure a GC is not in the process of completing, to
   * avoid, for example, an async GC being triggered on the switch
   * from GC to mutator thread before all GC threads have switched.
   *
   * @return True if GC is not in progress.
   */
 public static final boolean noThreadsInGC() {
   return false; 
 }

  /**
   * Check for memory exhaustion, possibly throwing an out of memory
   * exception and/or triggering another GC.
   *
   * @param why Why the collection was triggered
   * @param async True if this collection was asynchronously triggered.
   */
  private static final void checkForExhaustion(int why, boolean async)
    {
    
  }

  /**
   * Checks whether a plan instance is eligible to participate in a
   * collection.
   *
   * @param plan the plan to check
   * @return <code>true</code> if the plan is not participating,
   * <code>false</code> otherwise
   */
  public static boolean isNonParticipating(PlanLocal plan) {
    return false;  
  }

  /**
   * Prepare a plan that is not participating in a collection.
   *
   * @param p the plan to prepare
   */
  public static void prepareNonParticipating(PlanLocal p) {
    /*
     * The collector threads of processors currently running threads
     * off in JNI-land cannot run.
     */
  }

  /**
   * Set a collector thread's so that a scan of its stack
   * will start at VM_CollectorThread.run
   *
   * @param p the plan to prepare
   */
  public static void prepareParticipating (PlanLocal p) {
  }

  /**
   * Rendezvous with all other processors, returning the rank
   * (that is, the order this processor arrived at the barrier).
   */
  public static int rendezvous(int where) {
    return 0;
  }

  /***********************************************************************
   *
   * Finalizers
   */
  
  /**
   * Schedule the finalizerThread, if there are objects to be
   * finalized and the finalizerThread is on its queue (ie. currently
   * idle).  Should be called at the end of GC after moveToFinalizable
   * has been called, and before mutators are allowed to run.
   */
  public static void scheduleFinalizerThread () {
  }
}
