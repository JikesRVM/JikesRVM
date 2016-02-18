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
package org.vmmagic.unboxed;

import org.vmmagic.Unboxed;
import org.vmmagic.pragma.RawStorage;
import org.vmmagic.unboxed.harness.ArchitecturalWord;
import org.vmmagic.unboxed.harness.SimulatedMemory;

/**
 * <b>Stub</b> implementation of an Address type, intended only to
 * allow the core of MMTk to be compiled.  This <b>must</b> be
 * replaced with a concrete implementation appropriate to a specific
 * VM.
 * <p>
 * The address type is used by the runtime system and collector to
 * denote machine addresses.  We use a separate type instead of the
 * Java int type for coding clarity,  machine-portability (it can map
 * to 32 bit and 64 bit integral types), and access to unsigned
 * operations (Java does not have unsigned int types).
 */
@Unboxed
@RawStorage(lengthInWords = true, length = 1)
public final class Address {

  /****************************************************************************
   *
   * Special values
   */

  /**
   *
   */
  private final ArchitecturalWord value;

  Address(ArchitecturalWord value) {
    this.value = value;
  }

  /**
   * Return an <code>Address</code> instance that reflects the value
   * zero.
   *
   * @return An address instance that reflects the value zero.
   */
  public static Address zero() {
    return new Address(ArchitecturalWord.fromLong(0));
  }

  /**
   * Return <code>true</code> if this instance is zero.
   *
   * @return <code>true</code> if this instance is zero.
   */
  public boolean isZero() {
    return value.isZero();
  }

  /**
   * Return an <code>Address</code> instance that reflects the maximum
   * allowable <code>Address</code> value.
   *
   * @return An <code>Address</code> instance that reflects the
   * maximum allowable <code>Address</code> value.
   */
  public static Address max() {
    return new Address(ArchitecturalWord.fromLong(0xFFFFFFFF));
  }

  /**
   * Return <code>true</code> if this instance is the maximum
   * allowable <code>Address</code> value.
   *
   * @return <code>true</code> if this instance is the maximum
   * allowable <code>Address</code> valu.
   */
  public boolean isMax() {
    return value.isMax();
  }

  /****************************************************************************
   *
   * Conversions
   */

  /**
   * Fabricate an <code>Address</code> instance from an integer, after
   * sign extending the integer.
   *
   * @param address the integer from which to create an <code>Address</code>
   *          instance
   * @return An address instance
   */
  public static Address fromIntSignExtend(int address) {
    return new Address(ArchitecturalWord.fromIntSignExtend(address));
  }

  /**
   * Fabricate an <code>Address</code> instance from an integer, after
   * zero extending the integer.
   *
   * @param address the integer from which to create an <code>Address</code>
   *          instance
   * @return An address instance
   */
  public static Address fromIntZeroExtend(int address) {
    return new Address(ArchitecturalWord.fromIntZeroExtend(address));
  }

  /**
   * Fabricate an <code>Address</code> instance from an integer
   *
   * @param address the integer from which to create an <code>Address</code>
   *          instance
   * @return An address instance
   */
  public static Address fromLong(long address) {
    return new Address(ArchitecturalWord.fromLong(address));
  }

  /**
   * Fabricate an <code>ObjectReference</code> instance from an
   * <code>Address</code> instance.  It is the user's responsibility
   * to ensure that the <code>Address</code> is suitable (i.e. it
   * points to the object header, or satisfies any other VM-specific
   * requirement for such a conversion).
   *
   * @return An <code>ObjectReference</code> instance.
   */
  public ObjectReference toObjectReference() {
    return new ObjectReference(value);
  }

  /**
   * Return an integer that reflects the value of this
   * <code>Address</code> instance.
   *
   * @return An integer that reflects the value of this
   * <code>Address</code> instance.
   */
  public int toInt() {
    return value.toInt();
  }

  /**
   * Return an long that reflects the value of this
   * <code>Address</code> instance.
   *
   * @return An long that reflects the value of this
   * <code>Address</code> instance.
   */
  public long toLong() {
    return value.toLongZeroExtend();
  }

  /**
   * Return a <code>Word</code> instance that reflects the value of
   * this <code>Address</code> instance.
   *
   * @return A <code>Word</code> instance that reflects the value of
   * this <code>Address</code> instance.
   */
  public Word toWord() {
    return new Word(value);
  }

  /****************************************************************************
   *
   * Arithmetic operators
   */

  /**
   * Add an integer to this <code>Address</code>, and return the sum.
   *
   * @param  v the value to be added to this <code>Address</code>
   * @return An <code>Address</code> instance that reflects the result
   * of the addition.
   */
  public Address plus(int v) {
    return new Address(value.plus(v));
  }

  /**
   * Add an <code>Offset</code> to this <code>Address</code>, and
   * return the sum.
   *
   * @param offset the <code>Offset</code> to be added to the address
   * @return An <code>Address</code> instance that reflects the result
   * of the addition.
   */
  public Address plus(Offset offset) {
    return new Address(value.plus(offset.toLong()));
  }

  /**
   * Add an <code>Extent</code> to this <code>Address</code>, and
   * return the sum.
   *
   * @param extent the <code>Extent</code> to be added to this
   * <code>Address</code>
   * @return An <code>Address</code> instance that reflects the result
   * of the addition.
   */
  public Address plus(Extent extent) {
    return new Address(value.plus(extent.toLong()));
  }

  /**
   * Subtract an integer from this <code>Address</code>, and return
   * the result.
   *
   * @param v the integer to be subtracted from this
   * <code>Address</code>.
   * @return An <code>Address</code> instance that reflects the result
   * of the subtraction.
   */
  public Address minus(int v) {
    return new Address(value.minus(v));// TODO 64-bit
  }

  /**
   * Subtract an <code>Offset</code> from this <code>Address</code>, and
   * return the result.
   *
   * @param offset the <code>Offset</code> to be subtracted from this
   *          <code>Address</code>.
   * @return An <code>Address</code> instance that reflects the result
   * of the subtraction.
   */
  public Address minus(Offset offset) {
    return new Address(value.minus(offset.toLong()));// TODO 64-bit
  }

  /**
   * Subtract an <code>Extent</code> from this <code>Address</code>, and
   * return the result.
   *
   * @param extent the <code>Extent</code> to be subtracted from this
   *          <code>Address</code>.
   * @return An <code>Address</code> instance that reflects the result
   * of the subtraction.
   */
  public Address minus(Extent extent) {
    return new Address(value.minus(extent.toLong()));
  }

  /**
   * Compute the difference between two <code>Address</code>es and
   * return the result.
   *
   * @param addr2 the <code>Address</code> to be subtracted from this
   *          <code>Address</code>.
   * @return An <code>Offset</code> instance that reflects the result
   * of the subtraction.
   */
  public Offset diff(Address addr2) {
    return new Offset(value.diff(addr2.value));
  }

  /****************************************************************************
   *
   * Boolean operators
   */

  /**
   * Return true if this <code>Address</code> instance is <i>less
   * than</i> <code>addr2</code>.
   *
   * @param addr2 the <code>Address</code> to be compared to this
   *          <code>Address</code>.
   * @return true if this <code>Address</code> instance is <i>less
   * than</i> <code>addr2</code>.
   */
  public boolean LT(Address addr2) {
    return value.LT(addr2.value);
  }

  /**
   * Return true if this <code>Address</code> instance is <i>less
   * than or equal to</i> <code>addr2</code>.
   *
   * @param addr2 the <code>Address</code> to be compared to this
   *          <code>Address</code>.
   * @return true if this <code>Address</code> instance is <i>less
   * than or equal to</i> <code>addr2</code>.
   */
  public boolean LE(Address addr2) {
    return value.LE(addr2.value);
  }

  /**
   * Return true if this <code>Address</code> instance is <i>greater
   * than</i> <code>addr2</code>.
   *
   * @param addr2 the <code>Address</code> to be compared to this
   *          <code>Address</code>.
   * @return true if this <code>Address</code> instance is <i>greater
   * than</i> <code>addr2</code>.
   */
  public boolean GT(Address addr2) {
    return value.GT(addr2.value);
  }

  /**
   * Return true if this <code>Address</code> instance is <i>greater
   * than or equal to</i> <code>addr2</code>.
   *
   * @param addr2 the <code>Address</code> to be compared to this
   *          <code>Address</code>.
   * @return true if this <code>Address</code> instance is <i>greater
   * than or equal to</i> <code>addr2</code>.
   */
  public boolean GE(Address addr2) {
    return value.GE(addr2.value);
  }

  /**
   * Return true if this <code>Address</code> instance is <i>equal
   * to</i> <code>addr2</code>.
   *
   * @param addr2 the <code>Address</code> to be compared to this
   *          <code>Address</code>.
   * @return true if this <code>Address</code> instance is <i>equal
   * to</i> <code>addr2</code>.
   */
  public boolean EQ(Address addr2) {
    return value.EQ(addr2.value);
  }

  /**
   * Return true if this <code>Address</code> instance is <i>not equal
   * to</i> <code>addr2</code>.
   *
   * @param addr2 the <code>Address</code> to be compared to this
   *          <code>Address</code>.
   * @return true if this <code>Address</code> instance is <i>not
   * equal to</i> <code>addr2</code>.
   */
  public boolean NE(Address addr2) {
    return value.NE(addr2.value);
  }

  /****************************************************************************
   *
   * Software prefetch operators etc
   */

  /**
   * Prefetch a cache-line, architecture-independent
   */
  public void prefetch() {
  }

  /****************************************************************************
   *
   * Memory access operators
   */

  /**
   * Loads a reference from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public ObjectReference loadObjectReference() {
    return new ObjectReference(loadArchitecturalWord());
  }

  /**
   * Loads a reference from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public ObjectReference loadObjectReference(Offset offset) {
    return new ObjectReference(loadArchitecturalWord(offset));
  }

  /**
   * Loads a byte from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public byte loadByte() {
    return SimulatedMemory.getByte(this);
  }

  /**
   * Loads a byte from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public byte loadByte(Offset offset) {
    return this.plus(offset).loadByte();
  }

  /**
   * Loads a char from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public char loadChar() {
    return SimulatedMemory.getChar(this);
  }

  /**
   * Loads a char from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public char loadChar(Offset offset) {
    return this.plus(offset).loadChar();
  }

  /**
   * Loads a short from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public short loadShort() {
    return SimulatedMemory.getShort(this);
  }

  /**
   * Loads a short from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public short loadShort(Offset offset) {
    return this.plus(offset).loadShort();
  }

  /**
   * Loads a float from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public float loadFloat() {
    return SimulatedMemory.getFloat(this);
  }

  /**
   * Loads a float from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public float loadFloat(Offset offset) {
    return this.plus(offset).loadFloat();
  }

  /**
   * Loads an int from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public int loadInt() {
    return SimulatedMemory.getInt(this);
  }

  /**
   * Loads an int from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public int loadInt(Offset offset) {
    return this.plus(offset).loadInt();
  }


  /**
   * Loads a long from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public long loadLong() {
    return SimulatedMemory.getLong(this);
  }

  /**
   * Loads a long from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public long loadLong(Offset offset) {
    return this.plus(offset).loadLong();
  }

  /**
   * Loads a double from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public double loadDouble() {
    return SimulatedMemory.getDouble(this);
  }

  /**
   * Loads a double from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public double loadDouble(Offset offset) {
    return this.plus(offset).loadDouble();
  }

  /**
   * Loads a word value from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read word value.
   */
  private ArchitecturalWord loadArchitecturalWord() {
    return SimulatedMemory.getWord(this);
  }

  /**
   * Loads a word value from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read word value.
   */
  private ArchitecturalWord loadArchitecturalWord(Offset offset) {
    return SimulatedMemory.getWord(this.plus(offset));
  }

  /**
   * Loads an address value from the memory location pointed to by the
   * current instance.
   *
   * @return the read address value.
   */
  public Address loadAddress() {
    return new Address(loadArchitecturalWord());
  }

  /**
   * Loads an address value from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read address value.
   */
  public Address loadAddress(Offset offset) {
    return new Address(loadArchitecturalWord(offset));
  }

  /**
   * Loads a word value from the memory location pointed to by the
   * current instance.
   *
   * @return the read word value.
   */
  public Word loadWord() {
    return new Word(loadArchitecturalWord());
  }

  /**
   * Loads a word value from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read word value.
   */
  public Word loadWord(Offset offset) {
    return new Word(loadArchitecturalWord(offset));
  }

  /**
   * Loads an offset value from the memory location pointed to by the
   * current instance.
   *
   * @return the read word value.
   */
  public Offset loadOffset() {
    return new Offset(loadArchitecturalWord());
  }

  /**
   * Loads an offset value from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read word value.
   */
  public Offset loadOffset(Offset offset) {
    return new Offset(loadArchitecturalWord(offset));
  }

  /**
   * Loads an extent value from the memory location pointed to by the
   * current instance.
   *
   * @return the read word value.
   */
  public Extent loadExtent() {
    return new Extent(loadArchitecturalWord());
  }

  /**
   * Loads an extent value from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read word value.
   */
  public Extent loadExtent(Offset offset) {
    return new Extent(loadArchitecturalWord(offset));
  }

  /**
   * Stores the address value in the memory location pointed to by the
   * current instance.
   *
   * @param val The address value to store.
   */
  public void store(ObjectReference val) {
    SimulatedMemory.setWord(this, val.value);
  }

  /**
   * Stores the object reference value in the memory location pointed
   * to by the current instance.
   *
   * @param val The object reference value to store.
   * @param offset the offset to the value.
   */
  public void store(ObjectReference val, Offset offset) {
    this.plus(offset).store(val);
  }

  /**
   * Stores the address value in the memory location pointed to by the
   * current instance.
   *
   * @param val The address value to store.
   */
  public void store(Address val) {
    SimulatedMemory.setWord(this, val.value);
  }

  /**
   * Stores the address value in the memory location pointed to by the
   * current instance.
   *
   * @param val The address value to store.
   * @param offset the offset to the value.
   */
  public void store(Address val, Offset offset) {
    this.plus(offset).store(val);
  }

  /**
   * Stores the address value in the memory location pointed to by the
   * current instance.
   *
   * @param val The address value to store.
   */
  public void store(Offset val) {
    SimulatedMemory.setWord(this, val.value);
  }

  /**
   * Stores the address value in the memory location pointed to by the
   * current instance.
   *
   * @param val The address value to store.
   * @param offset the offset to the value.
   */
  public void store(Extent val, Offset offset) {
    this.plus(offset).store(val);
  }

  /**
   * Stores the address value in the memory location pointed to by the
   * current instance.
   *
   * @param val The address value to store.
   */
  public void store(Extent val) {
    SimulatedMemory.setWord(this, val.value);
  }

  /**
   * Stores the address value in the memory location pointed to by the
   * current instance.
   *
   * @param val The address value to store.
   * @param offset the offset to the value.
   */
  public void store(Offset val, Offset offset) {
    this.plus(offset).store(val);
  }

  /**
   * Stores the float value in the memory location pointed to by the
   * current instance.
   *
   * @param val The float value to store.
   */
  public void store(float val) {
    SimulatedMemory.setFloat(this, val);
  }

  /**
   * Stores the float value in the memory location pointed to by the
   * current instance.
   *
   * @param val The float value to store.
   * @param offset the offset to the value.
   */
  public void store(float val, Offset offset) {
    this.plus(offset).store(val);
  }

  /**
   * Stores the word value in the memory location pointed to by the
   * current instance.
   *
   * @param val The word value to store.
   */
  public void store(Word val) {
    SimulatedMemory.setWord(this, val.value);
  }

  /**
   * Stores the word value in the memory location pointed to by the
   * current instance.
   *
   * @param val The word value to store.
   * @param offset the offset to the value.
   */
  public void store(Word val, Offset offset) {
    this.plus(offset).store(val);
  }

  /**
   * Stores the byte value in the memory location pointed to by the
   * current instance.
   *
   * @param val The byte value to store.
   */
  public void store(byte val) {
    SimulatedMemory.setByte(this, val);
  }

  /**
   * Stores the byte value in the memory location pointed to by the
   * current instance.
   *
   * @param val The byte value to store.
   * @param offset the offset to the value.
   */
  public void store(byte val, Offset offset) {
    this.plus(offset).store(val);
  }


  /**
   * Stores an int value in memory location pointed to by the
   * current instance.
   *
   * @param val The int value to store.
   */
  public void store(int val) {
    SimulatedMemory.setInt(this, val);
  }

  /**
   * Stores an int value in memory location pointed to by the
   * current instance.
   *
   * @param val The int value to store.
   * @param offset the offset to the value.
   */
  public void store(int val, Offset offset) {
    this.plus(offset).store(val);
  }

  /**
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param val The double value to store.
   */
  public void store(double val) {
    SimulatedMemory.setDouble(this, val);
  }

  /**
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param val The double value to store.
   * @param offset the offset to the value.
   */
  public void store(double val, Offset offset) {
    this.plus(offset).store(val);
  }


  /**
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param val The double value to store.
   */
  public void store(long val) {
    SimulatedMemory.setLong(this, val);
  }

  /**
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param val The double value to store.
   * @param offset the offset to the value.
   */
  public void store(long val, Offset offset) {
    this.plus(offset).store(val);
  }

  /**
   * Stores a char value in the memory location pointed to by the
   * current instance.
   *
   * @param val the char value to store.
   */
  public void store(char val) {
    SimulatedMemory.setChar(this, val);
  }

  /**
   * Stores a char value in the memory location pointed to by the
   * current instance.
   *
   * @param val the char value to store.
   * @param offset the offset to the value.
   */
  public void store(char val, Offset offset) {
    this.plus(offset).store(val);
  }

  /**
   * Stores a short value in the memory location pointed to by the
   * current instance.
   *
   * @param val the short value to store.
   */
  public void store(short val) {
    SimulatedMemory.setShort(this, val);
  }

  /**
   * Stores a short value in the memory location pointed to by the
   * current instance.
   *
   * @param val the short value to store.
   * @param offset the offset to the value.
   */
  public void store(short val, Offset offset) {
    this.plus(offset).store(val);
  }

  /****************************************************************************
   *
   * Atomic memory access operators (compare and swap)
   */

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @return the old value to be passed to an attempt call.
   */
  public Word prepareWord() {
    return loadWord();
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @param offset the offset to the value.
   * @return the old value to be passed to an attempt call.
   */
  public Word prepareWord(Offset offset) {
    return loadWord(offset);
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @return the old value to be passed to an attempt call.
   */
  public ObjectReference prepareObjectReference() {
    return loadObjectReference();
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @param offset the offset to the value.
   * @return the old value to be passed to an attempt call.
   */
  public ObjectReference prepareObjectReference(Offset offset) {
    return loadObjectReference(offset);
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @return the old value to be passed to an attempt call.
   */
  public Address prepareAddress() {
    return loadAddress();
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @param offset the offset to the value.
   * @return the old value to be passed to an attempt call.
   */
  public Address prepareAddress(Offset offset) {
    return loadAddress(offset);
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @return the old value to be passed to an attempt call.
   */
  public int prepareInt() {
    return loadInt();
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @param offset the offset to the value.
   * @return the old value to be passed to an attempt call.
   */
  public int prepareInt(Offset offset) {
    return loadInt(offset);
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @return the old value to be passed to an attempt call.
   */
  public long prepareLong() {
    return loadLong();
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @param offset the offset to the value.
   * @return the old value to be passed to an attempt call.
   */
  public long prepareLong(Offset offset) {
    return loadLong(offset);
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param val the new value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(int old, int val) {
    return SimulatedMemory.exchangeInt(this, old, val);
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param val the new value.
   * @param offset the offset to the value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(int old, int val, Offset offset) {
    return this.plus(offset).attempt(old,val);
  }


  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param val the new value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(long old, long val) {
    return SimulatedMemory.exchangeLong(this, old, val);
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param val the new value.
   * @param offset the offset to the value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(long old, long val, Offset offset) {
    return this.plus(offset).attempt(old,val);
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param val the new value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(Word old, Word val) {
    return SimulatedMemory.exchangeWord(this, old.value, val.value);
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param val the new value.
   * @param offset the offset to the value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(Word old, Word val, Offset offset) {
    return this.plus(offset).attempt(old,val);
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param val the new value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(ObjectReference old, ObjectReference val) {
    return SimulatedMemory.exchangeWord(this, old.value, val.value);
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param val the new value.
   * @param offset the offset to the value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(ObjectReference old, ObjectReference val, Offset offset) {
    return this.plus(offset).attempt(old,val);
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param val the new value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(Address old, Address val) {
    return SimulatedMemory.exchangeWord(this, old.value, val.value);
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param val the new value.
   * @param offset the offset to the value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(Address old, Address val, Offset offset) {
    return this.plus(offset).attempt(old,val);
  }

  /**
   * Return a string representation of this address.
   */
  @Override
  public String toString() {
    return value.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Address) {
      return EQ((Address)o);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return (value == null) ? 0 : value.hashCode();
  }


}
