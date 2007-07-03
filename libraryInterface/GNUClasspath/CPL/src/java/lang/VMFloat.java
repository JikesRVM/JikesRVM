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
package java.lang;

import org.jikesrvm.runtime.VM_Magic;

/**
 * Float <==> int bit transfer for Jikes RVM.
 */
final class VMFloat {

  static int floatToIntBits(float value) {
    int val = VM_Magic.floatAsIntBits(value);
    int exponent = val & 0x7f800000;
    int mantissa = val & 0x007fffff;
    if (exponent == 0x7f800000 && mantissa != 0) {
      return 0x7fc00000;
    } else {
      return val;
    }
  }

  static int floatToRawIntBits(float value) {
    return VM_Magic.floatAsIntBits(value);
  }

  static float intBitsToFloat(int bits) {
    return VM_Magic.intBitsAsFloat(bits);
  }

  static String toString(float f) {
    return VMDouble.toString((double) f, true);
  }

  static float parseFloat(String str) {
    return (float) VMDouble.parseDouble(str);
  }
}
