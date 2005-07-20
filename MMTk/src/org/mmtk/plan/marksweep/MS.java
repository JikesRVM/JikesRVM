/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.marksweep;

import org.mmtk.plan.*;
import org.mmtk.policy.MarkSweepSpace;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.policy.Space;
import org.mmtk.vm.Collection;

import org.vmmagic.pragma.*;

/**
 * This class implements the global state of a simple mark-sweep collector.
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
public class MS extends StopTheWorld implements Uninterruptible {

  /****************************************************************************
   * Constants
   */
  public static final int MS_PAGE_RESERVE = (512<<10)>>>LOG_BYTES_IN_PAGE; // 1M
  public static final double MS_RESERVE_FRACTION = 0.1;


  /****************************************************************************
   * Class variables
   */

  public static final MarkSweepSpace msSpace
    = new MarkSweepSpace("ms", DEFAULT_POLL_FREQUENCY, (float) 0.6);
  public static final int MARK_SWEEP = msSpace.getDescriptor();

  /****************************************************************************
   * Instance variables
   */

  public final Trace msTrace;

  private int msReservedPages;
  private int availablePreGC;

  /**
   * Constructor.
   *
   */
  public MS() {
    msTrace = new Trace(metaDataSpace);
  }

  /**
   * Boot-time initialization
   */
  public void boot() throws InterruptiblePragma {
    super.boot();
    msReservedPages = (int) (getTotalPages() * MS_RESERVE_FRACTION);
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
      msTrace.prepare();
      msSpace.prepare();
      return;
    }
    if (phaseId == RELEASE) {
      msTrace.release();
      msSpace.release();

      int available = getTotalPages() - getPagesReserved();

      progress = (available > availablePreGC) && 
                 (available > getExceptionReserve());
      
      if (progress) {
        msReservedPages = (int) (available * MS_RESERVE_FRACTION);
        int threshold = 2 * getExceptionReserve();
        if (threshold < MS_PAGE_RESERVE) threshold = MS_PAGE_RESERVE;
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
    if (getCollectionsInitiated() > 0 || !isInitialized() || space == metaDataSpace) {
          return false;
    }
    mustCollect |= stressTestGCRequired() || MarkSweepLocal.mustCollect();
    availablePreGC = getTotalPages() - getPagesReserved();
    int reserve = (space == msSpace) ? msReservedPages : 0;

    if (mustCollect || availablePreGC <= reserve) {
      required = space.reservedPages() - space.committedPages();
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
   * allocation.  The superclass accounts for its spaces, we just
   * augment this with the mark-sweep space's contribution.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  public int getPagesUsed() {
    return (msSpace.reservedPages() + super.getPagesUsed());
  }


}
