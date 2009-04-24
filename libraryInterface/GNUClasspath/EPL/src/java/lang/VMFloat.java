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
package java.lang;

import org.jikesrvm.runtime.Magic;

/**
 * Float <==> int bit transfer for Jikes RVM.
 */
final class VMFloat {

  static int floatToIntBits(float value) {
    // Check for NaN and return canonical NaN value
    if (value != value) return 0x7fc00000;
    else return Magic.floatAsIntBits(value);
  }

  static int floatToRawIntBits(float value) {
    return Magic.floatAsIntBits(value);
  }

  static float intBitsToFloat(int bits) {
    return Magic.intBitsAsFloat(bits);
  }

  static String toString(float f) {
    return VMDouble.toString((double) f, true);
  }

  static float parseFloat(String str) {
    return (float) VMDouble.parseDouble(str);
  }
}
