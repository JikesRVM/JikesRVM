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
  static final double I2Dconstant = Double.longBitsToDouble(0x4330000080000000L);
  static final double IEEEmagic = Double.longBitsToDouble(0x4338000000000000L);
  static final long   longOne  =  1L;
  static final float  minusOne = -1.0F;
  static final float  zero     =  0.0F;
  static final float  half     =  0.5F;
  static final float  one      =  1.0F;
  static final float  two      =  2.0F;
  static final double zeroD    =  0.0;
  static final double oneD     =  1.0;
  static final float  half32   =  java.lang.Float.intBitsToFloat(0x2f800000);
  static final float  two32    =  java.lang.Float.intBitsToFloat(0x4f800000);
  static final double billionth = 1e-9;
  
  // largest double that can be rounded to an int
  static final double maxint   =  0.5D + 0x7FFFFFFF;

  // smallest double that can be rounded to an int
  static final double minint   =  (double)Integer.MIN_VALUE;
}
