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
   * Perform the actual write of a boolean write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void booleanWrite(ObjectReference ref, boolean value, Word slot, Word unused, int mode) {
    slot.toAddress().store((byte) (value ? 1 : 0));
  }

  /**
   * Perform the actual read of a boolean read barrier.
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public boolean booleanRead(ObjectReference ref, Word slot, Word unused, int mode) {
    return slot.toAddress().loadByte() != 0;
  }

  /**
   * Perform the actual write of a byte write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void byteWrite(ObjectReference ref, byte value, Word slot, Word unused, int mode) {
    slot.toAddress().store(value);
  }

  /**
   * Perform the actual read of a byte read barrier.
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public byte byteRead(ObjectReference ref, Word slot, Word unused, int mode) {
    return slot.toAddress().loadByte();
  }

  /**
   * Perform the actual write of a char write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void charWrite(ObjectReference ref, char value, Word slot, Word unused, int mode) {
    slot.toAddress().store(value);
  }

  /**
   * Perform the actual read of a char read barrier.
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public char charRead(ObjectReference ref, Word slot, Word unused, int mode) {
    return slot.toAddress().loadChar();
  }

  /**
   * Perform the actual write of a short write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void shortWrite(ObjectReference ref, short value, Word slot, Word unused, int mode) {
    slot.toAddress().store(value);
  }

  /**
   * Perform the actual read of a short read barrier.
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public short shortRead(ObjectReference ref, Word slot, Word unused, int mode) {
    return slot.toAddress().loadShort();
  }

  /**
   * Perform the actual write of a int write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void intWrite(ObjectReference ref, int value, Word slot, Word unused, int mode) {
    slot.toAddress().store(value);
  }

  /**
   * Perform the actual read of a int read barrier.
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public int intRead(ObjectReference ref, Word slot, Word unused, int mode) {
    return slot.toAddress().loadInt();
  }

  /**
   * Attempt an atomic compare and exchange in a write barrier sequence.
   *
   * @param objref The object that has the int field
   * @param old The old int to be swapped out
   * @param value the new int
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return True if the compare and swap was successful
   */
  @Override
  public boolean intTryCompareAndSwap(ObjectReference objref, int old,
      int value, Word metaDataA, Word metaDataB, int mode) {
    // TODO - implement
    throw new UnsupportedOperationException();
  }

  /**
   * Perform the actual write of a long write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void longWrite(ObjectReference ref, long value, Word slot, Word unused, int mode) {
    slot.toAddress().store(value);
  }

  /**
   * Perform the actual read of a long read barrier.
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public long longRead(ObjectReference ref, Word slot, Word unused, int mode) {
    return slot.toAddress().loadLong();
  }

  /**
   * Attempt an atomic compare and exchange in a write barrier sequence.
   *
   * @param objref The object that has the long field
   * @param old The old long to be swapped out
   * @param value the new long
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return True if the compare and swap was successful
   */
  @Override
  public boolean longTryCompareAndSwap(ObjectReference objref, long old,
      long value, Word metaDataA, Word metaDataB, int mode) {
    // TODO - implement
    throw new UnsupportedOperationException();
  }

  /**
   * Perform the actual write of a float write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void floatWrite(ObjectReference ref, float value, Word slot, Word unused, int mode) {
    slot.toAddress().store(value);
  }

  /**
   * Perform the actual read of a float read barrier.
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public float floatRead(ObjectReference ref, Word slot, Word unused, int mode) {
    return slot.toAddress().loadFloat();
  }

  /**
   * Perform the actual write of a double write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void doubleWrite(ObjectReference ref, double value, Word slot, Word unused, int mode) {
    slot.toAddress().store(value);
  }

  /**
   * Perform the actual read of a double read barrier.
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public double doubleRead(ObjectReference ref, Word slot, Word unused, int mode) {
    return slot.toAddress().loadDouble();
  }

  /**
   * Perform the actual write of an object reference write barrier.
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void objectReferenceWrite(ObjectReference ref, ObjectReference value, Word slot, Word unused, int mode) {
    slot.toAddress().store(value);
  }

  /**
   * Perform the actual read of an object reference read barrier.
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
    assert unused == null;
    return slot.toAddress().loadWord();
  }

  /**
   * Perform the actual write of the write barrier, writing the value as a raw Address.
   *
   * @param ref The object that has the Address field
   * @param target The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   */
  @Override
  public void addressWrite(ObjectReference ref, Address target, Word metaDataA,
      Word metaDataB, int mode) {
    // TODO - implement
    throw new UnsupportedOperationException();
  }

  /**
   * Perform the actual read of the read barrier, returning the value as a raw Address.
   *
   * @param ref The object that has the Address field
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public Address addressRead(ObjectReference ref, Word metaDataA,
      Word metaDataB, int mode) {
    // TODO - implement
    throw new UnsupportedOperationException();
  }

  /**
   * Attempt an atomic compare and exchange in a write barrier sequence.
   *
   * @param ref The object that has the Address field
   * @param old The old address to be swapped out
   * @param target The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return True if the compare and swap was successful
   */
  @Override
  public boolean addressTryCompareAndSwap(ObjectReference ref, Address old,
      Address target, Word metaDataA, Word metaDataB, int mode) {
    // TODO - implement
    throw new UnsupportedOperationException();
  }

  /**
   * Perform the actual write of the write barrier, writing the value as a raw Offset.
   *
   * @param ref The object that has the Offset field
   * @param target The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   */
  @Override
  public void offsetWrite(ObjectReference ref, Offset target, Word metaDataA,
      Word metaDataB, int mode) {
    // TODO - implement
    throw new UnsupportedOperationException();
  }

  /**
   * Perform the actual read of the read barrier, returning the value as a raw Offset.
   *
   * @param ref The object that has the Offset field
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public Offset offsetRead(ObjectReference ref, Word metaDataA, Word metaDataB,
      int mode) {
    // TODO - implement
    throw new UnsupportedOperationException();
  }

  /**
   * Perform the actual write of the write barrier, writing the value as a raw Extent.
   *
   * @param ref The object that has the Extent field
   * @param target The value that the slot will be updated to
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   */
  @Override
  public void extentWrite(ObjectReference ref, Extent target, Word metaDataA,
      Word metaDataB, int mode) {
    // TODO - implement
    throw new UnsupportedOperationException();
  }

  /**
   * Perform the actual read of the read barrier, returning the value as a raw Extent.
   *
   * @param ref The object that has the Extent field
   * @param metaDataA Opaque, VM-specific, meta-data identifying the slot
   * @param metaDataB Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public Extent extentRead(ObjectReference ref, Word metaDataA, Word metaDataB,
      int mode) {
    // TODO - implement
    throw new UnsupportedOperationException();
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
