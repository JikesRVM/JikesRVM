/*
 * (C) Copyright IBM Corp 2003
 */
//$Id$
package java.lang;

import com.ibm.JikesRVM.VM_Magic;

/**
 * Float <==> int bit transfer for Jikes RVM.
 * 
 * @author Dave Grove
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
