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
package org.jikesrvm.mm.mmtk;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.runtime.Magic;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

@Uninterruptible
public class Barriers extends org.mmtk.vm.Barriers implements SizeConstants {
  /**
   * Perform the actual write of a boolean write barrier.
   *
   * @param objref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Inline
  @Override
  public final void booleanWrite(ObjectReference objref, boolean value, Word offset, Word location, int mode) {
    Magic.setByteAtOffset(objref.toObject(), offset.toOffset(), (byte) (value ? 1 : 0));
  }

  /**
   * Perform the actual read of a boolean read barrier.
   *
   * @param objref The object that has the reference field
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Inline
  @Override
  public final boolean booleanRead(ObjectReference objref, Word offset, Word location, int mode) {
    return Magic.getByteAtOffset(objref.toObject(), offset.toOffset()) == 0;
  }

  /**
   * Perform the actual write of a byte write barrier.
   *
   * @param objref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Inline
  @Override
  public final void byteWrite(ObjectReference objref, byte value, Word offset, Word location, int mode) {
    Magic.setByteAtOffset(objref.toObject(), offset.toOffset(), value);
  }

  /**
   * Perform the actual read of a byte read barrier.
   *
   * @param objref The object that has the reference field
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Inline
  @Override
  public final byte byteRead(ObjectReference objref, Word offset, Word location, int mode) {
    return Magic.getByteAtOffset(objref.toObject(), offset.toOffset());
  }

  /**
   * Perform the actual write of a char write barrier.
   *
   * @param objref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Inline
  @Override
  public final void charWrite(ObjectReference objref, char value, Word offset, Word location, int mode) {
    Magic.setCharAtOffset(objref.toObject(), offset.toOffset(), value);
  }

  /**
   * Perform the actual read of a char read barrier.
   *
   * @param objref The object that has the reference field
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Inline
  @Override
  public final char charRead(ObjectReference objref, Word offset, Word location, int mode) {
    return Magic.getCharAtOffset(objref.toObject(), offset.toOffset());
  }

  /**
   * Perform the actual write of a short write barrier.
   *
   * @param objref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Inline
  @Override
  public final void shortWrite(ObjectReference objref, short value, Word offset, Word location, int mode) {
    Magic.setShortAtOffset(objref.toObject(), offset.toOffset(), value);
  }

  /**
   * Perform the actual read of a short read barrier.
   *
   * @param objref The object that has the reference field
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Inline
  @Override
  public final short shortRead(ObjectReference objref, Word offset, Word location, int mode) {
    return Magic.getShortAtOffset(objref.toObject(), offset.toOffset());
  }

  /**
   * Perform the actual write of a int write barrier.
   *
   * @param objref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Inline
  @Override
  public final void intWrite(ObjectReference objref, int value, Word offset, Word location, int mode) {
    Magic.setIntAtOffset(objref.toObject(), offset.toOffset(), value);
  }

  /**
   * Perform the actual read of a int read barrier.
   *
   * @param objref The object that has the reference field
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Inline
  @Override
  public final int intRead(ObjectReference objref, Word offset, Word location, int mode) {
    return Magic.getIntAtOffset(objref.toObject(), offset.toOffset());
  }

  /**
   * Perform the actual write of a long write barrier.
   *
   * @param objref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Inline
  @Override
  public final void longWrite(ObjectReference objref, long value, Word offset, Word location, int mode) {
    Magic.setLongAtOffset(objref.toObject(), offset.toOffset(), value);
  }

  /**
   * Perform the actual read of a long read barrier.
   *
   * @param objref The object that has the reference field
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Inline
  @Override
  public final long longRead(ObjectReference objref, Word offset, Word location, int mode) {
    return Magic.getLongAtOffset(objref.toObject(), offset.toOffset());
  }

  /**
   * Perform the actual write of a float write barrier.
   *
   * @param objref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Inline
  @Override
  public final void floatWrite(ObjectReference objref, float value, Word offset, Word location, int mode) {
    Magic.setFloatAtOffset(objref.toObject(), offset.toOffset(), value);
  }

  /**
   * Perform the actual read of a float read barrier.
   *
   * @param objref The object that has the reference field
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Inline
  @Override
  public final float floatRead(ObjectReference objref, Word offset, Word location, int mode) {
    return Magic.getFloatAtOffset(objref.toObject(), offset.toOffset());
  }

  /**
   * Perform the actual write of a double write barrier.
   *
   * @param objref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Inline
  @Override
  public final void doubleWrite(ObjectReference objref, double value, Word offset, Word location, int mode) {
    Magic.setDoubleAtOffset(objref.toObject(), offset.toOffset(), value);
  }

  /**
   * Perform the actual read of a double read barrier.
   *
   * @param objref The object that has the reference field
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Inline
  @Override
  public final double doubleRead(ObjectReference objref, Word offset, Word location, int mode) {
    return Magic.getDoubleAtOffset(objref.toObject(), offset.toOffset());
  }

  /**
   * Perform the actual write of an object reference write barrier.
   *
   * @param objref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param offset The offset from the ref
   * @param location The index of the FieldReference
   * @param mode The context in which the write is occurring
   */
  @Inline
  @Override
  public final void objectReferenceWrite(ObjectReference objref, ObjectReference value, Word offset, Word location, int mode) {
    Magic.setObjectAtOffset(objref.toObject(), offset.toOffset(), value.toObject(), location.toInt());
  }

  /**
   * Perform the actual read of an object reference read barrier.
   *
   * @param objref The object that has the reference field
   * @param offset The offset from the ref
   * @param location The index of the FieldReference
   * @param mode The context in which the write is occurring
   * @return the object reference read value
   */
  @Inline
  @Override
  public final ObjectReference objectReferenceRead(ObjectReference objref, Word offset, Word location, int mode) {
    return ObjectReference.fromObject(Magic.getObjectAtOffset(objref.toObject(), offset.toOffset(), location.toInt()));
  }

  /**
   * Perform the actual write of the non-heap write barrier.  This is
   * used when the store is not to an object, but to a non-heap location
   * such as statics or the stack.
   * @param target The value that the slot will be updated to
   * @param unusedA The offset from the ref
   * @param unusedB Unused
   * @param ref The object that has the reference field
   */
  @Inline
  @Override
  public final void objectReferenceNonHeapWrite(Address slot, ObjectReference target, Word unusedA, Word unusedB) {
    slot.store(target);
  }

  /**
   * Atomically write a reference field of an object or array and return
   * the old value of the reference field.
   *
   * @param objref The object that has the reference field
   * @param target The value that the slot will be updated to
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return The value that was replaced by the write.
   */
  @Inline
  @Override
  public final ObjectReference objectReferenceAtomicWrite(ObjectReference objref, ObjectReference target, Word offset, Word unused, int mode) {
    Object obj = objref.toObject();
    Object newObject = target.toObject();
    Object oldObject;
    do {
      oldObject = Magic.prepareObject(obj, offset.toOffset());
    } while (!Magic.attemptObject(obj, offset.toOffset(), oldObject, newObject));
    return ObjectReference.fromObject(oldObject);
  }

  /**
   * Attempt an atomic compare and exchange in a write barrier sequence.
   *
   * @param objref The object that has the reference field
   * @param old The old reference to be swapped out
   * @param target The value that the slot will be updated to
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return True if the compare and swap was successful
   */
  @Inline
  @Override
  public final boolean objectReferenceTryCompareAndSwap(ObjectReference objref, ObjectReference old, ObjectReference target, Word offset, Word unused, int mode) {
    Object oldValue;
    do {
      oldValue = Magic.prepareObject(objref, offset.toOffset());
      if (oldValue != old) return false;
    } while (!Magic.attemptObject(objref, offset.toOffset(), oldValue, target));
    return true;
  }

  /**
   * Perform the actual write of the write barrier, writing the value as a raw word.
   *
   * @param ref The object that has the reference field
   * @param target The value that the slot will be updated to
   * @param offset The offset from the ref
   * @param location The index of the FieldReference
   * @param mode The context in which the write is occurring
   */
  @Inline
  @Override
  public final void wordWrite(ObjectReference ref, Word target,
      Word offset, Word location, int mode) {
    Magic.setWordAtOffset(ref.toObject(), offset.toOffset(), target, location.toInt());
  }

  /**
   * Atomically write a raw reference field of an object or array and return
   * the old value of the reference field.
   *
   * @param ref The object that has the reference field
   * @param target The value that the slot will be updated to
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return The value that was replaced by the write.
   */
  @Inline
  @Override
  public final Word wordAtomicWrite(ObjectReference ref, Word target,
      Word offset, Word unused, int mode) {
    Word oldValue;
    do {
      oldValue = Magic.prepareWord(ref.toObject(), offset.toOffset());
    } while (!Magic.attemptWord(ref.toObject(), offset.toOffset(), oldValue, target));
    return oldValue;
  }

  /**
   * Attempt an atomic compare and exchange in a write barrier sequence.
   *
   * @param ref The object that has the reference field
   * @param old The old reference to be swapped out
   * @param target The value that the slot will be updated to
   * @param offset The offset from the ref
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return True if the compare and swap was successful
   */
  @Inline
  @Override
  public final boolean wordTryCompareAndSwap(ObjectReference ref, Word old, Word target,
      Word offset, Word unused, int mode) {
    do {
      Word currentValue = Magic.prepareWord(ref, offset.toOffset());
      if (currentValue != old) return false;
    } while (!Magic.attemptObject(ref, offset.toOffset(), old, target));
    return true;
  }

  /**
   * Perform the actual read of the read barrier, returning the value as a raw word.
   *
   * @param ref The object that has the reference field
   * @param offset The offset from the ref
   * @param location The index of the FieldReference
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Inline
  @Override
  public final Word wordRead(ObjectReference ref,
        Word offset, Word location, int mode) {
    return Magic.getWordAtOffset(ref.toObject(), offset.toOffset(), location.toInt());
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
  @Override
  public final void objectArrayStoreNoGCBarrier(Object[] dst, int index, Object value) {
    if (org.jikesrvm.VM.runningVM) {
      Address base = ObjectReference.fromObject(dst).toAddress();
      Address slot = base.plus(Offset.fromIntZeroExtend(index << LOG_BYTES_IN_ADDRESS));
      VM.activePlan.global().storeObjectReference(slot, ObjectReference.fromObject(value));
    } else {
      dst[index] = value;
    }
  }
}
