/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.marksweep;

import org.mmtk.plan.*;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.vm.ActivePlan;

import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-collector thread</i> behavior 
 * and state for the <i>MS</i> plan, which implements a full-heap
 * mark-sweep collector.<p>
 * 
 * Specifically, this class defines <i>MS</i> collection behavior
 * (through <code>trace</code> and the <code>collectionPhase</code>
 * method).<p>
 * 
 * @see MS for an overview of the mark-sweep algorithm.<p>
 * 
 * FIXME The SegregatedFreeList class (and its decendents such as
 * MarkSweepLocal) does not properly separate mutator and collector
 * behaviors, so the ms field below should really not exist in
 * this class as there is no collection-time allocation in this
 * collector.
 *
 * @see MS
 * @see MSMutator
 * @see StopTheWorldCollector
 * @see CollectorContext
 * @see SimplePhase#delegatePhase
 *
 * $Id$
 *
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class MSCollector extends StopTheWorldCollector implements Uninterruptible {

  /****************************************************************************
   * Instance fields
   */
  private MSTraceLocal trace;
  private MarkSweepLocal ms;  // see FIXME at top of this class

  /****************************************************************************
   * Initialization
   */
  
  /**
   * Constructor
   */
  public MSCollector() {
    trace = new MSTraceLocal(global().msTrace);
    ms = new MarkSweepLocal(MS.msSpace);
  }

  /****************************************************************************
   *
   * Collection
   */
  
  /**
   * Perform a per-collector collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param participating Is this thread participating in collection
   *        (as opposed to blocked in a JNI call)
   * @param primary Perform any single-threaded activities using this thread.
   */
  public final void collectionPhase(int phaseId, boolean participating,
                                    boolean primary)
    throws InlinePragma {
    if (phaseId == MS.PREPARE) {
      super.collectionPhase(phaseId, participating, primary);
      ms.prepare();
      trace.prepare();
      return;
    }

    if (phaseId == MS.START_CLOSURE) {
      trace.startTrace();
      return;
    }

    if (phaseId == MS.COMPLETE_CLOSURE) {
      trace.completeTrace();
      return;
    }

    if (phaseId == MS.RELEASE) {
      trace.release();
      super.collectionPhase(phaseId, participating, primary);
      return;
    }

    super.collectionPhase(phaseId, participating, primary);
  }
  
  /****************************************************************************
  *
  * Miscellaneous
  */ 

  /** @return The active global plan as an <code>MS</code> instance. */
  private static final MS global() throws InlinePragma {
    return (MS)ActivePlan.global();
  }

  /** @return The current trace instance. */
  public final TraceLocal getCurrentTrace() {
    return trace;
  }
}
