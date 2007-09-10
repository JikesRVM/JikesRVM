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
package org.mmtk.plan.generational;

import org.mmtk.plan.*;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.Space;

import org.mmtk.utility.deque.*;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.statistics.*;

import org.mmtk.vm.Collection;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class implements the core functionality of generic
 * two-generationa copying collectors.  Nursery collections occur when
 * either the heap is full or the nursery is full.  The nursery size
 * is determined by an optional command line argument.  If undefined,
 * the nursery size is "infinite", so nursery collections only occur
 * when the heap is full (this is known as a flexible-sized nursery
 * collector).  Thus both fixed and flexible nursery sizes are
 * supported.  Full heap collections occur when the nursery size has
 * dropped to a statically defined threshold,
 * <code>NURSERY_THRESHOLD</code><p>
 *
 * See also Plan.java for general comments on local vs global plan
 * classes.
 */
@Uninterruptible public abstract class Gen extends StopTheWorld {

  /*****************************************************************************
   *
   * Constants
   */
  protected static final float SURVIVAL_ESTIMATE = 0.8f; // est yield
  protected static final float MATURE_FRACTION = 0.5f; // est yield
  public static final boolean IGNORE_REMSETS = false;
  public static final boolean USE_STATIC_WRITE_BARRIER = false;

  // Allocators
  public static final int ALLOC_NURSERY        = ALLOC_DEFAULT;
  public static final int ALLOC_MATURE         = StopTheWorld.ALLOCATORS + 1;
  public static final int ALLOC_MATURE_MINORGC = StopTheWorld.ALLOCATORS + 2;
  public static final int ALLOC_MATURE_MAJORGC = StopTheWorld.ALLOCATORS + 3;

  public static final int SCAN_NURSERY = 0;
  public static final int SCAN_MATURE  = 1;

  /*****************************************************************************
   *
   * Class fields
   */

  /* Statistics */
  protected static BooleanCounter fullHeap = new BooleanCounter("majorGC", true, true);
  private static Timer fullHeapTime = new Timer("majorGCTime", false, true);
  protected static EventCounter wbFast;
  protected static EventCounter wbSlow;
  public static SizeCounter nurseryMark;
  public static SizeCounter nurseryCons;

  /** The nursery space is where all new objects are allocated by default */
  public static final CopySpace nurserySpace = new CopySpace("nursery", DEFAULT_POLL_FREQUENCY, 0.15f, true, false);

  public static final int NURSERY = nurserySpace.getDescriptor();
  public static final Address NURSERY_START = nurserySpace.getStart();
  public static final Address NURSERY_END = NURSERY_START.plus(nurserySpace.getExtent());

  private static int lastCommittedPLOSpages = 0;

  /*****************************************************************************
   *
   * Instance fields
   */
  /* status fields */
  public boolean gcFullHeap = false;
  public boolean nextGCFullHeap = false;

  /* The trace object */
  public final Trace nurseryTrace = new Trace(metaDataSpace);

  /**
   * Remset pools
   */
  public final SharedDeque arrayRemsetPool = new SharedDeque(metaDataSpace, 2);
  public final SharedDeque remsetPool = new SharedDeque(metaDataSpace, 1);

  /*
   * Class initializer
   */
  static {
    if (GATHER_WRITE_BARRIER_STATS) {
      wbFast = new EventCounter("wbFast");
      wbSlow = new EventCounter("wbSlow");
    }
    if (Stats.GATHER_MARK_CONS_STATS) {
      nurseryMark = new SizeCounter("nurseryMark", true, true);
      nurseryCons = new SizeCounter("nurseryCons", true, true);
    }
  }

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * Force the next collection to be full heap.
   */
  public void forceFullHeapCollection() {
    nextGCFullHeap = true;
  }

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase to execute.
   */
  @NoInline
  public void collectionPhase(short phaseId) {
    if (phaseId == SET_COLLECTION_KIND) {
      super.collectionPhase(phaseId);
      gcFullHeap = requiresFullHeapCollection();
      return;
    }

    if (phaseId == PREPARE) {
      nurserySpace.prepare(true);
      if (!traceFullHeap()) {
        nurseryTrace.prepare();
        ploSpace.prepare(false);
      } else {
        if (gcFullHeap) {
          if (Stats.gatheringStats()) fullHeap.set();
          fullHeapTime.start();
        }
        super.collectionPhase(phaseId);

        // we can throw away the remsets for a full heap GC
        remsetPool.clearDeque(1);
        arrayRemsetPool.clearDeque(2);
      }
      return;
    }

    if (phaseId == RELEASE) {
      nurserySpace.release();
      remsetPool.clearDeque(1);
      arrayRemsetPool.clearDeque(2);
      if (!traceFullHeap()) {
        nurseryTrace.release();
        ploSpace.release(false);
      } else {
        super.collectionPhase(phaseId);
        if (gcFullHeap) fullHeapTime.stop();
      }
      lastCommittedPLOSpages = ploSpace.committedPages();
      nextGCFullHeap = (getPagesAvail() < Options.nurserySize.getMinNursery());
      return;
    }

    super.collectionPhase(phaseId);
  }

  /**
   * This method controls the triggering of a GC. It is called periodically
   * during allocation. Returns true to trigger a collection.
   *
   * @param spaceFull Space request failed, must recover pages within 'space'.
   * @return True if a collection is requested by the plan.
   */
  public final boolean collectionRequired(boolean spaceFull) {
    boolean nurseryFull = nurserySpace.reservedPages() > Options.nurserySize.getMaxNursery();

    return super.collectionRequired(spaceFull) || nurseryFull;
  }

  /**
   * Determine if this GC should be a full heap collection.
   *
   * @return True is this GC should be a full heap collection.
   */
  protected boolean requiresFullHeapCollection() {
    if (collectionTrigger == Collection.EXTERNAL_GC_TRIGGER && Options.fullHeapSystemGC.getValue()) {
      return true;
    }

    if (nextGCFullHeap || collectionAttempt > 1) {
      // Forces full heap collection
      return true;
    }

    if (loSpace.allocationFailed()) {
      // We need space from the nursery
      return true;
    }

    // Estimate the yield from nursery PLOS pages
    int plosNurseryPages = ploSpace.committedPages() - lastCommittedPLOSpages;
    int plosYield = (int)(plosNurseryPages * SURVIVAL_ESTIMATE);

    // Estimate the yield from small nursery pages
    int smallNurseryPages = nurserySpace.committedPages();
    int smallNurseryYield = (int)((smallNurseryPages << 1) * SURVIVAL_ESTIMATE);

    if ((plosYield + smallNurseryYield) < getPagesRequired()) {
      // Our total yield is insufficent.
      return true;
    }

    if (nurserySpace.allocationFailed()) {
      if (smallNurseryYield < (nurserySpace.requiredPages() << 1)) {
        // We have run out of VM pages in the nursery
        return true;
      }
    }

    if (ploSpace.allocationFailed()) {
      if (plosYield < ploSpace.requiredPages()) {
        // We have run out of VM pages in the PLOS
        return true;
      }
    }

    return false;
  }


  /*****************************************************************************
   *
   * Correctness
   */

  /*****************************************************************************
   *
   * Accounting
   */

  /**
   * Return the number of pages reserved for copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for copying.
   */
  public int getCollectionReserve() {
    return nurserySpace.reservedPages() + super.getCollectionReserve();
  }

  /**
   * Return the number of pages in use given the pending
   * allocation.  Simply add the nursery's contribution to that of
   * the superclass.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  public int getPagesUsed() {
    return (nurserySpace.reservedPages() + super.getPagesUsed());
  }

  /**
   * Return the number of pages available for allocation, <i>assuming
   * all future allocation is to the nursery</i>.
   *
   * @return The number of pages available for allocation, <i>assuming
   * all future allocation is to the nursery</i>.
   */
  public int getPagesAvail() {
    return super.getPagesAvail() >> 1;
  }

  /**
   * Calculate the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   *
   * @return the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   */
  public int getPagesRequired() {
    /* We don't currently pretenure, so mature space must be zero */
    return super.getPagesRequired() + (nurserySpace.requiredPages() << 1);
  }

  /*****************************************************************************
   *
   * Miscellaneous
   */

  /**
   * @return Does the mature space do copying ?
   */
  protected boolean copyMature() {
    return false;
  }

  /**
   * Print pre-collection statistics. In this class we prefix the output
   * indicating whether the collection was full heap or not.
   */
  public void printPreStats() {
    if ((Options.verbose.getValue() >= 1) && (gcFullHeap))
      Log.write("[Full heap]");
    super.printPreStats();
  }

  /**
   * @return The mature space, set by each subclass of <code>Gen</code>.
   */
  protected abstract Space activeMatureSpace();

  /**
   * @return True if we should trace the whole heap during collection. True if
   *         we're ignorning remsets or if we're doing a full heap GC.
   */
  public final boolean traceFullHeap() {
    return IGNORE_REMSETS || gcFullHeap;
  }

  /**
   * @return Is current GC only collecting objects allocated since last GC.
   */
  public final boolean isCurrentGCNursery() {
    return !gcFullHeap;
  }

  /**
   * @return Is last GC a full collection?
   */
  public final boolean lastCollectionFullHeap() {
    return gcFullHeap;
  }

  /**
   * @see org.mmtk.plan.Plan#willNeverMove
   *
   * @param object Object in question
   * @return True if the object will never move
   */
  @Override
  public boolean willNeverMove(ObjectReference object) {
    if (Space.isInSpace(NURSERY, object))
      return false;
    return super.willNeverMove(object);
  }
}
