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

import org.jikesrvm.SizeConstants;
import org.jikesrvm.VM;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;

/**
 * The {@link Address} type is used by the runtime system and collector to
 * denote machine addresses.  We use a separate type instead of the
 * Java int type for coding clarity,  machine-portability (it can map
 * to 32 bit and 64 bit integral types), and access to unsigned
 * operations (Java does not have unsigned int types).
 * <p>
 * For efficiency and to avoid meta-circularity, the Address class is
 * intercepted like {@link org.jikesrvm.runtime.Magic} and converted into the base type so no
 * Address object is created run-time.
 *
 */
@Uninterruptible
public final class Address extends ArchitecturalWord implements SizeConstants {

  Address(int value) {
    super(value, false);
  }
  Address(int value, boolean zeroExtend) {
    super(value, zeroExtend);
  }
  Address(long value) {
    super(value);
  }

  /* Compensate for some java compilers helpfully defining this synthetically */
  @Override
  @Interruptible
  public String toString() {
    return super.toString();
  }


  /****************************************************************************
   *
   * Special values
   */

  /** Constant zero address */
  private static final Address _zero = new Address(0);

  /**
   * Return an {@link Address} instance that reflects the value
   * zero.
   *
   * @return An {@link Address} instance that reflects the value zero.
   */
  @UninterruptibleNoWarn
  public static Address zero() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return _zero;
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

  /** Constant zero address */
  private static final Address _max = fromIntSignExtend(-1);

  /**
   * Return an {@link Address} instance that reflects the maximum
   * allowable {@link Address} value.
   *
   * @return An {@link Address} instance that reflects the
   * maximum allowable {@link Address} value.
   */
  public static Address max() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return _max;
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
  @UninterruptibleNoWarn
  public static Address fromIntSignExtend(int address) {
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
  @UninterruptibleNoWarn
  public static Address fromIntZeroExtend(int address) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(address, true);
  }

  /**
   * Fabricate an {@link Address} instance from a long.
   *
   * @param address The long from which to create an {@link Address}
   * instance
   * @return An address instance
   */
  @UninterruptibleNoWarn
  public static Address fromLong(long address) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(address);
 }

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
  @Deprecated
  @UninterruptibleNoWarn
  public static Address fromInt(int address) {
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
  public int toInt() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (int) value;
  }

  /**
   * Return a <code>long</code> that reflects the value of this
   * {@link Address} instance.
   *
   * @return a <code>long</code> that reflects the value of this
   * {@link Address} instance.
   */
  public long toLong() {
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
  @UninterruptibleNoWarn
  public Address plus(int v) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(value + v);
  }

  /**
   * Add an {@link Offset} to this {@link Address}, and
   * return a new {@link Address} which is the sum.
   *
   * @param offset the {@link Offset} to be added to the address
   * @return An {@link Address} instance that reflects the result
   * of the addition.
   */
  @UninterruptibleNoWarn
  public Address plus(Offset offset) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(value + offset.toWord().toAddress().value);
  }

  /**
   * Add an {@link Extent} to this {@link Address}, and
   * return a new {@link Address} which is the sum.
   *
   * @param extent the {@link Extent} to be added to this
   * {@link Address}
   * @return An {@link Address} instance that reflects the result
   * of the addition.
   */
  @UninterruptibleNoWarn
  public Address plus(Extent extent) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(value + extent.toWord().toAddress().value);
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
  @UninterruptibleNoWarn
  public Address minus(int v) {
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
  @UninterruptibleNoWarn
  public Address minus(Offset offset) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(value - offset.toWord().toAddress().value);
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
  @UninterruptibleNoWarn
  public Address minus(Extent extent) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(value - extent.toWord().toAddress().value);
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
  @UninterruptibleNoWarn
  public Offset diff(Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(value - addr2.value);
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
   * Software prefetch and other per-address cache management operators
   */

  /**
   * Prefetch a cache-line, architecture-independent
   */
  public void prefetch() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * IA32 "prefetchnta" operation: "fetches the data into the
   * second-level cache, minimizing cache pollution." (Semantics are
   * micro-architecture-specific: check for your processor!)
   * NOTE: IA32-specific
   */
  public void prefetchNTA() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

//   CURRENTLY UNIMPLEMENTED
//   /**
//    * IA32 "prefetcht0" operation: "fetches the data into all cache
//    * levels, that is, to the second-level cache for the Pentium 4
//    * processor." (Semantics are micro-architecture-specific: check for
//    * your processor!)
//    */
//    public void prefetchT0() {
//      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
//    }

//   /**
//    * IA32 "prefetcht1" operation, identical to prefetcht0 on the
//    * Pentium 4 (Semantics are micro-architecture-specific: check for
//    * your processor!)
//    */
//    public void prefetchT1() {
//      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
//    }

//   /**
//    * IA32 "prefetcht2" operation, identical to prefetcht0 on the
//    * Pentium 4 (Semantics are micro-architecture-specific: check for
//    * your processor!)
//    */
//    public void prefetchT2() {
//      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
//    }

  /**
   * Write contents of this processor's modified data cache back to
   * main storage.
   */
  public void dcbst() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Touch a data cache block (use to prefetch).
   * NOTE: PowerPC-specific
   */
  public void dcbt() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Touch a data cache block for a store (use to prefetch on a
   * store).
   * NOTE: PowerPC-specific
   */
  public void dcbtst() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Zero all bytes of this 32 byte cache block without forcing a read
   * (use to avoid a miss on an initilizing store).
   * NOTE: PowerPC-specific
   */
  public void dcbz() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Zero all bytes of this cache block without forcing a read (use to
   * avoid a miss on an initilizing store). Note: this is an extended
   * form of dcbz for the PPC970 that deals with the larger (128 byte)
   * cache line.
   * NOTE: PowerPC-specific
   */
  public void dcbzl() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * The icbi instruction invalidates a block containing the byte
   * addressed in the instruction cache.
   * NOTE: PowerPC-specific
   */
  public void icbi() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
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
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Loads a reference from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the current instance.
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
   * @param offset the offset to the current instance.
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
   * @param offset the offset to the current instance.
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
   * @param offset the offset to the current instance.
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
    return 0;
  }

  /**
   * Loads a float from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the current instance.
   * @return the read value
   */
  public float loadFloat(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return 0;
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
   * @param offset the offset to the current instance.
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
   * @param offset the offset to the current instance.
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
   * @param offset the offset to the current instance.
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
   * @param offset the offset to the current instance.
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
   * current instance plus the passed offset.
   *
   * @param offset the offset to the current instance.
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
   * current instance plus the passed offset.
   *
   * @param value The address value to store.
   * @param offset the offset to the current instance.
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
   * current instance plus the passed offset.
   *
   * @param value The address value to store.
   * @param offset the offset to the current instance.
   */
  public void store(Address value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores the <code>float</code> value in the memory location pointed to by
   * the current instance.
   *
   * @param value The float value to store.
   */
  public void store(float value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores a <code>float</code> in the memory location pointed to by the
   * current instance plus the passed offset.
   *
   * @param value The float value to store.
   * @param offset the offset to the current instance.
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
   * @param offset the offset to the current instance.
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
   * @param offset the offset to the current instance.
   */
  public void store(byte value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }


  /**
   * Stores an <code>int</code> value in the memory location pointed to by the
   * current instance.
   *
   * @param value The int value to store.
   */
  public void store(int value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores an <code>int</code> value in the memory location pointed to by the
   * current instance plus the passed offset.
   *
   * @param value The int value to store.
   * @param offset The offset to the current instance.
   */
  public void store(int value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores a <code>double</code> value in the memory location pointed to
   * by the current instance.
   *
   * @param value The double value to store.
   */
  public void store(double value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores a <code>double</code> value in the memory location pointed to by the
   * current instance plus the passed offset.
   *
   * @param value The <code>double</code> value to store.
   * @param offset The offset to the current instance.
   */
  public void store(double value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }


  /**
   * Stores a <code>long</code> value in the memory location pointed to by the
   * current instance.
   *
   * @param value The <code>long</code> value to store.
   */
  public void store(long value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores a <code>long</code> value in the memory location pointed to by the
   * current instance.
   *
   * @param value The <code>double</code> value to store.
   * @param offset The offset to the current instance.
   */
  public void store(long value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores a <code>char</code> value in the memory location pointed to by the
   * current instance.
   *
   * @param value The char value to store.
   */
  public void store(char value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores a <code>char</code> value in the memory location pointed to by the
   * current instance.
   *
   * @param value The char value to store.
   * @param offset The offset to the current instance.
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
   * @param offset the offset to the current instance.
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
   * @param offset the offset to the current instance.
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
   * @param offset the offset to the current instance.
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
   * @param offset the offset to the current instance.
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
   * @param offset the offset to the current instance.
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
   * @param offset the offset to the current instance.
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
   * @param offset the offset to the current instance.
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
   * @param offset the offset to the current instance.
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
   * @param offset the offset to the current instance.
   * @return true if the attempt was successful.
   */
  public boolean attempt(Address old, Address value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }
}
