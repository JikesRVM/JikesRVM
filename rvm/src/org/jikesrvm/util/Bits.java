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
package org.jikesrvm.util;

import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Utilities for manipulating values at the bit-level.
 */
public class Bits {

  /**
   * @param value the value to mask
   * @return the lower 16 bits to
   * be used in a PPC immediate field
   */
  public static int PPCMaskLower16(int value) {
    return (value & 0xffff);
  }

  /**
   * @param value the value to mask
   * @return the lower 16 bits to
   * be used in a PPC immediate field
   */
  public static int PPCMaskLower16(Offset value) {
    return (value.toInt() & 0xffff);
  }

  /**
   * @param value the value to mask
   * @return the upper 16 bits to be used in a PPC
   * immediate field
   */
  public static int PPCMaskUpper16(int value) {
    short s = (short) (value & 0xffff);
    return ((value - s) >> 16) & 0xffff;
  }

  /**
   * @param value the value to mask
   * @return the upper 16 bits to be used in a PPC
   * immediate field, make sure fits in 32 bits
   */
  public static int PPCMaskUpper16(Offset value) {
    return PPCMaskUpper16(value.toInt());
  }

  /**
   * @param value the value to mask
   * @return the lower 8 bits (as an int) of an int
   */
  public static int lower8(int value) {
    return (value & 0xff);
  }

  /**
   * @param value the value to mask
   * @return the lower 16 bits (as an int) of  an int
   */
  public static int lower16(int value) {
    return (value & 0xffff);
  }

  /**
   * @param value the value to mask
   * @return the upper 16 bits (as an int) of an int
   */
  public static int upper16(int value) {
    return value >>> 16;
  }

  /**
   * @param value the value to mask
   * @return the upper 24 bits (as an int) of an int
   */
  public static int upper24(int value) {
    return value >>> 8;
  }

  /**
   * @param value the value to mask
   * @return the lower 32 bits (as an int) of a long
   */
  public static int lower32(long value) {
    return (int) value;
  }

  /**
   * @param value the value to mask
   * @return the upper 32 bits (as an int) of a long
   */
  public static int upper32(long value) {
    return (int) (value >>> 32);
  }

  /**
   * Finds out whether a given signed value can be represented in a
   * given number of bits.
   *
   * @param val the value to be represented
   * @param bits the number of bits to use.
   * @return {@code true} if val can be encoded in bits.
   */
  @Inline
  public static boolean fits(long val, int bits) {
    val = val >> bits - 1;
    return (val == 0L || val == -1L);
  }

  /**
   * Finds out whether a given signed value can be represented in a
   * given number of bits.
   *
   * @param val the value to be represented
   * @param bits the number of bits to use.
   * @return {@code true} if val can be encoded in bits.
   */
  @Inline
  public static boolean fits(Offset val, int bits) {
    return fits(val.toWord(), bits);
  }

  /**
   * Find out whether a given signed value can be represented in a
   * given number of bits.
   *
   * @param val the value to be represented
   * @param bits the number of bits to use.
   * @return {@code true} if val can be encoded in bits.
   */
  @Inline
  public static boolean fits(Address val, int bits) {
    return fits(val.toWord(), bits);
  }

  /**
   * Finds out whether a given signed value can be represented in a
   * given number of bits.
   *
   * @param val the value to be represented
   * @param bits the number of bits to use.
   * @return {@code true} if val can be encoded in bits.
   */
  public static boolean fits(int val, int bits) {
    val = val >> bits - 1;
    return (val == 0 || val == -1);
  }

  /**
   * Finds out whether a given signed value can be represented in a
   * given number of bits.
   *
   * @param val the value to be represented
   * @param bits the number of bits to use.
   * @return {@code true} if val can be encoded in bits.
   */
  @Inline
  public static boolean fits(Word val, int bits) {
    Word o = val.rsha(bits - 1);
    return (o.isZero() || o.isMax());
  }

  public static boolean fitsUnsigned(int val, int bits) {
    return val >= 0 && val < (1 << bits);
  }

}
