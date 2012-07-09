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

import org.mmtk.harness.sanity.Sanity;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.*;

/**
 * MMTk Harness implementation of Barriers interface
 */
@Uninterruptible
public class Barriers extends org.mmtk.vm.Barriers {
  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void booleanWrite(ObjectReference ref, boolean value, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    slot.toAddress().store((byte) (value ? 1 : 0));
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public boolean booleanRead(ObjectReference ref, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    return slot.toAddress().loadByte() != 0;
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void byteWrite(ObjectReference ref, byte value, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    slot.toAddress().store(value);
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public byte byteRead(ObjectReference ref, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    return slot.toAddress().loadByte();
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void charWrite(ObjectReference ref, char value, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    slot.toAddress().store(value);
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public char charRead(ObjectReference ref, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    return slot.toAddress().loadChar();
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void shortWrite(ObjectReference ref, short value, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    slot.toAddress().store(value);
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public short shortRead(ObjectReference ref, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    return slot.toAddress().loadShort();
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void intWrite(ObjectReference ref, int value, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    slot.toAddress().store(value);
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public int intRead(ObjectReference ref, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    return slot.toAddress().loadInt();
  }

  /**
   * {@inheritDoc}
   *
   * @param objref The object that has the int field
   * @param old The old int to be swapped out
   * @param value the new int
   * @param slot The address of the field
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return True if the compare and swap was successful
   */
  @Override
  public boolean intTryCompareAndSwap(ObjectReference objref, int old,
      int value, Word slot, Word unused, int mode) {
    assert unused == null;
    return slot.toAddress().attempt(old, value);
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void longWrite(ObjectReference ref, long value, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    slot.toAddress().store(value);
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public long longRead(ObjectReference ref, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    return slot.toAddress().loadLong();
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the long field
   * @param old The old long to be swapped out
   * @param value the new long
   * @param slot Opaque, VM-specific, meta-data identifying the slot
   * @param unused Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return True if the compare and swap was successful
   */
  @Override
  public boolean longTryCompareAndSwap(ObjectReference ref, long old,
      long value, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    return slot.toAddress().attempt(old, value);
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void floatWrite(ObjectReference ref, float value, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    slot.toAddress().store(value);
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public float floatRead(ObjectReference ref, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    return slot.toAddress().loadFloat();
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void doubleWrite(ObjectReference ref, double value, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    slot.toAddress().store(value);
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public double doubleRead(ObjectReference ref, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    return slot.toAddress().loadDouble();
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param value The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void objectReferenceWrite(ObjectReference ref, ObjectReference value, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    slot.toAddress().store(value);
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param slot The address to be read from
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public ObjectReference objectReferenceRead(ObjectReference ref,Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    return slot.toAddress().loadObjectReference();
  }

  /**
   * {@inheritDoc}
   *
   * @param slot The address that contains the reference field
   * @param target The value that the slot will be updated to
   * @param unusedA Opaque, VM-specific, meta-data identifying the slot
   * @param unusedB Opaque, VM-specific, meta-data identifying the slot
   */
  @Override
  public void objectReferenceNonHeapWrite(Address slot, ObjectReference target, Word unusedA, Word unusedB) {
    assert unusedA == null && unusedB == null;
    Sanity.assertValid(target);
    slot.store(target);
  }

  /**
   * {@inheritDoc}
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
    assert unused == null;
    Sanity.assertValid(ref);
    ObjectReference old;
    do {
      old = slot.toAddress().prepareObjectReference();
    } while (!slot.toAddress().attempt(old, target));
    return old;
  }

  /**
   * {@inheritDoc}
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
    assert unused == null;
    Sanity.assertValid(ref);
    return slot.toAddress().attempt(old, target);
  }


  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the reference field
   * @param target The value that the slot will be updated to
   * @param slot The address to be written to
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void wordWrite(ObjectReference ref, Word target, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    slot.toAddress().store(target);
  }

  /**
   * {@inheritDoc}
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
    assert unused == null;
    Sanity.assertValid(ref);
    Word old;
    do {
      old = slot.toAddress().prepareWord();
    } while (!slot.toAddress().attempt(old, target));
    return old;
  }

  /**
   * {@inheritDoc}
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
    assert unused == null;
    Sanity.assertValid(ref);
    return slot.toAddress().attempt(old, target);
  }

  /**
   * {@inheritDoc}
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
    Sanity.assertValid(ref);
    return slot.toAddress().loadWord();
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the Address field
   * @param target The value that the slot will be updated to
   * @param slot Opaque, VM-specific, meta-data identifying the slot
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void addressWrite(ObjectReference ref, Address target, Word slot,
      Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    slot.toAddress().store(target);
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the Address field
   * @param slot Opaque, VM-specific, meta-data identifying the slot
   * @param unused Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public Address addressRead(ObjectReference ref, Word slot,
      Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    return slot.toAddress().loadAddress();
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the Address field
   * @param old The old address to be swapped out
   * @param target The value that the slot will be updated to
   * @param slot Opaque, VM-specific, meta-data identifying the slot
   * @param unused Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return True if the compare and swap was successful
   */
  @Override
  public boolean addressTryCompareAndSwap(ObjectReference ref, Address old,
      Address target, Word slot, Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    return slot.toAddress().attempt(old, target);
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the Offset field
   * @param target The value that the slot will be updated to
   * @param slot Opaque, VM-specific, meta-data identifying the slot
   * @param unused Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   */
  @Override
  public void offsetWrite(ObjectReference ref, Offset target, Word slot,
      Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    slot.toAddress().store(target);
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the Offset field
   * @param slot Opaque, VM-specific, meta-data identifying the slot
   * @param unused Opaque, VM-specific, meta-data identifying the slot
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public Offset offsetRead(ObjectReference ref, Word slot,
      Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    return slot.toAddress().loadOffset();
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the Extent field
   * @param target The value that the slot will be updated to
   * @param slot Opaque, VM-specific, meta-data identifying the slot
   * @param unused Unused
   * @param mode The context in which the write is occurring
   */
  @Override
  public void extentWrite(ObjectReference ref, Extent target, Word slot,
      Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    slot.toAddress().store(target);
  }

  /**
   * {@inheritDoc}
   *
   * @param ref The object that has the Extent field
   * @param slot Opaque, VM-specific, meta-data identifying the slot
   * @param unused Unused
   * @param mode The context in which the write is occurring
   * @return the read value
   */
  @Override
  public Extent extentRead(ObjectReference ref, Word slot,
      Word unused, int mode) {
    assert unused == null;
    Sanity.assertValid(ref);
    return slot.toAddress().loadExtent();
  }

  @Override
  public void objectArrayStoreNoGCBarrier(Object [] dst, int index, Object value) {
    dst[index] = value;
  }

}
