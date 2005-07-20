/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.copyms;

import org.mmtk.plan.*;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.MarkSweepSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.options.Options;

import org.mmtk.vm.Collection;

import org.vmmagic.pragma.*;

/**
 * This class implements the global state of a full-heap collector
 * with a copying nursery and mark-sweep mature space.  Unlike a full 
 * generational collector, there is no write barrier, no remembered set, and 
 * every collection is full-heap.
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities, and divides global and local state
 * into separate class hierarchies.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  There is a single instance of Plan (or the
 * appropriate sub-class), and a 1:1 mapping of PlanLocal to "kernel
 * threads" (aka CPUs or in Jikes RVM, VM_Processors).  Thus instance
 * methods of PlanLocal allow fast, unsychronized access to functions such as
 * allocation and collection.
 *
 * The global instance defines and manages static resources
 * (such as memory and virtual memory resources).  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance properties of MMTk plans.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class CopyMS extends StopTheWorld implements Uninterruptible {

  /****************************************************************************
   * Constants
   */
  public static final int CopyMS_PAGE_RESERVE = (512<<10)>>>LOG_BYTES_IN_PAGE;
  public static final double CopyMS_RESERVE_FRACTION = 0.1;


  /****************************************************************************
   * Class variables
   */
  public static final CopySpace nurserySpace = new CopySpace("nursery", DEFAULT_POLL_FREQUENCY, (float) 0.15, true, false);
  public static final MarkSweepSpace msSpace = new MarkSweepSpace("ms", DEFAULT_POLL_FREQUENCY, (float) 0.5);

  public static final int NURSERY = nurserySpace.getDescriptor();
  public static final int MARK_SWEEP = msSpace.getDescriptor();

  public static final int ALLOC_NURSERY = ALLOC_DEFAULT;
  public static final int ALLOC_MS = StopTheWorld.ALLOCATORS + 1;
  public static int ALLOCATORS = ALLOC_MS;


  /****************************************************************************
   * Instance variables
   */

  public final Trace trace;

  private int msReservedPages;
  private int availablePreGC;

  /**
   * Constructor.
   *
   */
  public CopyMS() {
	  trace = new Trace(metaDataSpace);
  }

  /**
   * Boot-time initialization
   */
  public void boot() throws InterruptiblePragma {
    super.boot();
    msReservedPages = (int) (getTotalPages() * CopyMS_RESERVE_FRACTION);
  }

  /*****************************************************************************
   *
   * Collection
   */


  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase to execute.
   */
  public final void collectionPhase(int phaseId) throws InlinePragma {
    if (phaseId == PREPARE) {
      super.collectionPhase(phaseId);
      trace.prepare();
      msSpace.prepare();
      nurserySpace.prepare(true);
      return;
    }
    if (phaseId == RELEASE) {
      trace.release();
      msSpace.release();
      nurserySpace.release();

      int available = getTotalPages() - getPagesReserved();

      progress = (available > availablePreGC) && 
                 (available > getExceptionReserve());
      
      if (progress) {
        msReservedPages = (int) (available * CopyMS_RESERVE_FRACTION);
        int threshold = 2 * getExceptionReserve();
        if (threshold < CopyMS_PAGE_RESERVE) threshold = CopyMS_PAGE_RESERVE;
        if (msReservedPages < threshold)
          msReservedPages = threshold;
      } else {
        msReservedPages = msReservedPages/2;
      }

      super.collectionPhase(phaseId);
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
    if (mustCollect || heapFull || nurseryFull) {
      required = space.reservedPages() - space.committedPages();
      // account for copy reserve
      if (space == nurserySpace) required = required<<1;  
      Collection.triggerCollection(Collection.RESOURCE_GC_TRIGGER);
      return true;
    }
    return false;
  }

  /*****************************************************************************
   *
   * Accounting
   */

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  public int getPagesUsed() {
    return super.getPagesUsed() +
      msSpace.reservedPages() +
      nurserySpace.reservedPages();
  }

  /**
   * Return the number of pages reserved for copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for copying.
   */
  public final int getCopyReserve() {
    return nurserySpace.reservedPages() + super.getCopyReserve();
  }

  /**
   * @return The number of pages available for allocation, <i>assuming
   * all future allocation is to the nursery</i>.
   */
  public final int getPagesAvail() {
    return (getTotalPages() - getPagesReserved()) >> 1;
  }

}
