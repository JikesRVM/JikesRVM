/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.runtime;

/**
 * Placeholder for constants that are accessed from
 * generated code.
 */
public class VM_Math {
  /*
   * Constants that are used by the compilers in generated code.
   */
  static final double I2Dconstant = Double.longBitsToDouble(0x4330000080000000L);
  static final double IEEEmagic = Double.longBitsToDouble(0x4338000000000000L);
  static final long longOne = 1L;
  static final float minusOne = -1.0F;
  static final float zero = 0.0F;
  static final float half = 0.5F;
  static final float one = 1.0F;
  static final float two = 2.0F;
  static final double zeroD = 0.0;
  static final double oneD = 1.0;
  static final float half32 = java.lang.Float.intBitsToFloat(0x2f800000);
  static final float two32 = java.lang.Float.intBitsToFloat(0x4f800000);
  static final double billionth = 1e-9;

  // largest double that can be rounded to an int
  static final double maxint = 0.5D + 0x7FFFFFFF;

  // smallest double that can be rounded to an int
  static final double minint = (double) Integer.MIN_VALUE;
}
