/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan.generational.copying;

import org.mmtk.plan.generational.Gen;
import org.mmtk.plan.generational.GenCollector;
import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.CopySpace;
import org.mmtk.utility.alloc.Allocator;
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
 */
@Uninterruptible
public class GenCopyCollector extends GenCollector {

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
    if (allocator == Plan.ALLOC_LOS) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Allocator.getMaximumAlignedSize(bytes, align) > Plan.MAX_NON_LOS_COPY_BYTES);
      return los.alloc(bytes, align, offset);
    } else {
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(bytes <= Plan.MAX_NON_LOS_COPY_BYTES);
        VM.assertions._assert(allocator == GenCopy.ALLOC_MATURE_MINORGC ||
            allocator == GenCopy.ALLOC_MATURE_MAJORGC);
      }
      return mature.alloc(bytes, align, offset);
    }
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
    if (allocator == Plan.ALLOC_LOS)
      Plan.loSpace.initializeHeader(object, false);
    else if (GenCopy.IGNORE_REMSETS)
      CopySpace.markObject(getCurrentTrace(),object, GenCopy.immortalSpace.getMarkState());
    if (Gen.USE_OBJECT_BARRIER)
      Plan.markAsUnlogged(object);
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
  public void collectionPhase(short phaseId, boolean primary) {
    if (global().traceFullHeap()) {
      if (phaseId == GenCopy.PREPARE) {
        super.collectionPhase(phaseId, primary);
        if (global().gcFullHeap) mature.rebind(GenCopy.toSpace());
      }
      if (phaseId == GenCopy.CLOSURE) {
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
