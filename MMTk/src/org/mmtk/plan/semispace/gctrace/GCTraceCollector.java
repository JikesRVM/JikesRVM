/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 *
 * (C) Copyright Department of Computer Science,
 * University of Massachusetts, Amherst. 2003
 */
package org.mmtk.plan.semispace.gctrace;

import org.mmtk.plan.*;
import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.semispace.*;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;


/**
 * This class implements <i>per-collector thread</i> behavior and state for the
 * <i>GCTrace</i> plan, which implements a GC tracing algorithm.<p>
 * 
 * Specifically, this class defines <i>SS</i> collection behavior
 * (through <code>inducedTrace</code> and the <code>collectionPhase</code>
 * method), and collection-time allocation (copying of objects).<p>
 * 
 * See {@link GCTrace} for an overview of the GC trace algorithm.<p>
 * 
 * @see SSCollector
 * @see GCTrace
 * @see GCTraceMutator
 * @see org.mmtk.plan.StopTheWorldCollector
 * @see org.mmtk.plan.CollectorContext
 * @see org.mmtk.plan.SimplePhase#delegatePhase
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author Perry Cheng
 * @author Daniel Frampton
 * @author Robin Garner
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 * 
 * @version $Revision$
 * @date $Date$
 */
public class GCTraceCollector extends SSCollector implements Uninterruptible {
  /****************************************************************************
   * Instance fields
   */
  protected final GCTraceTraceLocal inducedTrace;

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   */
  public GCTraceCollector() {
    inducedTrace = new GCTraceTraceLocal(global().ssTrace);
  }

  /****************************************************************************
   * 
   * Collection
   */

  /**
   * Perform a per-collector collection phase.
   * 
   * @param phaseId The collection phase to perform
   * @param primary perform any single-threaded local activities.
   */
  public void collectionPhase(int phaseId, boolean primary) {
    if (phaseId == SS.PREPARE) {
      super.collectionPhase(phaseId, primary);
      return;
    }

    if (phaseId == GCTrace.START_CLOSURE) {
      inducedTrace.startTrace();
      return;
    }

    if (phaseId == GCTrace.COMPLETE_CLOSURE) {
      inducedTrace.completeTrace();
      return;
    }

    if (phaseId == GCTrace.RELEASE) {
      inducedTrace.release();
      if (!GCTrace.traceInducedGC) {
        super.collectionPhase(phaseId, primary);
      }
      return;
    }

    /* fall through case */
    if (!GCTrace.traceInducedGC ||
        ((phaseId != StopTheWorld.SOFT_REFS) &&
         (phaseId != StopTheWorld.WEAK_REFS) &&
         (phaseId != StopTheWorld.PHANTOM_REFS) &&
         (phaseId != StopTheWorld.FORWARD_REFS) &&
         (phaseId != StopTheWorld.FORWARD_FINALIZABLE) &&
         (phaseId != StopTheWorld.FINALIZABLE))) {
      // Delegate up.
      super.collectionPhase(phaseId, primary);
      return;
    }
  }

  /****************************************************************************
   * 
   * Miscellaneous
   */

  /** @return The active global plan as a <code>GCTrace</code> instance. */
  private static final GCTrace global() throws InlinePragma {
    return (GCTrace) VM.activePlan.global();
  }

  /** @return The current trace instance */
  public TraceLocal getCurrentTrace() {
    return inducedTrace;
  }
}
