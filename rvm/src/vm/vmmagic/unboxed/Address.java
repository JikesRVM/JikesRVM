/* -*-coding: iso-8859-1 -*-
 *
 * Copyright © IBM Corp 2001, 2004
 *
 * $Id$
 */
package org.vmmagic.unboxed;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_SizeConstants;

import org.vmmagic.pragma.*;

/**
 * The {@link Address} type is used by the runtime system and collector to
 * denote machine addresses.  We use a separate type instead of the
 * Java int type for coding clarity,  machine-portability (it can map
 * to 32 bit and 64 bit integral types), and access to unsigned
 * operations (Java does not have unsigned int types).
 * <p>
 * For efficiency and to avoid meta-circularity, the Address class is
 * intercepted like magic and converted into the base type so no
 * Address object is created run-time.
 *
 * @author Perry Cheng
 * @modified Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public final class Address implements Uninterruptible, VM_SizeConstants {

  // Do not try to create a static field containing special address values.
  // Suboptimal code will be generated.

  //-#if RVM_FOR_32_ADDR
  private int value;
  //-#elif RVM_FOR_64_ADDR
  private long value;
  //-#endif

  /****************************************************************************
   *
   * Constructors
   */

  //-#if RVM_FOR_32_ADDR
  /**
   * Create an {@link Address} instance from an integer.
   */
  Address(int address) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    value = address;
  }
  //-#elif RVM_FOR_64_ADDR
  /**
   * Create an {@link Address} instance from a long.
   */
  Address(long address) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    value = address;
  }
  //-#endif


  /****************************************************************************
   *
   * Special values
   */

  /**
   * Return an {@link Address} instance that reflects the value
   * zero.
   *
   * @return An address instance that reflects the value zero.
   */
  public static Address zero() throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(0);
  }

  /**
   * Return <code>true</code> if this instance is zero.
   * 
   * @return <code>true</code> if this instance is zero. 
   */
  public boolean isZero() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return EQ(zero());
  }

  /**
   * Return an {@link Address} instance that reflects the maximum
   * allowable {@link Address} value.
   *
   * @return An {@link Address} instance that reflects the
   * maximum allowable {@link Address} value.
   */
  public static Address max() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return fromIntSignExtend(-1);
  }

  /**
   * Return <code>true</code> if this instance is the maximum allowable
   * {@link Address} value.
   * 
   * @return <code>true</code> if this instance is the maximum allowable
   * {@link Address} value.
   */
  public boolean isMax() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return EQ(max());
  }

  /****************************************************************************
   *
   * Conversions
   */

  /**
   * Fabricate an {@link Address} instance from an integer, after
   * sign extending the integer.
   *
   * @param address the integer from which to create an {@link Address}
   * instance
   * @return An address instance
   */
  public static Address fromIntSignExtend(int address) 
    throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(address);
  }

  /**
   * Fabricate an {@link Address} instance from an integer, after
   * zero extending the integer.
   *
   * @param address the integer from which to create an {@link Address}
   * instance
   * @return An address instance
   */
  public static Address fromIntZeroExtend(int address) 
    throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new Address(address);
    //-#elif RVM_FOR_64_ADDR
    long val = ((long)address) & 0x00000000ffffffffL;
    return new Address(val);
    //-#endif
  }

  //-#if RVM_FOR_64_ADDR
  /**
   * Fabricate an {@link Address} instance from a long.
   *
   * @param address The long from which to create an {@link Address}
   * instance
   * @return An address instance
   */
  public static Address fromLong(long address)
    throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED); 
    return new Address(address);
  }
  //-#endif

  /**
   * Fabricate an {@link Address} instance from an integer
   *
   * @deprecated To support 32 & 64 bits, the user should be explicit
   * about sign extension
   *
   * @param address the integer from which to create an {@link Address}
   * instance
   * @return An address instance
   */
  public static Address fromInt(int address) 
    throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(address);
  }

  /**
   * Fabricate an <code>ObjectReference</code> instance from an
   * {@link Address} instance.  It is the user's responsibility
   * to ensure that the {@link Address} is suitable (i.e. it
   * points to the object header, or satisfies any other VM-specific
   * requirement for such a conversion).
   *
   * @return An <code>ObjectReference</code> instance.
   */
  public ObjectReference toObjectReference() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Return an integer that reflects the value of this
   * {@link Address} instance.
   *
   * @return An integer that reflects the value of this
   * {@link Address} instance.
   */
  public int toInt () {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (int) value;
  }

  /**
   * Return an long that reflects the value of this
   * {@link Address} instance.
   *
   * @return An long that reflects the value of this
   * {@link Address} instance.
   */
  public long toLong () {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    if (VM.BuildFor64Addr) {
      return value;
    } else {
      return 0x00000000ffffffffL & ((long) value);
    }
  }

  /**
   * Return a <code>Word</code> instance that reflects the value of
   * this {@link Address} instance.
   *
   * @return A <code>Word</code> instance that reflects the value of
   * this {@link Address} instance.
   */
  public Word toWord() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value);
  }

  /****************************************************************************
   *
   * Arithemtic operators
   */

  /**
   * Add an integer to this {@link Address}, and return the sum.
   *
   * @param  v the value to be added to this {@link Address}
   * @return An {@link Address} instance that reflects the result
   * of the addition.
   */
  public Address add(int v) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(value + v);
  }

  /**
   * Add an {@link Offset} to this {@link Address}, and
   * return the sum.
   *
   * @param offset the {@link Offset} to be added to the address
   * @return An {@link Address} instance that reflects the result
   * of the addition.
   */
  public Address add(Offset offset) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new Address(value + offset.toInt());
    //-#elif RVM_FOR_64_ADDR
    return new Address(value + offset.toLong());
    //-#endif
  }

  /**
   * Add an {@link Extent} to this {@link Address}, and
   * return the sum.
   *
   * @param extent the {@link Extent} to be added to this
   * {@link Address}
   * @return An {@link Address} instance that reflects the result
   * of the addition.
   */
  public Address add(Extent extent) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new Address(value + extent.toInt());
    //-#elif RVM_FOR_64_ADDR
    return new Address(value + extent.toLong());
    //-#endif
  }

  /**
   * Subtract an integer from this {@link Address}, and return
   * the result.
   *
   * @param v The integer to be subtracted from this
   * {@link Address}.
   * @return An {@link Address} instance that reflects the result
   * of the subtraction.
   */
  public Address sub(int v) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(value - v);
  }

  /**
   * Subtract an {@link Offset} from this {@link Address}, and
   * return the result.
   *
   * @param offset the {@link Offset} to be subtracted from this
   * {@link Address}.
   * @return An {@link Address} instance that reflects the result
   * of the subtraction.
   */
  public Address sub(Offset offset) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new Address(value - offset.toInt());
    //-#elif RVM_FOR_64_ADDR
    return new Address(value - offset.toLong());
    //-#endif
  }

  /**
   * Subtract an {@link Extent} from this {@link Address}, and
   * return the result.
   *
   * @param extent the {@link Extent} to be subtracted from this
   * {@link Address}.
   * @return An {@link Address} instance that reflects the result
   * of the subtraction.
   */
  public Address sub(Extent extent) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new Address(value - extent.toInt());
    //-#elif RVM_FOR_64_ADDR
    return new Address(value - extent.toLong());
    //-#endif
  }

  /**
   * Compute the difference between two {@link Address}es and
   * return the result.
   *
   * @param addr2 the {@link Address} to be subtracted from this
   * {@link Address}.
   * @return An {@link Offset} instance that reflects the result
   * of the subtraction.
   */
  public Offset diff(Address addr2) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return Offset.fromIntZeroExtend(value - addr2.value);
    //-#elif RVM_FOR_64_ADDR
    return Offset.fromLong(value - addr2.value);
    //-#endif
  }


  /****************************************************************************
   *
   * Boolean operators
   */

  /**
   * Return true if this {@link Address} instance is <i>less
   * than</i> <code>addr2</code>.
   *
   * @param addr2 the {@link Address} to be compared to this
   * {@link Address}.
   * @return true if this {@link Address} instance is <i>less
   * than</i> <code>addr2</code>.
   */
 public boolean LT(Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    if (value >= 0 && addr2.value >= 0) return value < addr2.value;
    if (value < 0 && addr2.value < 0) return value < addr2.value;
    if (value < 0) return false; 
    return true;
  }

  /**
   * Return true if this {@link Address} instance is <i>less
   * than or equal to</i> <code>addr2</code>.
   *
   * @param addr2 the {@link Address} to be compared to this
   * {@link Address}.
   * @return true if this {@link Address} instance is <i>less
   * than or equal to</i> <code>addr2</code>.
   */
  public boolean LE(Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (value == addr2.value) || LT(addr2);
  }

  /**
   * Return true if this {@link Address} instance is <i>greater
   * than</i> <code>addr2</code>.
   *
   * @param addr2 the {@link Address} to be compared to this
   * {@link Address}.
   * @return true if this {@link Address} instance is <i>greater
   * than</i> <code>addr2</code>.
   */
  public boolean GT(Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return addr2.LT(this);
  }

  /**
   * Return true if this {@link Address} instance is <i>greater
   * than or equal to</i> <code>addr2</code>.
   *
   * @param addr2 the {@link Address} to be compared to this
   * {@link Address}.
   * @return true if this {@link Address} instance is <i>greater
   * than or equal to</i> <code>addr2</code>.
   */
  public boolean GE(Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return addr2.LE(this);
  }

  /**
   * Return true if this {@link Address} instance is <i>equal
   * to</i> <code>addr2</code>.
   *
   * @param addr2 the {@link Address} to be compared to this
   * {@link Address}.
   * @return true if this {@link Address} instance is <i>equal
   * to</i> <code>addr2</code>.
   */
  public boolean EQ(Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value == addr2.value;
  }

  /**
   * Return true if this {@link Address} instance is <i>not equal
   * to</i> <code>addr2</code>.
   *
   * @param addr2 the {@link Address} to be compared to this
   * {@link Address}.
   * @return true if this {@link Address} instance is <i>not
   * equal to</i> <code>addr2</code>.
   */
  public boolean NE(Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return !EQ(addr2);
  }

//   public boolean equals(Object o) {
//     if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
//     return (o instanceof Address) && ((Address) o).value == value;
//   }


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
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
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
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /** 
   * Loads a byte from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public byte loadByte() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return (byte)0;
  }

  /** 
   * Loads a byte from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public byte loadByte(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return (byte)0;
  }

  /** 
   * Loads a char from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public char loadChar() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return (char)0;
  }

  /** 
   * Loads a char from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public char loadChar(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return (char)0;
  }

  /** 
   * Loads a short from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public short loadShort() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return (short)0;
  }

  /** 
   * Loads a short from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public short loadShort(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return (short)0;
  }

  /**
   * Loads a float from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public float loadFloat() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return (float)0;
  }

  /** 
   * Loads a float from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public float loadFloat(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return (float)0;
  }

  /** 
   * Loads an int from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public int loadInt() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
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
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return 0;
  }


  /** 
   * Loads a long from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public long loadLong() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
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
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return 0L;
  }

  /** 
   * Loads a <code>double</code> from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public double loadDouble() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return 0;
  }

  /** 
   * Loads a <code>double</code> from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public double loadDouble(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return 0;
  }


  /** 
   * Loads an {@link Address} value from the memory location pointed to by the
   * current instance.
   *
   * @return the read address value.
   */
  public Address loadAddress() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /** 
   * Loads an {@link Address} value from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read address value.
   */
  public Address loadAddress(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /** 
   * Loads a word value from the memory location pointed to by the
   * current instance.
   *
   * @return the read word value.
   */
  public Word loadWord() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
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
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Stores the {@link Address} value in the memory location pointed to by the
   * current instance.
   *
   * @param value The address value to store.
   */
  public void store(ObjectReference value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores the address value in the memory location pointed to by the
   * current instance.
   *
   * @param value The address value to store.
   * @param offset the offset to the value.
   */
  public void store(ObjectReference value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }
 
  /**
   * Stores the address value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The address value to store.
   */
  public void store(Address value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores the address value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The address value to store.
   * @param offset the offset to the value.
   */
  public void store(Address value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores the {@link float} value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The float value to store.
   */
  public void store(float value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores the {@lfloat value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The float value to store.
   * @param offset the offset to the value.
   */
  public void store(float value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }


  /**
   * Stores the {@link Word} value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The {@link Word} value to store.
   */
  public void store(Word value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores the {@link Word} value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The {@link Word} value to store.
   * @param offset the offset to the value.
   */
  public void store(Word value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores the <code>byte</code> value in the memory location pointed to by
   * the  current instance.
   *
   * @param value The <code>byte</code> value to store.
   */
  public void store(byte value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores the byte value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The byte value to store.
   * @param offset the offset to the value.
   */
  public void store(byte value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }


  /** 
   * Stores an int value in memory location pointed to by the
   * current instance.
   *
   * @param value The int value to store.
   */
  public void store(int value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores an int value in memory location pointed to by the
   * current instance.
   *
   * @param value The int value to store.
   * @param offset the offset to the value.
   */
  public void store(int value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param value The double value to store.
   */
  public void store(double value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param value The double value to store.
   * @param offset the offset to the value.
   */
  public void store(double value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }


  /** 
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param value The double value to store.
   */
  public void store(long value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @param value The double value to store.
   */
  public void store(long value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores a char value in the memory location pointed to by the
   * current instance.
   *
   * @param value the char value to store. 
   */
  public void store(char value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores a char value in the memory location pointed to by the
   * current instance.
   *
   * @param value the char value to store. 
   * @param offset the offset to the value.
   */
  public void store(char value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores a short value in the memory location pointed to by the
   * current instance.
   *
   * @param value the short value to store. 
   */
  public void store(short value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores a short value in the memory location pointed to by the
   * current instance.
   *
   * @param value the short value to store. 
   * @param offset the offset to the value.
   */
  public void store(short value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }


  /****************************************************************************
   *
   * Atomic memory access operators (compare and swap)
   */

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to {@link #attempt}.
   *
   * @return the old value to be passed to an {@link #attempt} call.
   */
  public Word prepareWord() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to {@link #attempt}.
   *
   * @param offset the offset to the value.
   * @return the old value to be passed to an {@link #attempt} call.
   */
  public Word prepareWord(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to {@link #attempt}.
   *
   * @return the old value to be passed to an {@link #attempt} call.
   */
  public ObjectReference prepareObjectReference() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to {@link #attempt}.
   *
   * @param offset the offset to the value.
   * @return the old value to be passed to an {@link #attempt} call.
   */
  public ObjectReference prepareObjectReference(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to {@link #attempt}.
   *
   * @return the old value to be passed to an {@link #attempt} call.
   */
  public Address prepareAddress() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to {@link #attempt}.
   *
   * @param offset the offset to the value.
   * @return the old value to be passed to an {@link #attempt} call.
   */
  public Address prepareAddress(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to {@link #attempt}.
   *
   * @return the old value to be passed to an {@link #attempt} call.
   */
  public int prepareInt() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return 0;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to {@link #attempt}.
   *
   * @param offset the offset to the value.
   * @return the old value to be passed to an {@link #attempt} call.
   */
  public int prepareInt(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
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
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
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
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
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
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
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
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
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
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to {@link #prepareObjectReference}.
   *
   * @param old the old value.
   * @param value the new value.
   * @param offset the offset to the value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(ObjectReference old, ObjectReference value, 
                         Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to @link #prepareAddress}.
   *
   * @param old the old value.
   * @param value the new value.
   * @return true if the attempt was successful.
   */ 
  public boolean attempt(Address old, Address value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
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
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }
}
