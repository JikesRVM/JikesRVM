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
package org.mmtk.plan.semispace.gctrace;

import org.mmtk.plan.semispace.SSMutator;
import org.mmtk.plan.*;
import org.mmtk.utility.TraceGenerator;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-mutator thread</i> behavior and state for the
 * <i>GCTrace</i> plan, which implements a GC tracing algorithm.<p>
 *
 * Specifically, this class defines <i>SS</i> mutator-time allocation, write
 * barriers, and per-mutator collection semantics.<p>
 *
 * See {@link GCTrace} for an overview of the GC trace algorithm.<p>
 *
 * @see SSMutator
 * @see GCTrace
 * @see GCTraceCollector
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 */
@Uninterruptible public class GCTraceMutator extends SSMutator {

  /****************************************************************************
   *
   * Mutator-time allocation
   */

  /**
   * {@inheritDoc}
   */
  @Override
  @Inline
  public final void postAlloc(ObjectReference object, ObjectReference typeRef,
      int bytes, int allocator) {
    /* Make the trace generator aware of the new object. */
    TraceGenerator.addTraceObject(object, allocator);

    super.postAlloc(object, typeRef, bytes, allocator);

    /* Now have the trace process aware of the new allocation. */
    GCTrace.traceInducedGC = TraceGenerator.MERLIN_ANALYSIS;
    TraceGenerator.traceAlloc(allocator == GCTrace.ALLOC_IMMORTAL, object, typeRef, bytes);
    GCTrace.traceInducedGC = false;
  }


  /****************************************************************************
   *
   * Write barrier.
   */

  /**
   * {@inheritDoc}<p>
   *
   * In this case, we remember the address of the source of the
   * pointer if the new reference points into the nursery from
   * non-nursery space.
   */
  @Override
  @Inline
  public final void objectReferenceWrite(ObjectReference src, Address slot,
      ObjectReference tgt, Word metaDataA,
      Word metaDataB, int mode) {
    TraceGenerator.processPointerUpdate(mode == INSTANCE_FIELD,
        src, slot, tgt);
    VM.barriers.objectReferenceWrite(src, tgt, metaDataA, metaDataB, mode);
  }

  @Override
  @Inline
  public boolean objectReferenceTryCompareAndSwap(ObjectReference src, Address slot,
      ObjectReference old, ObjectReference tgt, Word metaDataA,
      Word metaDataB, int mode) {
    boolean result = VM.barriers.objectReferenceTryCompareAndSwap(src, old, tgt, metaDataA, metaDataB, mode);
    if (result) {
      TraceGenerator.processPointerUpdate(mode == INSTANCE_FIELD, src, slot, tgt);
    }
    return result;
  }

  /**
   * {@inheritDoc}<p>
   *
   * @param src The source of the values to be copied
   * @param srcOffset The offset of the first source address, in
   * bytes, relative to <code>src</code> (in principle, this could be
   * negative).
   * @param dst The mutated object, i.e. the destination of the copy.
   * @param dstOffset The offset of the first destination address, in
   * bytes relative to <code>tgt</code> (in principle, this could be
   * negative).
   * @param bytes The size of the region being copied, in bytes.
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  @Override
  public boolean objectReferenceBulkCopy(ObjectReference src, Offset srcOffset,
      ObjectReference dst, Offset dstOffset, int bytes) {
    /* These names seem backwards, but are defined to be compatable with the
     * previous writeBarrier method. */
    Address slot = dst.toAddress().plus(dstOffset);
    Address tgtLoc = src.toAddress().plus(srcOffset);
    for (int i = 0; i < bytes; i += BYTES_IN_ADDRESS) {
      ObjectReference tgt = tgtLoc.loadObjectReference();
      TraceGenerator.processPointerUpdate(false, dst, slot, tgt);
      slot = slot.plus(BYTES_IN_ADDRESS);
      tgtLoc = tgtLoc.plus(BYTES_IN_ADDRESS);
    }
    return false;
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * {@inheritDoc}
   */
  @Override
  public void collectionPhase(short phaseId, boolean primary) {
    if (!GCTrace.traceInducedGC ||
        (phaseId != StopTheWorld.PREPARE) &&
        (phaseId != StopTheWorld.RELEASE)) {
      // Delegate up.
      super.collectionPhase(phaseId, primary);
    }
  }
}
