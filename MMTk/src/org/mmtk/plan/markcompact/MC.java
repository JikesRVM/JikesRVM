/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.markcompact;

import org.mmtk.plan.*;
import org.mmtk.policy.MarkCompactSpace;
import org.mmtk.policy.Space;
import org.mmtk.vm.Collection;

import org.vmmagic.pragma.*;

/**
 * This class implements the global state of a simple sliding mark-compact
 * collector.
 * 
 * FIXME Need algorithmic overview and references.
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
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class MC extends StopTheWorld implements Uninterruptible {

  /****************************************************************************
   * Class variables
   */

  public static final MarkCompactSpace mcSpace
    = new MarkCompactSpace("mc", DEFAULT_POLL_FREQUENCY, (float) 0.6);
  public static final int MARK_COMPACT = mcSpace.getDescriptor();

  /* Phases */
  public static final int PREPARE_FORWARD     = new SimplePhase("fw-prepare", Phase.GLOBAL_FIRST  ).getId();
  public static final int FORWARD_CLOSURE     = new SimplePhase("fw-closure", Phase.COLLECTOR_ONLY).getId();
  public static final int RELEASE_FORWARD     = new SimplePhase("fw-release", Phase.GLOBAL_LAST   ).getId();

  /* FIXME these two phases need to be made per-collector phases */
  public static final int CALCULATE_FP        = new SimplePhase("calc-fp",    Phase.MUTATOR_ONLY  ).getId();
  public static final int COMPACT             = new SimplePhase("compact",    Phase.MUTATOR_ONLY  ).getId();

  /**
   * This is the phase that is executed to perform a mark-compact collection.
   * 
   * FIXME: Far too much duplication and inside knowledge of StopTheWorld
   */
  public ComplexPhase mcCollection = new ComplexPhase("collection", null, new int[] {
      initPhase,
      rootClosurePhase,
      refTypeClosurePhase,
      completeClosurePhase,
      CALCULATE_FP,
      PREPARE_FORWARD,
      PREPARE_MUTATOR,
      ROOTS,
      forwardPhase,
      FORWARD_CLOSURE,
      RELEASE_MUTATOR,
      RELEASE_FORWARD,
      COMPACT,
      finishPhase});

  /****************************************************************************
   * Instance variables
   */

  public final Trace markTrace;
  public final Trace forwardTrace;

  /**
   * Constructor.
   * 
   */
  public MC() {
    markTrace = new Trace(metaDataSpace);
    forwardTrace = new Trace(metaDataSpace);
    collection = mcCollection;
  }

  /**
   * Boot-time initialization
   */
  public void boot() throws InterruptiblePragma {
    super.boot();
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
      markTrace.prepare();
      mcSpace.prepare();
      return;
    }
    if (phaseId == RELEASE) {
      markTrace.release();
      mcSpace.release();
      super.collectionPhase(phaseId);
      return;
    }

    if (phaseId == PREPARE_FORWARD) {
      super.collectionPhase(PREPARE);
      forwardTrace.prepare();
      mcSpace.prepare();
      return;
    }
    if (phaseId == RELEASE_FORWARD) {
      forwardTrace.release();
      mcSpace.release();
      super.collectionPhase(RELEASE);
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
    mustCollect |= stressTestGCRequired();
    boolean heapFull = getPagesReserved() > getTotalPages();

    if (mustCollect || heapFull) {
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
    return (mcSpace.reservedPages() + super.getPagesUsed());
  }
}
