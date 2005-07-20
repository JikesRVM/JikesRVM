/*
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
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public abstract class Gen extends StopTheWorld implements Uninterruptible {

  /*****************************************************************************
   *
   * Constants
   */
  protected static final float SURVIVAL_ESTIMATE = (float) 0.8; // est yield
  protected static final float MATURE_FRACTION = (float) 0.5; // est yield
  public final static boolean IGNORE_REMSETS = false;

  // Allocators
  public static final int ALLOC_NURSERY = ALLOC_DEFAULT;
  public static final int ALLOC_MATURE = StopTheWorld.ALLOCATORS + 1;
  public static int ALLOCATORS = ALLOC_MATURE;

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


  /*****************************************************************************
   *
   * Instance fields
   */
  /* status fields */
  public boolean gcFullHeap = false;
  public boolean lastGCFullHeap = false;

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
    gcFullHeap |= Options.fullHeapSystemGC.getValue();
  }
  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase to execute.
   */
  public void collectionPhase(int phaseId) throws NoInlinePragma {
    if (phaseId == PREPARE) {
      nurserySpace.prepare(true);
      lastGCFullHeap = gcFullHeap;
      if (collectMatureSpace()) {
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
      if (collectMatureSpace()) {
        super.collectionPhase(phaseId);
        if (gcFullHeap) fullHeapTime.stop();
      }
      gcFullHeap = (getPagesAvail() < Options.nurserySize.getMinNursery());
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
  public final boolean poll(boolean mustCollect, Space space)
    throws LogicallyUninterruptiblePragma {
    if (getCollectionsInitiated() > 0 || !isInitialized() || 
        space == metaDataSpace)
      return false;

    mustCollect |= stressTestGCRequired();
    boolean heapFull = getPagesReserved() > getTotalPages();
    boolean nurseryFull = nurserySpace.reservedPages() >
                          Options.nurserySize.getMaxNursery();
    boolean metaDataFull = metaDataSpace.reservedPages() >
                           META_DATA_FULL_THRESHOLD;
    if (mustCollect || heapFull || nurseryFull || metaDataFull) {
      required = space.reservedPages() - space.committedPages();
      if (space == nurserySpace ||
          (copyMature() && (space == activeMatureSpace())))
        required = required<<1;  // must account for copy reserve
      int nurseryYield = ((int)(nurserySpace.committedPages() *
                          SURVIVAL_ESTIMATE))<<1;
      gcFullHeap |= mustCollect || (nurseryYield < required);
      Collection.triggerCollection(Collection.RESOURCE_GC_TRIGGER);
      return true;
    }
    return false;
  }
  //private static int counter = 0;

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
   * Print pre-collection statistics.  In this class we prefix the output
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
   * @return True if the last GC was a full heap GC.
   */
  public boolean isLastGCFull () {
    return lastGCFullHeap;
  }

  /**
   * @return True if we should trace the mature space during collection.
   */
  public final boolean collectMatureSpace() {
    return IGNORE_REMSETS || gcFullHeap;
  }

  /**
   * @return Is current GC only collecting objects allocated since last GC.
   */
  public final boolean isCurrentGCNursery() {
    return !gcFullHeap;
  }
}
