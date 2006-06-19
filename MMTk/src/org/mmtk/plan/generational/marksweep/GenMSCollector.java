/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.generational.marksweep;

import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.generational.*;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.statistics.Stats;

import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-collector thread</i> behavior and state for
 * the <code>GenMS</code> two-generational copying collector.<p>
 * 
 * Specifically, this class defines semantics specific to the collection of
 * the mature generation (<code>GenCollector</code> defines nursery semantics).
 * In particular the mature space allocator is defined (for collection-time
 * allocation into the mature space), and the mature space per-collector thread
 * collection time semantics are defined.<p>
 * 
 * @see GenMS for a description of the <code>GenMS</code> algorithm.
 * 
 * @see GenMS
 * @see GenMSMutator
 * @see GenCollector
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
public abstract class GenMSCollector extends GenCollector implements Uninterruptible {

  /*****************************************************************************
   *
   * Instance fields
   */

	/** The allocator for the mature space */
  private final MarkSweepLocal mature;
  private final GenMSMatureTraceLocal matureTrace;

  /**
   * Constructor
   */
  public GenMSCollector() {
    mature = new MarkSweepLocal(GenMS.msSpace);
    matureTrace = new GenMSMatureTraceLocal(global().matureTrace, this);
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
   * @param allocator The allocator to use.
   * @return The address of the first byte of the allocated region
   */
  public final Address allocCopy(ObjectReference original, int bytes,
                                 int align, int offset, int allocator)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) {
      Assert._assert(bytes <= Plan.LOS_SIZE_THRESHOLD);
      Assert._assert(allocator == GenMS.ALLOC_MATURE);
    }
    if (Stats.GATHER_MARK_CONS_STATS) {
      if (Space.isInSpace(GenMS.NURSERY, original)) GenMS.nurseryMark.inc(bytes);
    }
    return mature.alloc(bytes, align, offset, GenMS.msSpace.inMSCollection());
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
    GenMS.msSpace.writeMarkBit(object);
    MarkSweepLocal.liveObject(object);
  }

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a (local) collection phase.
   *
   * @param phaseId Collection phase to perform
   * @param participating Is this thread participating or blocked in JNI
   * @param primary Is this thread to do the one-off thread-local tasks
   */
  public void collectionPhase(int phaseId, boolean participating,
                              boolean primary)
    throws NoInlinePragma {
    if (global().traceFullHeap()) {
      if (phaseId == GenMS.PREPARE) {
        super.collectionPhase(phaseId, participating, primary);
        matureTrace.prepare();
        if (global().gcFullHeap) mature.prepare();
        return;
      }

      if (phaseId == GenMS.START_CLOSURE) {
        matureTrace.startTrace();
        return;
      }

      if (phaseId == GenMS.COMPLETE_CLOSURE) {
        matureTrace.completeTrace();
        return;
      }

      if (phaseId == GenMS.RELEASE) {
        matureTrace.release();
        if (global().gcFullHeap) {
        	mature.releaseCollector();
        	mature.releaseMutator();
        }
        super.collectionPhase(phaseId, participating, primary);
        return;
      }
    }

    super.collectionPhase(phaseId, participating, primary);
  }

  public final TraceLocal getFullHeapTrace() throws InlinePragma {
    return matureTrace;
  }
  
	/****************************************************************************
	 *
	 * Miscellaneous
	 */
	
  /** @return The active global plan as a <code>GenMS</code> instance. */
  private static final GenMS global() throws InlinePragma {
    return (GenMS)ActivePlan.global();
  }
}
