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

import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

@Uninterruptible public class Barriers extends org.mmtk.vm.Barriers implements VM_SizeConstants {
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
    VM_Magic.setObjectAtOffset(obj, offset, target.toObject(), locationMetadata);
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
      oldObject = VM_Magic.prepareObject(obj, offset);
    } while (!VM_Magic.attemptObject(obj, offset, oldObject, newObject));
    return ObjectReference.fromObject(oldObject);
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
      ObjectReference old, ObjectReference target, Offset offset, int locationMetadata, int mode) {
    Object oldValue;
    do {
      oldValue = VM_Magic.prepareObject(ref, offset);
      if (oldValue != old) return false;
    } while (!VM_Magic.attemptObject(ref, offset, oldValue, target));
    return true;
  }

  /**
   * Sets an element of a object array without invoking any write
   * barrier.
   *
   * @param dst the destination array
   * @param index the index of the element to set
   * @param value the new value for the element
   */
  @Override
  public final void setArrayNoBarrier(Object [] dst, int index, Object value) {
    setArrayNoBarrierStatic(dst, index, value);
  }
  @UninterruptibleNoWarn
  public static void setArrayNoBarrierStatic(Object [] dst, int index, Object value) {
    if (VM.runningVM)
      VM_Magic.setObjectAtOffset(dst, Offset.fromIntZeroExtend(index << LOG_BYTES_IN_ADDRESS), value);
    else
      dst[index] = value;
  }

  /**
   * Sets an element of a char array without invoking any write
   * barrier.  This method is called by the Log method, as it will be
   * used during garbage collection and needs to manipulate character
   * arrays without causing a write barrier operation.
   *
   * @param dst the destination array
   * @param index the index of the element to set
   * @param value the new value for the element
   */
  public final void setArrayNoBarrier(char [] dst, int index, char value) {
    setArrayNoBarrierStatic(dst, index, value);
  }
  public static void setArrayNoBarrierStatic(char [] dst, int index, char value) {
    if (VM.runningVM)
      VM_Magic.setCharAtOffset(dst, Offset.fromIntZeroExtend(index << LOG_BYTES_IN_CHAR), value);
    else
      dst[index] = value;
  }

  /**
   * Gets an element of a char array without invoking any read barrier
   * or performing bounds check.
   *
   * @param src the source array
   * @param index the natural array index of the element to get
   * @return the new value of element
   */
  public final char getArrayNoBarrier(char [] src, int index) {
    return getArrayNoBarrierStatic(src, index);
  }
  public static char getArrayNoBarrierStatic(char [] src, int index) {
    if (VM.runningVM)
      return VM_Magic.getCharAtOffset(src, Offset.fromIntZeroExtend(index << LOG_BYTES_IN_CHAR));
    else
      return src[index];
  }

  /**
   * Gets an element of a byte array without invoking any read barrier
   * or bounds check.
   *
   * @param src the source array
   * @param index the natural array index of the element to get
   * @return the new value of element
   */
  public final byte getArrayNoBarrier(byte [] src, int index) {
    return getArrayNoBarrierStatic(src, index);
  }
  public static byte getArrayNoBarrierStatic(byte [] src, int index) {
    if (VM.runningVM)
      return VM_Magic.getByteAtOffset(src, Offset.fromIntZeroExtend(index));
    else
      return src[index];
  }

  /**
   * Gets an element of an int array without invoking any read barrier
   * or performing bounds checks.
   *
   * @param src the source array
   * @param index the natural array index of the element to get
   * @return the new value of element
   */
  public final int getArrayNoBarrier(int [] src, int index) {
    if (VM.runningVM)
      return VM_Magic.getIntAtOffset(src, Offset.fromIntZeroExtend(index<<LOG_BYTES_IN_INT));
    else
      return src[index];
  }

  /**
   * Gets an element of an Object array without invoking any read
   * barrier or performing bounds checks.
   *
   * @param src the source array
   * @param index the natural array index of the element to get
   * @return the new value of element
   */
  public final Object getArrayNoBarrier(Object [] src, int index) {
    if (VM.runningVM)
      return VM_Magic.getObjectAtOffset(src, Offset.fromIntZeroExtend(index<<LOG_BYTES_IN_ADDRESS));
    else
      return src[index];
  }


  /**
   * Gets an element of an array of byte arrays without causing the potential
   * thread switch point that array accesses normally cause.
   *
   * @param src the source array
   * @param index the index of the element to get
   * @return the new value of element
   */
  public final byte[] getArrayNoBarrier(byte[][] src, int index) {
    return getArrayNoBarrierStatic(src, index);
  }
  public static byte[] getArrayNoBarrierStatic(byte[][] src, int index) {
    if (VM.runningVM)
      return VM_Magic.addressAsByteArray(VM_Magic.objectAsAddress(VM_Magic.getObjectAtOffset(src, Offset.fromIntZeroExtend(index << LOG_BYTES_IN_ADDRESS))));
    else
      return src[index];
  }
}
