/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Placeholder for constants that are accessed from
 * generated code.
 *
 * @author Bowen Alpern
 * @author Mauricio Serrano
 * @author John Whaley
 */
public class VM_Math {

  //-#if RVM_FOR_IA32
  /**
   * A well-known memory location used to manipulate the FPU control word.
   */
  static int FPUControlWord;
  //-#endif RVM_FOR_IA32

  /*
   * Constants that are used by the compilers in generated code.
   */
  static double I2Dconstant = Double.longBitsToDouble(0x4330000080000000L);
  static double IEEEmagic = Double.longBitsToDouble(0x4338000000000000L);
  static long   longOne  =  1L;
  static float  minusOne = -1.0F;
  static float  zero     =  0.0F;
  static float  half     =  0.5F;
  static float  one      =  1.0F;
  static float  two      =  2.0F;
  static double zeroD    =  0.0;
  static double oneD     =  1.0;
  static float  half32   =  java.lang.Float.intBitsToFloat(0x2f800000);
  static float  two32    =  java.lang.Float.intBitsToFloat(0x4f800000);
  static double billionth = 1e-9;
  
  // largest double that can be rounded to an int
  static double maxint   =  0.5D + 0x7FFFFFFF;

  // smallest double that can be rounded to an int
  static double minint   =  (double)Integer.MIN_VALUE;
}
