/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * OPT_Bits.java
 *
 * utilities for manipulating values at the bit-level.
 *
 * @author Stephen Fink
 * @author Dave Grove
 */
public class OPT_Bits {

  //-#if RVM_FOR_POWERPC
  /**
   * Return the lower 16 bits to
   * be used in a PPC immediate field
   */
  static int PPCMaskLower16(int value) {
    return  (value & 0xffff);
  }

  /**
   * Return the upper 16 bits to be used in a PPC 
   * immediate field
   */
  static int PPCMaskUpper16(int value) {
    short s = (short)(value & 0xffff);
    return  ((value - (int)s) >> 16) & 0xffff;
  }
  //-#endif

  /**
   * Return the lower 8 bits (as an int) of an int
   */
  static int lower8(int value) {
    return (value & 0xff);
  }

  /**
   * Return the lower 16 bits (as an int) of  an int
   */
  static int lower16(int value) {
    return (value & 0xffff);
  }

  /**
   * Return the upper 16 bits (as an int) of an int
   */
  static int upper16(int value) {
    return value >>> 16;
  }

  /**
   * Return the upper 24 bits (as an int) of an int
   */
  static int upper24(int value) {
    return value >>> 8;
  }

  /**
   * Return the lower 32 bits (as an int) of a long
   */
  static int lower32(long value) {
    return (int)value;
  }

  /**
   * Return the upper 32 bits (as an int) of a long
   */
  static int upper32(long value) {
    return (int)(value >>> 32);
  }


  /**
   * Does an int literal val fit in bits bits?
   */
  static boolean fits (int val, int bits) {
    val = val >> bits - 1;
    return  (val == 0 || val == -1);
  }


  /**
   * Return the number of ones in the binary representation of an integer.
   */
  static int populationCount(int value) {
    int result = 0;
    while (value != 0) {
      result++;
      value &= (value-1); // clear lsb 1 bit
    }
    return result;
  }
}
