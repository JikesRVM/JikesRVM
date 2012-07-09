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
package org.jikesrvm.runtime;

import org.vmmagic.pragma.Entrypoint;

/**
 * Placeholder for constants that are accessed from
 * generated code.
 */
public class MathConstants {
  /*
   * Constants that are used by the compilers in generated code.
   */
  @Entrypoint
  static final double I2Dconstant = Double.longBitsToDouble(0x4330000080000000L);
  @Entrypoint
  static final double IEEEmagic = Double.longBitsToDouble(0x4338000000000000L);
  @Entrypoint
  static final long longOne = 1L;
  @Entrypoint
  static final float minusOne = -1.0F;
  @Entrypoint
  static final float zero = 0.0F;
  @Entrypoint
  static final float half = 0.5F;
  @Entrypoint
  static final float one = 1.0F;
  @Entrypoint
  static final float two = 2.0F;
  @Entrypoint
  static final double zeroD = 0.0;
  @Entrypoint
  static final double oneD = 1.0;
  @Entrypoint
  static final float half32 = java.lang.Float.intBitsToFloat(0x2f800000);
  @Entrypoint
  static final float two32 = java.lang.Float.intBitsToFloat(0x4f800000);
  @Entrypoint
  static final double billionth = 1e-9;

  /** largest double that can be rounded to an int */
  @Entrypoint
  static final double maxint = 0.5D + 0x7FFFFFFF;
  /** largest double that can be rounded to a long */
  @Entrypoint
  static final double maxlong = 0.5D + 0x7FFFFFFFFFFFFFFFL;
  /** largest float that can be rounded to an int (0x7FFFFFFF) */
  @Entrypoint
  static final float maxintF = java.lang.Float.intBitsToFloat(0x4F000000);
  /** largest float that can be rounded to a long (0x7FFFFFFFFFFFFFFF) */
  @Entrypoint
  static final float maxlongF = java.lang.Float.intBitsToFloat(0x5F000000);

  /** smallest double that can be rounded to an int */
  @Entrypoint
  static final double minint = (double) Integer.MIN_VALUE;
}
