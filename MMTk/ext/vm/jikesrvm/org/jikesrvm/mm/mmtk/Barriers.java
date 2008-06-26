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
package org.jikesrvm.mm.mmtk;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.runtime.Magic;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

@Uninterruptible
public class Barriers extends org.mmtk.vm.Barriers implements SizeConstants {
  /**
   * Perform the actual write of the write barrier.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param target The value that the slot will be updated to
   * @param offset The offset from the ref (metaDataA)
   * @param locationMetadata An index of the FieldReference (metaDataB)
   * @param mode The context in which the write is occuring
   */
  @Inline
  public final void performWriteInBarrier(ObjectReference ref, Address slot,
                                           ObjectReference target, Offset offset,
                                           int locationMetadata, int mode) {
    Object obj = ref.toObject();
    Magic.setObjectAtOffset(obj, offset, target.toObject(), locationMetadata);
  }

  /**
   * Perform the actual write of the write barrier, writing the value as a raw word.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param rawTarget The value that the slot will be updated to
   * @param offset The offset from the ref (metaDataA)
   * @param locationMetadata An index of the FieldReference (metaDataB)
   * @param mode The context in which the write is occuring
   */
  @Inline
  public final void performRawWriteInBarrier(ObjectReference ref, Address slot,
                                             Word rawTarget, Offset offset,
                                             int locationMetadata, int mode) {
    Object obj = ref.toObject();
    Magic.setWordAtOffset(obj, offset, rawTarget, locationMetadata);
  }

  /**
   * Perform the actual read of the read barrier.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param offset The offset from the ref (metaDataA)
   * @param locationMetadata An index of the FieldReference (metaDataB)
   * @param mode The context in which the write is occuring
   * @return the read value
   */
  @Inline
  public final ObjectReference performReadInBarrier(ObjectReference ref, Address slot,
                                                    Offset offset, int locationMetadata, int mode) {
    Object obj = ref.toObject();
    return ObjectReference.fromObject(Magic.getObjectAtOffset(obj, offset, locationMetadata));
  }

  /**
   * Perform the actual read of the read barrier, returning the value as a raw word.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param offset The offset from the ref (metaDataA)
   * @param locationMetadata An index of the FieldReference (metaDataB)
   * @param mode The context in which the write is occuring
   * @return the read value
   */
  @Inline
  public final Word performRawReadInBarrier(ObjectReference ref, Address slot,
                                            Offset offset, int locationMetadata, int mode) {
    Object obj = ref.toObject();
    return Magic.getWordAtOffset(obj, offset, locationMetadata);
  }

  /**
   * Atomically write a reference field of an object or array and return
   * the old value of the reference field.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param target The value that the slot will be updated to
   * @param offset The offset from the ref (metaDataA)
   * @param locationMetadata An index of the FieldReference (metaDataB)
   * @param mode The context in which the write is occuring
   * @return The value that was replaced by the write.
   */
  @Inline
  public final ObjectReference performWriteInBarrierAtomic(
                                           ObjectReference ref, Address slot,
                                           ObjectReference target, Offset offset,
                                           int locationMetadata, int mode) {
    Object obj = ref.toObject();
    Object newObject = target.toObject();
    Object oldObject;
    do {
      oldObject = Magic.prepareObject(obj, offset);
    } while (!Magic.attemptObject(obj, offset, oldObject, newObject));
    return ObjectReference.fromObject(oldObject);
  }


  /**
   * Atomically write a raw reference field of an object or array and return
   * the old value of the reference field.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param rawTarget The value that the slot will be updated to
   * @param offset The offset from the ref (metaDataA)
   * @param locationMetadata An index of the FieldReference (metaDataB)
   * @param mode The context in which the write is occuring
   * @return The value that was replaced by the write.
   */
  @Inline
  public final Word performRawWriteInBarrierAtomic(
                                           ObjectReference ref, Address slot,
                                           Word rawTarget, Offset offset,
                                           int locationMetadata, int mode) {
    Object obj = ref.toObject();
    Word oldValue;
    do {
      oldValue = Magic.prepareWord(obj, offset);
    } while (!Magic.attemptWord(obj, offset, oldValue, rawTarget));
    return oldValue;
  }

  /**
   * Attempt an atomic compare and exchange in a write barrier sequence.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param old The old reference to be swapped out
   * @param target The value that the slot will be updated to
   * @param offset The offset from the ref (metaDataA)
   * @param locationMetadata An index of the FieldReference (metaDataB)
   * @param mode The context in which the write is occuring
   * @return True if the compare and swap was successful
   */
  @Inline
  public final boolean tryCompareAndSwapWriteInBarrier(ObjectReference ref, Address slot,
                                                       ObjectReference old, ObjectReference target,
                                                       Offset offset, int locationMetadata, int mode) {
    Object oldValue;
    do {
      oldValue = Magic.prepareObject(ref, offset);
      if (oldValue != old) return false;
    } while (!Magic.attemptObject(ref, offset, oldValue, target));
    return true;
  }


  /**
   * Attempt an atomic compare and exchange in a write barrier sequence.
   *
   * @param ref The object that has the reference field
   * @param slot The slot that holds the reference
   * @param old The old reference to be swapped out
   * @param target The value that the slot will be updated to
   * @param offset The offset from the ref (metaDataA)
   * @param locationMetadata An index of the FieldReference (metaDataB)
   * @param mode The context in which the write is occuring
   * @return True if the compare and swap was successful
   */
  @Inline
  public final boolean tryRawCompareAndSwapWriteInBarrier(ObjectReference ref, Address slot,
                                                          Word rawOld, Word rawTarget, Offset offset,
                                                          int locationMetadata, int mode) {
    do {
      Word currentValue = Magic.prepareWord(ref, offset);
      if (currentValue != rawOld) return false;
    } while (!Magic.attemptObject(ref, offset, rawOld, rawTarget));
    return true;
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
  @UninterruptibleNoWarn
  public final void setArrayNoBarrier(Object [] dst, int index, Object value) {
    if (org.jikesrvm.VM.runningVM) {
      Address base = ObjectReference.fromObject(dst).toAddress();
      Address slot = base.plus(Offset.fromIntZeroExtend(index << LOG_BYTES_IN_ADDRESS));
      VM.activePlan.collector().storeObjectReference(slot, ObjectReference.fromObject(value));
    } else {
      dst[index] = value;
    }
  }
}
