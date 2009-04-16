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
package org.mmtk.plan.stickyimmix;

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.plan.immix.Immix;
import org.mmtk.utility.Log;
import org.mmtk.utility.deque.SharedDeque;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.statistics.BooleanCounter;
import org.mmtk.utility.statistics.Stats;
import org.mmtk.vm.Collection;

import org.vmmagic.pragma.*;

/**
 * This class implements the global state of a simple sticky mark bits collector,
 * based on an immix collector.  The sticky mark bits algorithm is
 * due to Demmers et al. (http://doi.acm.org/10.1145/96709.96735), and allows
 * generational collection to be performed in a non-moving heap by overloading
 * the role of mark bits to also indicate whether an object is new (nursery) or
 * not.  Thus nursery objects are identified by a bit in their header, not by
 * where they lie within the address space.  While Demmers et al. did their work
 * in a conservative collector, here we have an exact collector, so we can use
 * a regular write barrier, and don't need to use page protection etc.
 *
 * See the PLDI'08 paper by Blackburn and McKinley for a description
 * of the algorithm: http://doi.acm.org/10.1145/1375581.1375586
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
public class StickyImmix extends Immix {

  /****************************************************************************
   * Constants
   */
  /** If true, then new PLOS objects are collected at each nursery GC */
  static final boolean NURSERY_COLLECT_PLOS = true;
  /** If true then we only do full heap GCs---so we're like MarkSweep (+ write barrier) */
  static final boolean MAJOR_GC_ONLY = false;
  /** estimated collection yield */
  protected static final float SURVIVAL_ESTIMATE = (float) 0.8;

  public static int SCAN_NURSERY = 2;

  /****************************************************************************
   * Class variables
   */
  private static int lastCommittedImmixPages = 0;

  /* statistics */
  public static BooleanCounter fullHeap = new BooleanCounter("majorGC", true, true);

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

    if (phaseId == SET_COLLECTION_KIND) {
      super.collectionPhase(phaseId);
      collectWholeHeap = requiresFullHeapCollection();
      if (Stats.gatheringStats() && collectWholeHeap) fullHeap.set();
      boolean userTriggeredGC = collectionTrigger == Collection.EXTERNAL_GC_TRIGGER && Options.fullHeapSystemGC.getValue();
      immixSpace.setCollectionKind(emergencyCollection, collectWholeHeap, collectionAttempt, requiredAtStart, userTriggeredGC);
      return;
    }

    if (!collectWholeHeap && phaseId == PREPARE) {
      immixTrace.prepare();
      immixSpace.prepare(false);
      return;
    }

    if (phaseId == RELEASE) {
      if (collectWholeHeap) {
        super.collectionPhase(RELEASE);
      } else {
        immixTrace.release();
        immixSpace.release(false);
      }
      modPool.reset();
      lastCommittedImmixPages = immixSpace.committedPages();
      nextGCWholeHeap = (getPagesAvail() < Options.nurserySize.getMinNursery());
      return;
    }

    super.collectionPhase(phaseId);
  }

  /*****************************************************************************
   *
   * Accounting
   */

  /**
   * This method controls the triggering of a GC. It is called periodically
   * during allocation. Returns true to trigger a collection.
   *
   * @param spaceFull Space request failed, must recover pages within 'space'.
   * @return True if a collection is requested by the plan.
   */
  public final boolean collectionRequired(boolean spaceFull) {
    boolean nurseryFull = immixSpace.getPagesAllocated() > Options.nurserySize.getMaxNursery();
    return super.collectionRequired(spaceFull) || nurseryFull;
  }

  /**
   * Determine whether this GC should be a full heap collection.
   *
   * @return True if this GC should be a full heap collection.
   */
  protected boolean requiresFullHeapCollection() {
    if (collectionTrigger == Collection.EXTERNAL_GC_TRIGGER && Options.fullHeapSystemGC.getValue()) {
      return true;
    }
    if (nextGCWholeHeap || collectionAttempt > 1) {
      // Forces full heap collection
      return true;
    }
    if (loSpace.allocationFailed()) {
      // We need space from the nursery
      return true;
    }

    // Estimate the yield from small nursery pages
    int smallNurseryPages = immixSpace.committedPages() - lastCommittedImmixPages;
    int smallNurseryYield = (int)(smallNurseryPages * SURVIVAL_ESTIMATE);

    if (smallNurseryYield < getPagesRequired()) {
      // Our total yield is insufficient.
      return true;
    }

    if (immixSpace.allocationFailed()) {
      if (smallNurseryYield < immixSpace.requiredPages()) {
        // We have run out of VM pages in the nursery
        return true;
      }
    }

    return false;
  }

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
   * Register specialized methods.
   */
  @Interruptible
  protected void registerSpecializedMethods() {
    TransitiveClosure.registerSpecializedScan(SCAN_NURSERY, StickyImmixNurseryTraceLocal.class);
    super.registerSpecializedMethods();
  }
}
