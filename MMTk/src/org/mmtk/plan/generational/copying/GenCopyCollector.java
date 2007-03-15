/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2005
 */
package org.mmtk.plan.generational.copying;

import org.mmtk.plan.generational.GenCollector;
import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.CopySpace;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-collector thread</i> behavior and state for
 * the <code>GenCopy</code> two-generational copying collector.<p>
 * 
 * Specifically, this class defines semantics specific to the collection of
 * the mature generation (<code>GenCollector</code> defines nursery semantics).
 * In particular the mature space allocator is defined (for collection-time
 * allocation into the mature space), and the mature space per-collector thread
 * collection time semantics are defined.<p>
 * 
 * @see GenCopy for a description of the <code>GenCopy</code> algorithm.
 * 
 * @see GenCopy
 * @see GenCopyMutator
 * @see GenCollector
 * @see org.mmtk.plan.StopTheWorldCollector
 * @see org.mmtk.plan.CollectorContext
 * @see org.mmtk.plan.SimplePhase#delegatePhase
 * 
 *
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 */
@Uninterruptible public abstract class GenCopyCollector extends GenCollector {

  /******************************************************************
   * Instance fields
   */

  /** The allocator for the mature space */
  private final CopyLocal mature;

  /** The trace object for full-heap collections */
  private final GenCopyMatureTraceLocal matureTrace;

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   */
  public GenCopyCollector() {
    mature = new CopyLocal(GenCopy.toSpace());
    matureTrace = new GenCopyMatureTraceLocal(global().matureTrace, this);
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
  @Inline
  public Address allocCopy(ObjectReference original, int bytes,
      int align, int offset, int allocator) { 
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(bytes <= Plan.LOS_SIZE_THRESHOLD);
      VM.assertions._assert(allocator == GenCopy.ALLOC_MATURE_MINORGC ||
                     allocator == GenCopy.ALLOC_MATURE_MAJORGC);
    }

    Address result = mature.alloc(bytes, align, offset, true);
    return result;
  }

  /**
   * Perform any post-copy actions.  In this case we clear any bits used 
   * for this object's GC metadata.
   * 
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator to allocate from
   */
  @Inline
  public final void postCopy(ObjectReference object, ObjectReference typeRef,
      int bytes, int allocator) { 
    CopySpace.clearGCBits(object);
    if (GenCopy.IGNORE_REMSETS)
      CopySpace.markObject(getCurrentTrace(),object, GenCopy.immortalSpace.getMarkState());
  }

  
  /*****************************************************************************
   * 
   * Collection
   */

  /**
   * Execute a per-collector collection phase.
   * 
   * @param phaseId The phase to execute.
   * @param primary True if this thread should peform local single-threaded
   * actions.
   */
  public void collectionPhase(int phaseId, boolean primary) {
    if (global().traceFullHeap()) {
      if (phaseId == GenCopy.PREPARE) {
        super.collectionPhase(phaseId, primary);
        if (global().gcFullHeap) mature.rebind(GenCopy.toSpace());       
      }
      if (phaseId == GenCopy.START_CLOSURE) {
        matureTrace.startTrace();
        return;
      }

      if (phaseId == GenCopy.COMPLETE_CLOSURE) {
        matureTrace.completeTrace();
        return;
      }
      if (phaseId == GenCopy.RELEASE) {
        matureTrace.release();
        super.collectionPhase(phaseId, primary);
        return;
      }
    }
    super.collectionPhase(phaseId, primary);
  }

  /*****************************************************************************
   * 
   * Miscellaneous
   */

  /** @return The active global plan as a <code>GenCopy</code> instance. */
  private static GenCopy global() {
    return (GenCopy) VM.activePlan.global();
  }

  /** Show the status of the mature allocator. */
  protected final void showMature() {
    mature.show();
  }

  public final TraceLocal getFullHeapTrace() { return matureTrace; }
}
