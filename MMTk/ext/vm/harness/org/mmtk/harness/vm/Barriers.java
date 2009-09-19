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
package org.mmtk.harness.vm;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.*;

/**
 * MMTk Harness implementation of Barriers interface
 */
@Uninterruptible
public class Barriers extends org.mmtk.vm.Barriers {
  /**
   * Perform the actual write of the write barrier.
   *
   * @param ref The object that has the reference field
   * @param target The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void objectReferenceWrite(ObjectReference ref, ObjectReference target, Word slot, Word unused, int mode) {
    slot.toAddress().store(target);
  }

  /**
   * Perform the actual read of the read barrier.
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public ObjectReference objectReferenceRead(ObjectReference ref,Word slot, Word unused, int mode) {
    return slot.toAddress().loadObjectReference();
  }

  /**
   * Perform the actual write of the non-heap write barrier.  This is
   * used when the store is not to an object, but to a non-heap location
   * such as statics or the stack.
   *
   * @param slot The address that contains the reference field
   * @param target The value that the slot will be updated to
   * @param unusedA Opaque, VM-specific, meta-data identifying the slot
   * @param unusedB Opaque, VM-specific, meta-data identifying the slot
   */
  @Override
  public void objectReferenceNonHeapWrite(Address slot, ObjectReference target, Word unusedA, Word unusedB) {
    slot.store(target);
  }

  /**
   * Atomically write a reference field of an object or array and return
   * the old value of the reference field.
   *
   * @param ref The object that has the reference field
   * @param target The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return The value that was replaced by the write.
   */
  @Override
  public ObjectReference objectReferenceAtomicWrite(ObjectReference ref, ObjectReference target, Word slot, Word unused, int mode) {
    ObjectReference old;
    do {
      old = slot.toAddress().prepareObjectReference();
    } while (!slot.toAddress().attempt(old, target));
    return old;
  }

  /**
   * Attempt an atomic compare and exchange in a write barrier sequence.
   *
   * @param ref The object that has the reference field
   * @param old The old reference to be swapped out
   * @param target The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return True if the compare and swap was successful
   */
  @Override
  public boolean objectReferenceTryCompareAndSwap(ObjectReference ref, ObjectReference old, ObjectReference target, Word slot, Word unused, int mode) {
    return slot.toAddress().attempt(old, target);
  }


  /**
   * Perform the actual write of the write barrier, writing the value as a raw Word.
   *
   * @param ref The object that has the reference field
   * @param target The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void wordWrite(ObjectReference ref, Word target, Word slot, Word unused, int mode) {
    slot.toAddress().store(target);
  }

  /**
   * Atomically write a reference field of an object or array and return
   * the old value of the reference field.
   *
   * @param ref The object that has the reference field
   * @param target The value that the slot will be updated to
   * @param slot Unused
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return The raw value that was replaced by the write.
   */
  @Override
  public Word wordAtomicWrite(ObjectReference ref, Word target,
      Word slot, Word unused, int mode) {
    Word old;
    do {
      old = slot.toAddress().prepareWord();
    } while (!slot.toAddress().attempt(old, target));
    return old;
  }

  /**
   * Attempt an atomic compare and exchange in a write barrier sequence.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param old The old reference to be swapped out
   * @param target The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return True if the compare and swap was successful
   */
  @Override
  public boolean wordTryCompareAndSwap(ObjectReference ref, Word old, Word target,
      Word slot, Word unused, int mode) {
    return slot.toAddress().attempt(old, target);
  }

  /**
   * Perform the actual read of the read barrier, returning the value as a raw Word.
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public Word wordRead(ObjectReference ref, Word slot, Word unused, int mode) {
    return slot.toAddress().loadWord();
  }

  /**
   * Sets an element of an object array without invoking any write
   * barrier.  This method is called by the Map class to ensure
   * potentially-allocation-triggering write barriers do not occur in
   * allocation slow path code.
   *
   * @param dst the destination array
   * @param index the index of the element to set
   * @param value the new value for the element
   */
  @Override
  public void objectArrayStoreNoGCBarrier(Object [] dst, int index, Object value) {
    dst[index] = value;
  }
}
