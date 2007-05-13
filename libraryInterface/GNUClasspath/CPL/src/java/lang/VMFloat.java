/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2003
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
}
