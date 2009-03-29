/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan.generational.marksweep;

import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.generational.*;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.statistics.Stats;

import org.mmtk.vm.VM;

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
 * @see org.mmtk.plan.StopTheWorldCollector
 * @see org.mmtk.plan.CollectorContext
 */
@Uninterruptible
public class GenMSCollector extends GenCollector {

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
  @Inline
  @Override
  public final Address allocCopy(ObjectReference original, int bytes,
                                 int align, int offset, int allocator) {
    if (Stats.GATHER_MARK_CONS_STATS) {
      if (Space.isInSpace(GenMS.NURSERY, original)) GenMS.nurseryMark.inc(bytes);
    }

    if (allocator == Plan.ALLOC_LOS) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Allocator.getMaximumAlignedSize(bytes, align) > Plan.MAX_NON_LOS_COPY_BYTES);
      return los.alloc(bytes, align, offset);
    } else {
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(bytes <= Plan.MAX_NON_LOS_COPY_BYTES);
        VM.assertions._assert(allocator == GenMS.ALLOC_MATURE_MINORGC ||
            allocator == GenMS.ALLOC_MATURE_MAJORGC);
      }
      return mature.alloc(bytes, align, offset);
    }
  }

  /**
   * Perform any post-copy actions.
   *
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  @Inline
  @Override
  public final void postCopy(ObjectReference object, ObjectReference typeRef,
                             int bytes, int allocator) {
    if (allocator == Plan.ALLOC_LOS)
      Plan.loSpace.initializeHeader(object, false);
    else
      GenMS.msSpace.postCopy(object, allocator == GenMS.ALLOC_MATURE_MAJORGC);
    if (Gen.USE_OBJECT_BARRIER)
      Plan.markAsUnlogged(object);
  }

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a (local) collection phase.
   *
   * @param phaseId Collection phase to perform
   * @param primary Is this thread to do the one-off thread-local tasks
   */
  @NoInline
  public void collectionPhase(short phaseId, boolean primary) {
    if (global().traceFullHeap()) {
      if (phaseId == GenMS.PREPARE) {
        super.collectionPhase(phaseId, primary);
        matureTrace.prepare();
        if (global().gcFullHeap) mature.prepare();
        return;
      }

      if (phaseId == GenMS.CLOSURE) {
        matureTrace.completeTrace();
        return;
      }

      if (phaseId == GenMS.RELEASE) {
        matureTrace.release();
        if (global().gcFullHeap) {
          mature.release();
        }
        super.collectionPhase(phaseId, primary);
        return;
      }
    }

    super.collectionPhase(phaseId, primary);
  }

  @Inline
  public final TraceLocal getFullHeapTrace() {
    return matureTrace;
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as a <code>GenMS</code> instance. */
  @Inline
  private static GenMS global() {
    return (GenMS) VM.activePlan.global();
  }
}
