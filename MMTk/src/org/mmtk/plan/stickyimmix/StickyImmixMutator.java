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
package org.mmtk.plan.stickyimmix;

import org.mmtk.plan.*;
import org.mmtk.plan.immix.ImmixMutator;

import org.mmtk.utility.HeaderByte;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior
 * and state for the <i>StickyImmix</i> plan, which implements a
 * generational mark-sweep collector.<p>
 *
 * Specifically, this class defines <i>MS</i> mutator-time allocation
 * and per-mutator thread collection semantics (flushing and restoring
 * per-mutator allocator state).<p>
 * *
 * @see StickyImmix
 * @see StickyImmixCollector
 * @see MutatorContext
 * @see Phase
 */
@Uninterruptible
public class StickyImmixMutator extends ImmixMutator {

  /****************************************************************************
   * Instance fields
   */

  /**
   *
   */
  private ObjectReferenceDeque modBuffer;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public StickyImmixMutator() {
    super();
    modBuffer = new ObjectReferenceDeque("mod buf", global().modPool);
  }

  /****************************************************************************
   *
   * Barriers
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
      ObjectReference tgt, Word metaDataA, Word metaDataB, int mode) {
    if (HeaderByte.isUnlogged(src))
      logSource(src);
    VM.barriers.objectReferenceWrite(src, tgt, metaDataA, metaDataB, mode);
  }

  /**
   * {@inheritDoc}<p>
   *
   * In this case, we remember the mutated source address range and
   * will scan that address range at GC time.
   *
   * @param src The source of the values to copied
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
  @Inline
  public final boolean objectReferenceBulkCopy(ObjectReference src, Offset srcOffset,
      ObjectReference dst, Offset dstOffset, int bytes) {
    if (HeaderByte.isUnlogged(src))
      logSource(src);
    return false;
  }

  @Override
  @Inline
  public boolean objectReferenceTryCompareAndSwap(ObjectReference src, Address slot,
                                               ObjectReference old, ObjectReference tgt, Word metaDataA,
                                               Word metaDataB, int mode) {
    if (HeaderByte.isUnlogged(src))
      logSource(src);
    return VM.barriers.objectReferenceTryCompareAndSwap(src,old,tgt,metaDataA,metaDataB,mode);
  }

  /**
   * Add an object to the modified objects buffer and mark the
   * object has having been logged.  Since duplicate entries do
   * not raise any correctness issues, we do <i>not</i> worry
   * about synchronization and allow threads to race to log the
   * object, potentially including it twice (unlike reference
   * counting where duplicates would lead to incorrect reference
   * counts).
   *
   * @param src The object to be logged
   */
  private void logSource(ObjectReference src) {
    HeaderByte.markAsLogged(src);
    modBuffer.push(src);
  }

  @Override
  public final void flushRememberedSets() {
    modBuffer.flushLocal();
    assertRemsetFlushed();
  }

  public final void assertRemsetFlushed() {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(modBuffer.isFlushed());
    }
  }


  /****************************************************************************
   *
   * Collection
   */

  /**
   * {@inheritDoc}
   */
  @Override
  @Inline
  public final void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == StickyImmix.PREPARE) {
      flushRememberedSets();
    }
    if (phaseId == StickyImmix.RELEASE) {
      assertRemsetFlushed();
    }

    if (!global().collectWholeHeap) {
      if (phaseId == StickyImmix.PREPARE) {
        immix.prepare();
        return;
      }

      if (phaseId == StickyImmix.RELEASE) {
        immix.release();
        return;
      }
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as an <code>MSGen</code> instance. */
  @Inline
  private static StickyImmix global() {
    return (StickyImmix) VM.activePlan.global();
  }
}
