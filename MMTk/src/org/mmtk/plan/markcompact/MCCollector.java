/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.markcompact;

import org.mmtk.plan.*;

import org.mmtk.utility.sanitychecker.SanityCheckerLocal;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-collector thread</i> behavior 
 * and state for the <i>MC</i> plan, which implements a full-heap
 * mark-compact collector.<p>
 * 
 * Specifically, this class defines <i>MC</i> collection behavior
 * (through <code>trace</code> and the <code>collectionPhase</code>
 * method), and collection-time allocation.<p>
 * 
 * @see MC for an overview of the mark-compact algorithm.<p>
 * 
 * FIXME Currently MC does not properly separate mutator and collector
 * behaviors, so some of the collection logic in MCMutator should
 * really be per-collector thread, not per-mutator thread.
 * 
 * @see MC
 * @see MCMutator
 * @see StopTheWorldCollector
 * @see CollectorContext
 * @see SimplePhase#delegatePhase
 * 
 * $Id$
 * 
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
@Uninterruptible public class MCCollector extends StopTheWorldCollector {

  private static final boolean TRACE_MARK = false;
  private static final boolean TRACE_FORWARD = true;

  /****************************************************************************
   * Instance fields
   */

  private final MCMarkTraceLocal markTrace;
  private final MCForwardTraceLocal forwardTrace;
  private boolean currentTrace;

  // Sanity checking
  private final MCSanityCheckerLocal sanityChecker;

  
  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   */
  public MCCollector() {
    markTrace = new MCMarkTraceLocal(global().markTrace);
    forwardTrace = new MCForwardTraceLocal(global().forwardTrace);
    sanityChecker = new MCSanityCheckerLocal();
  }

  
  /****************************************************************************
   * 
   * Collection-time allocation
   */

  /**
   * Allocate space for copying an object (this method <i>does not</i>
   * copy the object, it only allocates space)
   * 
   * @param original A reference to the original object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @return The address of the first byte of the allocated region
   */
  public Address allocCopy(ObjectReference original, int bytes,
      int align, int offset, int allocator)
  throws InlinePragma {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(allocator == MC.ALLOC_IMMORTAL);

    return immortal.alloc(bytes, align, offset, true);
  }

  /**
   * Perform any post-copy actions.
   * 
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  public void postCopy(ObjectReference object, ObjectReference typeRef,
      int bytes, int allocator)
  throws InlinePragma {
    MC.immortalSpace.initializeHeader(object);
  }

  /****************************************************************************
   * 
   * Collection
   */

  /**
   * Perform a per-collector collection phase.
   * 
   * @param phaseId The collection phase to perform
   * @param primary Perform any single-threaded activities using this thread.
   */
  public final void collectionPhase(int phaseId, boolean primary)
      throws InlinePragma {
    if (phaseId == MC.PREPARE) {
      currentTrace = TRACE_MARK;
      super.collectionPhase(phaseId, primary);
      markTrace.prepare();
      return;
    }

    if (phaseId == MC.START_CLOSURE) {
      markTrace.startTrace();
      return;
    }

    if (phaseId == MC.COMPLETE_CLOSURE) {
      markTrace.completeTrace();
      return;
    }

    if (phaseId == MC.RELEASE) {
      markTrace.release();
      super.collectionPhase(phaseId, primary);
      return;
    }

    if (phaseId == MC.PREPARE_FORWARD) {
      currentTrace = TRACE_FORWARD;
      super.collectionPhase(MC.PREPARE, primary);
      forwardTrace.prepare();
      return;
    }

    if (phaseId == MC.FORWARD_CLOSURE) {
      forwardTrace.startTrace();
      forwardTrace.completeTrace();
      return;
    }

    if (phaseId == MC.RELEASE_FORWARD) {
      forwardTrace.release();
      super.collectionPhase(MC.RELEASE, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   * 
   * Miscellaneous
   */

  /** @return The current trace instance. */
  public final TraceLocal getCurrentTrace() {
    if (currentTrace == TRACE_MARK) {
      return markTrace;
    } else {
      return forwardTrace;
    }
  }

  /** @return Return the current sanity checker. */
  public SanityCheckerLocal getSanityChecker() {
    return sanityChecker;
  }

  /** @return The active global plan as an <code>MC</code> instance. */
  private static final MC global() throws InlinePragma {
    return (MC) VM.activePlan.global();
  }
}
