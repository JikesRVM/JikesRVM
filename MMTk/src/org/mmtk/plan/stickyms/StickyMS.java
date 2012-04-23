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
package org.mmtk.plan.stickyms;

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.plan.marksweep.MS;
import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.utility.deque.SharedDeque;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.sanitychecker.SanityChecker;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements the global state of a simple sticky mark bits collector,
 * based a simple on mark-sweep collector.  The sticky mark bits algorithm is
 * due to Demmers et al. (http://doi.acm.org/10.1145/96709.96735), and allows
 * generational collection to be performed in a non-moving heap by overloading
 * the role of mark bits to also indicate whether an object is new (nursery) or
 * not.  Thus nursery objects are identified by a bit in their header, not by
 * where they lie within the address space.  While Demmers et al. did their work
 * in a conservative collector, here we have an exact collector, so we can use
 * a regular write barrier, and don't need to use page protection etc.
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities, and divides global and local state
 * into separate class hierarchies.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  There is a single instance of Plan (or the
 * appropriate sub-class), and a 1:1 mapping of PlanLocal to "kernel
 * threads" (aka CPUs or in Jikes RVM, Processors).  Thus instance
 * methods of PlanLocal allow fast, unsychronized access to functions such as
 * allocation and collection.
 *
 * The global instance defines and manages static resources
 * (such as memory and virtual memory resources).  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance properties of MMTk plans.
 */
@Uninterruptible
public class StickyMS extends MS {

  /****************************************************************************
   * Constants
   */
  /** If true, then new PLOS objects are collected at each nursery GC */
  static final boolean NURSERY_COLLECT_PLOS = true;
  /** If true then we only do full heap GCs---so we're like MarkSweep (+ write barrier) */
  static final boolean MAJOR_GC_ONLY = false;

  /****************************************************************************
   * Class variables
   */

  public static int SCAN_NURSERY = 1;

  /****************************************************************************
   * Instance variables
   */
  /* status fields */
  /** will the next collection collect the whole heap? */
  public boolean nextGCWholeHeap = false;
  /** will this collection collect the whole heap */
  public boolean collectWholeHeap = nextGCWholeHeap;

  /* Remset pool */
  public final SharedDeque modPool = new SharedDeque("msgen mod objects", metaDataSpace, 1);

  /****************************************************************************
   * Static initialization
   */
  {
    msSpace.makeAgeSegregatedSpace();  /* this space is to be collected generationally */
  }

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * A user-triggered GC has been initiated.
   */
  public void userTriggeredGC() {
    nextGCWholeHeap |= Options.fullHeapSystemGC.getValue();
  }

  /**
   * Force the next collection to be full heap.
   */
  @Override
  public void forceFullHeapCollection() {
    nextGCWholeHeap = true;
  }

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase to execute.
   */
  @Inline
  @Override
  public final void collectionPhase(short phaseId) {

    if (phaseId == INITIATE) {
      collectWholeHeap = MAJOR_GC_ONLY || emergencyCollection || nextGCWholeHeap;
      nextGCWholeHeap = false;
      super.collectionPhase(phaseId);
      return;
    }

    if (!collectWholeHeap) {
      if (phaseId == PREPARE) {
        msTrace.prepare();
        msSpace.prepare(false);
        return;
      }

      if (phaseId == RELEASE) {
        msTrace.release();
        msSpace.release();
        modPool.reset();
        nextGCWholeHeap = (getPagesAvail() < Options.nurserySize.getMinNursery());
        return;
      }
    }

    super.collectionPhase(phaseId);
  }

  /*****************************************************************************
   *
   * Accounting
   */

  /**
   * Print pre-collection statistics. In this class we prefix the output
   * indicating whether the collection was full heap or not.
   */
  @Override
  public void printPreStats() {
    if ((Options.verbose.getValue() >= 1) && (collectWholeHeap))
      Log.write("[Full heap]");
    super.printPreStats();
  }

  /**
   * @return Is current GC only collecting objects allocated since last GC.
   */
  @Override
  public final boolean isCurrentGCNursery() {
    return !collectWholeHeap;
  }

  /**
   * @return Is last GC a full collection?
   */
  public final boolean isLastGCFull() {
    return collectWholeHeap;
  }

  /**
   * Return the expected reference count. For non-reference counting
   * collectors this becomes a true/false relationship.
   * @param object The object to check.
   * @param sanityRootRC The number of root references to the object.
   *
   * @return The expected (root excluded) reference count.
   */
  @Override
  public int sanityExpectedRC(ObjectReference object, int sanityRootRC) {
    Space space = Space.getSpaceForObject(object);

    // Immortal spaces
    if (space == StickyMS.immortalSpace || space == StickyMS.vmSpace) {
      return space.isReachable(object) ? SanityChecker.ALIVE : SanityChecker.DEAD;
    }

    // Mature space (nursery collection)
    if (VM.activePlan.global().isCurrentGCNursery() && space != StickyMS.msSpace) {
      return SanityChecker.UNSURE;
    }

    return super.sanityExpectedRC(object, sanityRootRC);
  }

  /**
   * Register specialized methods.
   */
  @Override
  @Interruptible
  protected void registerSpecializedMethods() {
    TransitiveClosure.registerSpecializedScan(SCAN_NURSERY, StickyMSNurseryTraceLocal.class);
    super.registerSpecializedMethods();
  }
}
