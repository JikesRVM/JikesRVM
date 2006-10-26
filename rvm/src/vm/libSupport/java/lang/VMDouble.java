/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2003
 */
//$Id$
package java.lang;

import com.ibm.jikesrvm.VM_Magic;

/**
 * Double <==> long bit transfer for Jikes RVM.
 * 
 * @author Dave Grove
 */
final class VMDouble {

  static {
    initIDs();
  };

  static long doubleToLongBits(double value) {
    long val = VM_Magic.doubleAsLongBits(value);
    long exponent = val & 0x7ff0000000000000L;
    long mantissa = val & 0x000fffffffffffffL;
    if (exponent ==  0x7ff0000000000000L && mantissa != 0) {
      return 0x7ff8000000000000L;
    } else {
      return val;
    }
  }

  static long doubleToRawLongBits(double value) {
    return VM_Magic.doubleAsLongBits(value);
  }

  static double longBitsToDouble(long bits) {
    return VM_Magic.longBitsAsDouble(bits);
  }

  public static native String toString(double d, boolean isFloat);

  public static native void initIDs();

  public static native double parseDouble(String str);
}
