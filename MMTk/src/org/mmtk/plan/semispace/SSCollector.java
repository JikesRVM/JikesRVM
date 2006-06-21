/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan.semispace;

import org.mmtk.plan.*;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.Space;
import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-collector thread</i> behavior and state for the
 * <i>SS</i> plan, which implements a full-heap semi-space collector.
 * <p>
 * 
 * Specifically, this class defines <i>SS</i> collection behavior (through
 * <code>trace</code> and the <code>collectionPhase</code> method), and
 * collection-time allocation (copying of objects).
 * <p>
 * 
 * @see SS for an overview of the semi-space algorithm.
 *      <p>
 * 
 * @see SS
 * @see SSMutator
 * @see StopTheWorldCollector
 * @see CollectorContext
 * @see SimplePhase#delegatePhase
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author Perry Cheng
 * @author Robin Garner
 * @author Daniel Frampton
 * 
 * @version $Revision$
 * @date $Date$
 */
public class SSCollector extends StopTheWorldCollector implements
    Uninterruptible {

  /*****************************************************************************
   * Instance fields
   */

  protected final SSTraceLocal trace;

  protected final CopyLocal ss;

  /*****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   */
  public SSCollector() {
    ss = new CopyLocal(SS.copySpace0);
    trace = new SSTraceLocal(global().ssTrace);
  }

  /*****************************************************************************
   * 
   * Collection-time allocation
   */

  /**
   * Allocate space for copying an object (this method <i>does not</i> copy the
   * object, it only allocates space)
   * 
   * @param original
   *          A reference to the original object
   * @param bytes
   *          The size of the space to be allocated (in bytes)
   * @param align
   *          The requested alignment.
   * @param offset
   *          The alignment offset.
   * @return The address of the first byte of the allocated region
   */
  public Address allocCopy(ObjectReference original, int bytes, int align,
      int offset, int allocator) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) {
      Assert._assert(bytes <= Plan.LOS_SIZE_THRESHOLD);
      Assert._assert(allocator == SS.ALLOC_SS);
    }

    return ss.alloc(bytes, align, offset);
  }

  /**
   * Perform any post-copy actions.
   * 
   * @param object
   *          The newly allocated object
   * @param typeRef
   *          the type reference for the instance being created
   * @param bytes
   *          The size of the space to be allocated (in bytes)
   */
  public void postCopy(ObjectReference object, ObjectReference typeRef,
      int bytes, int allocator) throws InlinePragma {
    CopySpace.clearGCBits(object);
  }

  /*****************************************************************************
   * 
   * Collection
   */

  /**
   * Perform a per-collector collection phase.
   * 
   * @param phaseId
   *          The collection phase to perform
   * @param primary
   *          Perform any single-threaded activities using this thread.
   */
  public void collectionPhase(int phaseId, boolean primary) throws InlinePragma {
    if (phaseId == SS.PREPARE) {
      // rebind the copy bump pointer to the appropriate semispace.
      ss.rebind(SS.toSpace());
      super.collectionPhase(phaseId, primary);
      return;
    }

    if (phaseId == SS.START_CLOSURE) {
      trace.startTrace();
      return;
    }

    if (phaseId == SS.COMPLETE_CLOSURE) {
      trace.completeTrace();
      return;
    }

    if (phaseId == SS.RELEASE) {
      trace.release();
      super.collectionPhase(phaseId, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /*****************************************************************************
   * 
   * Object processing and tracing
   */

  /**
   * Return true if the given reference is to an object that is within one of
   * the semi-spaces.
   * 
   * @param object
   *          The object in question
   * @return True if the given reference is to an object that is within one of
   *         the semi-spaces.
   */
  public static final boolean isSemiSpaceObject(ObjectReference object) {
    return Space.isInSpace(SS.SS0, object) || Space.isInSpace(SS.SS1, object);
  }

  /*****************************************************************************
   * 
   * Miscellaneous
   */

  /** @return The active global plan as an <code>SS</code> instance. */
  private static final SS global() throws InlinePragma {
    return (SS) ActivePlan.global();
  }

  /** @return the current trace object. */
  public TraceLocal getCurrentTrace() {
    return trace;
  }
}
