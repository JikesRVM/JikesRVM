/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
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
import org.mmtk.vm.VM;

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
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
@Uninterruptible public abstract class Gen extends StopTheWorld {

  /*****************************************************************************
   * 
   * Constants
   */
  protected static final float SURVIVAL_ESTIMATE = (float) 0.8; // est yield
  protected static final float MATURE_FRACTION = (float) 0.5; // est yield
  public final static boolean IGNORE_REMSETS = false;

  // Allocators
  public static final int ALLOC_NURSERY        = ALLOC_DEFAULT;
  public static final int ALLOC_MATURE         = StopTheWorld.ALLOCATORS + 1;
  public static final int ALLOC_MATURE_MINORGC = StopTheWorld.ALLOCATORS + 2;
  public static final int ALLOC_MATURE_MAJORGC = StopTheWorld.ALLOCATORS + 3;
  public static int ALLOCATORS                 = ALLOC_MATURE_MAJORGC;

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
  public static final CopySpace nurserySpace = new CopySpace("nursery", DEFAULT_POLL_FREQUENCY, (float) 0.15, true, false);

  public static final int NURSERY = nurserySpace.getDescriptor();;
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

  /**
   * Constructor
   */
  public Gen() {
  }

  /*****************************************************************************
   * 
   * Collection
   */

  /**
   * A user-triggered GC has been initiated.
   */
  public void userTriggeredGC() {
    nextGCFullHeap |= Options.fullHeapSystemGC.getValue();
  }
  
  /**
   * Perform a (global) collection phase.
   * 
   * @param phaseId Collection phase to execute.
   */
  public void collectionPhase(int phaseId) throws NoInlinePragma {
    if (phaseId == INITIATE) {
      gcFullHeap = nextGCFullHeap;
      super.collectionPhase(phaseId);
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
        ploSpace.release();
        lastCommittedPLOSpages = ploSpace.committedPages();
      } else {
        super.collectionPhase(phaseId);
        if (gcFullHeap) fullHeapTime.stop();
      }
      nextGCFullHeap = (getPagesAvail() < Options.nurserySize.getMinNursery());
      progress = (getPagesReserved() + required) < getTotalPages();
      return;
    }

    super.collectionPhase(phaseId);
  }

  /**
   * Poll for a collection
   * 
   * @param mustCollect Force a collection.
   * @param space The space that caused the poll.
   * @return True if a collection is required.
   */
  @LogicallyUninterruptible
  public final boolean poll(boolean mustCollect, Space space) { 
    if (getCollectionsInitiated() > 0 || !isInitialized())
      return false;

    if (stressTestGCRequired()) {
      mustCollect = true;
      nextGCFullHeap = true;
    }
    boolean heapFull = getPagesReserved() > getTotalPages();
    boolean nurseryFull = nurserySpace.reservedPages() >
                          Options.nurserySize.getMaxNursery();
    boolean metaDataFull = metaDataSpace.reservedPages() >
                           META_DATA_FULL_THRESHOLD;
    if (mustCollect || heapFull || nurseryFull || metaDataFull) {
      if (space == metaDataSpace) {
        /* In general we must not trigger a GC on metadata allocation since 
         * this is not, in general, in a GC safe point.  Instead we initiate
         * an asynchronous GC, which will occur at the next safe point.
         */
        setAwaitingCollection();
        return false;
      }
      required = space.reservedPages() - space.committedPages();
      if (space == nurserySpace ||
          (copyMature() && (space == activeMatureSpace())))
        required = required << 1; // must account for copy reserve
      int plosNurseryPages = ploSpace.committedPages() - lastCommittedPLOSpages;
      int nurseryYield = (int)(((nurserySpace.committedPages() * 2) + plosNurseryPages) * SURVIVAL_ESTIMATE);
      nextGCFullHeap |= nurseryYield < required;
      VM.collection.triggerCollection(Collection.RESOURCE_GC_TRIGGER);
      return true;
    }
    return false;
  }

 
  /*****************************************************************************
   * 
   * Correctness
   */
  
  /**
   * Remset entries should never be produced by MMTk code.  If the host JVM
   * produces remset entries during GC, it is the responsibility of the host
   * JVM to flush those remset entries out of the mutator contexts.
   */
  public static void assertMutatorRemsetsFlushed() {
    if (VM.VERIFY_ASSERTIONS) {
      GenMutator mutator = null;
      while ((mutator = (GenMutator) VM.activePlan.getNextMutator()) != null)
        mutator.assertRemsetFlushed();
    }
  }

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
  public int getCopyReserve() {
    return nurserySpace.reservedPages() + super.getCopyReserve();
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
  public final boolean isLastGCFull() {
    return gcFullHeap;
  }
}
