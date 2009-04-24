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

/**
 * <b>Stub</b> implementation of an Address type, intended only to
 * allow the core of MMTk to be compiled.  This <b>must</b> be
 * replaced with a concrete implementation appropriate to a specific
 * VM.
 *
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
   * Return an <code>Address</code> instance that reflects the value
   * zero.
   *
   * @return An address instance that reflects the value zero.
   */
  public static Address zero() {
    return null;
  }

  /**
   * Return <code>true</code> if this instance is zero.
   *
   * @return <code>true</code> if this instance is zero.
   */
  public boolean isZero() {
    return false;
  }

  /**
   * Return an <code>Address</code> instance that reflects the maximum
   * allowable <code>Address</code> value.
   *
   * @return An <code>Address</code> instance that reflects the
   * maximum allowable <code>Address</code> value.
   */
  public static Address max() {
    return null;
  }

  /**
   * Return <code>true</code> if this instance is the maximum
   * allowable <code>Address</code> value.
   *
   * @return <code>true</code> if this instance is the maximum
   * allowable <code>Address</code> valu.
   */
  public boolean isMax() {
    return false;
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
    return null;
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
    return null;
  }

  /**
   * Fabricate an <code>Address</code> instance from an integer
   *
   * @param address the integer from which to create an <code>Address</code>
   *          instance
   * @return An address instance
   */
  public static Address fromLong(long address) {
    return null;
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
    return null;
  }

  /**
   * Return an integer that reflects the value of this
   * <code>Address</code> instance.
   *
   * @return An integer that reflects the value of this
   * <code>Address</code> instance.
   */
  public int toInt() {
    return 0;
  }

  /**
   * Return an long that reflects the value of this
   * <code>Address</code> instance.
   *
   * @return An long that reflects the value of this
   * <code>Address</code> instance.
   */
  public long toLong() {
    return 0;
  }

  /**
   * Return a <code>Word</code> instance that reflects the value of
   * this <code>Address</code> instance.
   *
   * @return A <code>Word</code> instance that reflects the value of
   * this <code>Address</code> instance.
   */
  public Word toWord() {
    return null;
  }

  /****************************************************************************
   *
   * Arithemtic operators
   */

  /**
   * Add an integer to this <code>Address</code>, and return the sum.
   *
   * @param  v the value to be added to this <code>Address</code>
   * @return An <code>Address</code> instance that reflects the result
   * of the addition.
   */
  public Address plus(int v) {
    return null;
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
    return null;
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
    return null;
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
    return null;
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
    return null;
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
    return null;
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
    return null;
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
    return false;
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
    return false;
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
    return false;
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
    return false;
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
    return false;
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
    return false;
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
    return null;
  }

  /**
   * Loads a reference from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public ObjectReference loadObjectReference(Offset offset) {
    return null;
  }

  /**
   * Loads a byte from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public byte loadByte() {
    return (byte) 0;
  }

  /**
   * Loads a byte from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public byte loadByte(Offset offset) {
    return (byte) 0;
  }

  /**
   * Loads a char from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public char loadChar() {
    return (char) 0;
  }

  /**
   * Loads a char from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public char loadChar(Offset offset) {
    return (char) 0;
  }

  /**
   * Loads a short from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public short loadShort() {
    return (short) 0;
  }

  /**
   * Loads a short from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public short loadShort(Offset offset) {
    return (short) 0;
  }

  /**
   * Loads a float from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public float loadFloat() {
    return (float) 0;
  }

  /**
   * Loads a float from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public float loadFloat(Offset offset) {
    return (float) 0;
  }

  /**
   * Loads an int from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public int loadInt() {
    return 0;
  }

  /**
   * Loads an int from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public int loadInt(Offset offset) {
    return 0;
  }


  /**
   * Loads a long from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public long loadLong() {
    return 0L;
  }

  /**
   * Loads a long from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public long loadLong(Offset offset) {
    return 0L;
  }

  /**
   * Loads a double from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public double loadDouble() {
    return 0;
  }

  /**
   * Loads a double from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public double loadDouble(Offset offset) {
    return 0;
  }


  /**
   * Loads an address value from the memory location pointed to by the
   * current instance.
   *
   * @return the read address value.
   */
  public Address loadAddress() {
    return null;
  }

  /**
   * Loads an address value from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read address value.
   */
  public Address loadAddress(Offset offset) {
    return null;
  }

  /**
   * Loads a word value from the memory location pointed to by the
   * current instance.
   *
   * @return the read word value.
   */
  public Word loadWord() {
    return null;
  }

  /**
   * Loads a word value from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read word value.
   */
  public Word loadWord(Offset offset) {
    return null;
  }

  /**
   * Stores the address value in the memory location pointed to by the
   * current instance.
   *
   * @param value The address value to store.
   */
  public void store(ObjectReference value) {
  }

  /**
   * Stores the object reference value in the memory location pointed
   * to by the current instance.
   *
   * @param value The object reference value to store.
   * @param offset the offset to the value.
   */
  public void store(ObjectReference value, Offset offset) {
  }

  /**
   * Stores the address value in the memory location pointed to by the
   * current instance.
   *
   * @param value The address value to store.
   */
  public void store(Address value) {
  }

  /**
   * Stores the address value in the memory location pointed to by the
   * current instance.
   *
   * @param value The address value to store.
   * @param offset the offset to the value.
   */
  public void store(Address value, Offset offset) {
  }

  /**
   * Stores the float value in the memory location pointed to by the
   * current instance.
   *
   * @param value The float value to store.
   */
  public void store(float value) {
  }

  /**
   * Stores the float value in the memory location pointed to by the
   * current instance.
   *
   * @param value The float value to store.
   * @param offset the offset to the value.
   */
  public void store(float value, Offset offset) {
  }

  /**
   * Stores the word value in the memory location pointed to by the
   * current instance.
   *
   * @param value The word value to store.
   */
  public void store(Word value) {
  }

  /**
   * Stores the word value in the memory location pointed to by the
   * current instance.
   *
   * @param value The word value to store.
   * @param offset the offset to the value.
   */
  public void store(Word value, Offset offset) {
  }

  /**
   * Stores the byte value in the memory location pointed to by the
   * current instance.
   *
   * @param value The byte value to store.
   */
  public void store(byte value) {
  }

  /**
   * Stores the byte value in the memory location pointed to by the
   * current instance.
   *
   * @param value The byte value to store.
   * @param offset the offset to the value.
   */
  public void store(byte value, Offset offset) {
  }


  /**
   * Stores an int value in memory location pointed to by the
   * current instance.
   *
   * @param value The int value to store.
   */
  public void store(int value) {
  }

  /**
   * Stores an int value in memory location pointed to by the
   * current instance.
   *
   * @param value The int value to store.
   * @param offset the offset to the value.
   */
  public void store(int value, Offset offset) {
  }

  /**
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param value The double value to store.
   */
  public void store(double value) {
  }

  /**
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param value The double value to store.
   * @param offset the offset to the value.
   */
  public void store(double value, Offset offset) {
  }


  /**
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param value The double value to store.
   */
  public void store(long value) {
  }

  /**
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param value The double value to store.
   * @param offset the offset to the value.
   */
  public void store(long value, Offset offset) {
  }

  /**
   * Stores a char value in the memory location pointed to by the
   * current instance.
   *
   * @param value the char value to store.
   */
  public void store(char value) {
  }

  /**
   * Stores a char value in the memory location pointed to by the
   * current instance.
   *
   * @param value the char value to store.
   * @param offset the offset to the value.
   */
  public void store(char value, Offset offset) {
  }

  /**
   * Stores a short value in the memory location pointed to by the
   * current instance.
   *
   * @param value the short value to store.
   */
  public void store(short value) {
  }

  /**
   * Stores a short value in the memory location pointed to by the
   * current instance.
   *
   * @param value the short value to store.
   * @param offset the offset to the value.
   */
  public void store(short value, Offset offset) {
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
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @param offset the offset to the value.
   * @return the old value to be passed to an attempt call.
   */
  public Word prepareWord(Offset offset) {
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @return the old value to be passed to an attempt call.
   */
  public ObjectReference prepareObjectReference() {
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @param offset the offset to the value.
   * @return the old value to be passed to an attempt call.
   */
  public ObjectReference prepareObjectReference(Offset offset) {
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @return the old value to be passed to an attempt call.
   */
  public Address prepareAddress() {
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @param offset the offset to the value.
   * @return the old value to be passed to an attempt call.
   */
  public Address prepareAddress(Offset offset) {
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @return the old value to be passed to an attempt call.
   */
  public int prepareInt() {
    return 0;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @param offset the offset to the value.
   * @return the old value to be passed to an attempt call.
   */
  public int prepareInt(Offset offset) {
    return 0;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param value the new value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(int old, int value) {
    return false;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param value the new value.
   * @param offset the offset to the value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(int old, int value, Offset offset) {
    return false;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param value the new value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(Word old, Word value) {
    return false;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param value the new value.
   * @param offset the offset to the value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(Word old, Word value, Offset offset) {
    return false;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param value the new value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(ObjectReference old, ObjectReference value) {
    return false;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param value the new value.
   * @param offset the offset to the value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(ObjectReference old, ObjectReference value,
      Offset offset) {
    return false;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param value the new value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(Address old, Address value) {
    return false;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param value the new value.
   * @param offset the offset to the value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(Address old, Address value, Offset offset) {
    return false;
  }

}
