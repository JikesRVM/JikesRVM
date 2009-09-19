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
package org.mmtk.vm;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.*;

@Uninterruptible
public abstract class Barriers {
  /**
   * Perform the actual write of a boolean write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   */
  public abstract void booleanWrite(ObjectReference ref, boolean value, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual read of a boolean read barrier.
   *
   * @param ref The object that has the reference field
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  public abstract boolean booleanRead(ObjectReference ref, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual write of a byte write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   */
  public abstract void byteWrite(ObjectReference ref, byte value, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual read of a byte read barrier.
   *
   * @param ref The object that has the reference field
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  public abstract byte byteRead(ObjectReference ref, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual write of a char write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   */
  public abstract void charWrite(ObjectReference ref, char value, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual read of a char read barrier.
   *
   * @param ref The object that has the reference field
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  public abstract char charRead(ObjectReference ref, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual write of a short write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   */
  public abstract void shortWrite(ObjectReference ref, short value, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual read of a short read barrier.
   *
   * @param ref The object that has the reference field
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  public abstract short shortRead(ObjectReference ref, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual write of a int write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   */
  public abstract void intWrite(ObjectReference ref, int value, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual read of a int read barrier.
   *
   * @param ref The object that has the reference field
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  public abstract int intRead(ObjectReference ref, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual write of a long write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   */
  public abstract void longWrite(ObjectReference ref, long value, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual read of a long read barrier.
   *
   * @param ref The object that has the reference field
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  public abstract long longRead(ObjectReference ref, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual write of a float write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   */
  public abstract void floatWrite(ObjectReference ref, float value, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual read of a float read barrier.
   *
   * @param ref The object that has the reference field
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  public abstract float floatRead(ObjectReference ref, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual write of a double write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   */
  public abstract void doubleWrite(ObjectReference ref, double value, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual read of a double read barrier.
   *
   * @param ref The object that has the reference field
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  public abstract double doubleRead(ObjectReference ref, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual write of an object reference write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   */
  public abstract void objectReferenceWrite(ObjectReference ref, ObjectReference value, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual read of a read barrier.
   *
   * @param ref The object that has the reference field
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  public abstract ObjectReference objectReferenceRead(ObjectReference ref, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual write of the non-heap write barrier.  This is
   * used when the store is not to an object, but to a non-heap location
   * such as statics or the stack.
   *
   * @param slot The address that contains the reference field
   * @param target The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   */
  public abstract void objectReferenceNonHeapWrite(Address slot, ObjectReference target, Word metaDataA, Word metaDataB);

  /**
   * Atomically write a reference field of an object or array and return
   * the old value of the reference field.
   *
   * @param ref The object that has the reference field
   * @param target The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return The value that was replaced by the write.
   */
  public abstract ObjectReference objectReferenceAtomicWrite(ObjectReference ref, ObjectReference target, Word metaDataA, Word metaDataB, int mode);

  /**
   * Attempt an atomic compare and exchange in a write barrier sequence.
   *
   * @param ref The object that has the reference field
   * @param old The old reference to be swapped out
   * @param target The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return True if the compare and swap was successful
   */
  public abstract boolean objectReferenceTryCompareAndSwap(ObjectReference ref, ObjectReference old, ObjectReference target, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual write of the write barrier, writing the value as a raw Word.
   *
   * @param ref The object that has the reference field
   * @param target The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   */
  public abstract void wordWrite(ObjectReference ref, Word target, Word metaDataA, Word metaDataB, int mode);

  /**
   * Atomically write a reference field of an object or array and return
   * the old value of the reference field.
   *
   * @param ref The object that has the reference field
   * @param target The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return The raw value that was replaced by the write.
   */
  public abstract Word wordAtomicWrite(ObjectReference ref, Word rawTarget, Word metaDataA, Word metaDataB, int mode);

  /**
   * Attempt an atomic compare and exchange in a write barrier sequence.
   *
   * @param ref The object that has the reference field
   * @param old The old reference to be swapped out
   * @param target The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return True if the compare and swap was successful
   */
  public abstract boolean wordTryCompareAndSwap(ObjectReference ref, Word old, Word target, Word metaDataA, Word metaDataB, int mode);

  /**
   * Perform the actual read of the read barrier, returning the value as a raw Word.
   *
   * @param ref The object that has the reference field
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  public abstract Word wordRead(ObjectReference ref, Word metaDataA, Word metaDataB, int mode);

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
  public abstract void objectArrayStoreNoGCBarrier(Object [] dst, int index, Object value);
}
