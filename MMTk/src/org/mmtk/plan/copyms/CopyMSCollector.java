/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.copyms;

import org.mmtk.plan.*;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.utility.sanitychecker.SanityCheckerLocal;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-collector thread</i> behavior 
 * and state for the <i>CopyMS</i> plan.<p>
 * 
 * Specifically, this class defines <i>CopyMS</i> 
 * collection behavior (through <code>trace</code> and
 * the <code>collectionPhase</code> method), and
 * collection-time allocation into the mature space.
 * 
 * @see CopyMS
 * @see CopyMSMutator
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
public abstract class CopyMSCollector extends StopTheWorldCollector implements Uninterruptible {

  /****************************************************************************
   * Instance fields
   */

  private MarkSweepLocal mature;
  private CopyMSTraceLocal trace;

  // Sanity checking
  private CopyMSSanityCheckerLocal sanityChecker;

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Create a new (local) instance.
   */
  public CopyMSCollector() {
    mature = new MarkSweepLocal(CopyMS.msSpace);
    trace = new CopyMSTraceLocal(global().trace);
    sanityChecker = new CopyMSSanityCheckerLocal();
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
  public final Address allocCopy(ObjectReference original, int bytes,
      int align, int offset, int allocator)
  throws InlinePragma {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(bytes <= Plan.LOS_SIZE_THRESHOLD);
      VM.assertions._assert(allocator == CopyMS.ALLOC_MS);
    }
    return mature.alloc(bytes, align, offset, CopyMS.msSpace.inMSCollection());
  }

  /**
   * Perform any post-copy actions.
   * 
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  public final void postCopy(ObjectReference object, ObjectReference typeRef,
      int bytes, int allocator)
  throws InlinePragma {
    CopyMS.msSpace.postCopy(object, true);
  }

  /****************************************************************************
   * 
   * Collection
   */

  /**
   * Perform a per-collector collection phase.
   * 
   * @param phaseId The collection phase to perform
   * @param primary Use this thread for single-threaded local activities.
   */
  public final void collectionPhase(int phaseId, boolean primary)
      throws InlinePragma {
    if (phaseId == CopyMS.PREPARE) {
      super.collectionPhase(phaseId, primary);
      mature.prepare();
      trace.prepare();
      return;
    }

    if (phaseId == CopyMS.START_CLOSURE) {
      trace.startTrace();
      return;
    }

    if (phaseId == CopyMS.COMPLETE_CLOSURE) {
      trace.completeTrace();
      return;
    }

    if (phaseId == CopyMS.RELEASE) {
      mature.releaseCollector();
      mature.releaseMutator();
      trace.release();
      super.collectionPhase(phaseId, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   * 
   * Miscellaneous
   */

  /** @return the active global plan as an <code>MS</code> instance. */
  private static final CopyMS global() throws InlinePragma {
    return (CopyMS) VM.activePlan.global();
  }

  /** @return Return the current sanity checker. */
  public SanityCheckerLocal getSanityChecker() {
    return sanityChecker;
  }

  /** @return The current trace instance. */
  public final TraceLocal getCurrentTrace() { return trace; }
  
}
