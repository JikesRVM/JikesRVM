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
package org.mmtk.plan.stickyms;

import org.mmtk.plan.*;
import org.mmtk.plan.marksweep.MSMutator;
import org.mmtk.policy.MarkSweepLocal;

import org.mmtk.utility.HeaderByte;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior
 * and state for the <i>StickyMS</i> plan, which implements a
 * generational mark-sweep collector.<p>
 *
 * Specifically, this class defines <i>MS</i> mutator-time allocation
 * and per-mutator thread collection semantics (flushing and restoring
 * per-mutator allocator state).<p>
 * *
 * @see StickyMS
 * @see StickyMSCollector
 * @see MutatorContext
 * @see Phase
 */
@Uninterruptible
public class StickyMSMutator extends MSMutator {

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
  public StickyMSMutator() {
    ms = new MarkSweepLocal(StickyMS.msSpace);
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

  /**
   * Assert that the remsets have been flushed.  This is critical to
   * correctness.  We need to maintain the invariant that remset entries
   * do not accrue during GC.  If the host JVM generates barrier entires
   * it is its own responsibility to ensure that they are flushed before
   * returning to MMTk.
   */
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
    if (phaseId == StickyMS.PREPARE) {
      flushRememberedSets();
    }
    if (phaseId == StickyMS.RELEASE) {
      assertRemsetFlushed();
    }

    if (!global().collectWholeHeap) {
      if (phaseId == StickyMS.PREPARE) {
        ms.prepare();
        return;
      }

      if (phaseId == StickyMS.RELEASE) {
        ms.release();
        return;
      }
    }

    super.collectionPhase(phaseId, primary);
  }


  @Override
  public void flush() {
    super.flush();
    ms.flush();
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as an <code>MSGen</code> instance. */
  @Inline
  private static StickyMS global() {
    return (StickyMS) VM.activePlan.global();
  }
}
