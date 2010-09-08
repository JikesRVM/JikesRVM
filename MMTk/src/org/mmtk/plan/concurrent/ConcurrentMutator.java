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
package org.mmtk.plan.concurrent;

import org.mmtk.plan.*;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior
 * and state for a simple whole-heap concurrent collector.
 *
 * @see Concurrent
 * @see ConcurrentCollector
 * @see StopTheWorldMutator
 * @see MutatorContext
 */
@Uninterruptible
public abstract class ConcurrentMutator extends SimpleMutator {

  /****************************************************************************
   * Instance fields
   */
  public static boolean newMutatorBarrierActive = false;
  protected volatile boolean barrierActive = false;

  protected ConcurrentMutator() {
    barrierActive = newMutatorBarrierActive;
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a per-mutator collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param primary Perform any single-threaded activities using this thread.
   */
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == Concurrent.SET_BARRIER_ACTIVE) {
      barrierActive = true;
      return;
    }

    if (phaseId == Concurrent.CLEAR_BARRIER_ACTIVE) {
      barrierActive = false;
      return;
    }

    if (phaseId == Concurrent.FLUSH_MUTATOR) {
      flush();
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
  *
  * Write and read barriers.
  */

  /**
   * A new reference is about to be created. Take appropriate write
   * barrier actions.<p>
   *
   * <b>In this case we employ a Yuasa style snapshot barrier.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param tgt The target of the new reference
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  @Inline
  @Override
  public void objectReferenceWrite(ObjectReference src, Address slot, ObjectReference tgt, Word metaDataA, Word metaDataB, int mode) {
    if (barrierActive) checkAndEnqueueReference(slot.loadObjectReference());
    VM.barriers.objectReferenceWrite(src, tgt, metaDataA, metaDataB, mode);
  }

  /**
   * Attempt to atomically exchange the value in the given slot
   * with the passed replacement value. If a new reference is
   * created, we must then take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param old The old reference to be swapped out
   * @param tgt The target of the new reference
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   * @return True if the swap was successful.
   */
  @Inline
  @Override
  public boolean objectReferenceTryCompareAndSwap(ObjectReference src, Address slot, ObjectReference old,
                                               ObjectReference tgt, Word metaDataA, Word metaDataB, int mode) {
    boolean result = VM.barriers.objectReferenceTryCompareAndSwap(src, old, tgt, metaDataA, metaDataB, mode);
    if (barrierActive) checkAndEnqueueReference(old);
    return result;
  }

  /**
   * A number of references are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
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
  @Inline
  @Override
  public boolean objectReferenceBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    Address cursor = dst.toAddress().plus(dstOffset);
    Address limit = cursor.plus(bytes);
    while (cursor.LT(limit)) {
      ObjectReference ref = cursor.loadObjectReference();
      if (barrierActive) checkAndEnqueueReference(ref);
      cursor = cursor.plus(BYTES_IN_ADDRESS);
    }
    return false;
  }

  /**
   * Read a reference type. In a concurrent collector this may
   * involve adding the referent to the marking queue.
   *
   * @param ref The referent being read.
   * @return The new referent.
   */
  @Inline
  @Override
  public ObjectReference javaLangReferenceReadBarrier(ObjectReference ref) {
    if (barrierActive) checkAndEnqueueReference(ref);
    return ref;
  }

  /**
   * Process a reference that may require being enqueued as part of a concurrent
   * collection.
   *
   * @param ref The reference to check.
   */
  protected abstract void checkAndEnqueueReference(ObjectReference ref);
}
