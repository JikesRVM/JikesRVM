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
package org.mmtk.plan.generational.immix;

import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.generational.*;
import org.mmtk.policy.Space;
import org.mmtk.utility.HeaderByte;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.alloc.ImmixAllocator;
import org.mmtk.utility.statistics.Stats;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-collector thread</i> behavior and state for
 * the <code>GenImmix</code> two-generational copying collector.<p>
 *
 * Specifically, this class defines semantics specific to the collection of
 * the copy generation (<code>GenCollector</code> defines nursery semantics).
 * In particular the copy space allocator is defined (for collection-time
 * allocation into the copy space), and the copy space per-collector thread
 * collection time semantics are defined.<p>
 *
 * @see GenImmix for a description of the <code>GenImmix</code> algorithm.
 *
 * @see GenImmix
 * @see GenImmixMutator
 * @see GenCollector
 * @see org.mmtk.plan.StopTheWorldCollector
 * @see org.mmtk.plan.CollectorContext
 */
@Uninterruptible
public class GenImmixCollector extends GenCollector {

  /*****************************************************************************
   *
   * Instance fields
   */
  private final GenImmixMatureTraceLocal matureTrace = new GenImmixMatureTraceLocal(global().matureTrace, this);
  private final GenImmixMatureDefragTraceLocal defragTrace = new GenImmixMatureDefragTraceLocal(global().matureTrace, this);

  private final org.mmtk.policy.immix.CollectorLocal immix = new org.mmtk.policy.immix.CollectorLocal(GenImmix.immixSpace);

  private final ImmixAllocator copy = new ImmixAllocator(GenImmix.immixSpace, true, false);
  private final ImmixAllocator defragCopy = new ImmixAllocator(GenImmix.immixSpace, true, true);

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
  @Inline
  public final Address allocCopy(ObjectReference original, int bytes,
                                 int align, int offset, int allocator) {

    if (Stats.GATHER_MARK_CONS_STATS) {
      if (Space.isInSpace(GenImmix.NURSERY, original)) GenImmix.nurseryMark.inc(bytes);
    }
    if (allocator == Plan.ALLOC_LOS) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Allocator.getMaximumAlignedSize(bytes, align) > Plan.MAX_NON_LOS_COPY_BYTES);
      return los.alloc(bytes, align, offset);
    } else {
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(bytes <= Plan.MAX_NON_LOS_COPY_BYTES);
        if (GenImmix.immixSpace.inImmixCollection())
          VM.assertions._assert(allocator == GenImmix.ALLOC_MATURE_MAJORGC);
        else
          VM.assertions._assert(allocator == GenImmix.ALLOC_MATURE_MINORGC);
      }
      if (GenImmix.immixSpace.inImmixDefragCollection()) {
        return defragCopy.alloc(bytes, align, offset);
      } else
        return copy.alloc(bytes, align, offset);
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
  public final void postCopy(ObjectReference object, ObjectReference typeRef,
      int bytes, int allocator) {
    if (allocator == Plan.ALLOC_LOS)
      Plan.loSpace.initializeHeader(object, false);
    else {
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert((!GenImmix.immixSpace.inImmixCollection() && allocator == GenImmix.ALLOC_MATURE_MINORGC) ||
            (GenImmix.immixSpace.inImmixCollection() && allocator == GenImmix.ALLOC_MATURE_MAJORGC));
      }
      GenImmix.immixSpace.postCopy(object, bytes, allocator == GenImmix.ALLOC_MATURE_MAJORGC);
    }
    if (Gen.USE_OBJECT_BARRIER)
      HeaderByte.markAsUnlogged(object);
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
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {
    TraceLocal trace = GenImmix.immixSpace.inImmixDefragCollection() ? defragTrace : matureTrace;

    if (global().traceFullHeap()) {
      if (phaseId == GenImmix.PREPARE) {
        super.collectionPhase(phaseId, primary);
        trace.prepare();
        copy.reset();
        if (global().gcFullHeap) {
          immix.prepare(true);
          defragCopy.reset();
        }
        return;
      }

      if (phaseId == GenImmix.CLOSURE) {
        trace.completeTrace();
        return;
      }

      if (phaseId == GenImmix.RELEASE) {
        trace.release();
        if (global().gcFullHeap) {
          immix.release(true);
          copy.reset();
        }
        super.collectionPhase(phaseId, primary);
        return;
      }
    }

    super.collectionPhase(phaseId, primary);
  }

  @Inline
  public final TraceLocal getFullHeapTrace() {
    return GenImmix.immixSpace.inImmixDefragCollection() ? defragTrace : matureTrace;
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as a <code>GenImmix</code> instance. */
  @Inline
  private static GenImmix global() {
    return (GenImmix) VM.activePlan.global();
  }
}
