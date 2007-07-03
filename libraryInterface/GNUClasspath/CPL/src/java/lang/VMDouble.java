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
 * Double <==> long bit transfer for Jikes RVM.
 */
final class VMDouble {

  static {
    initIDs();
  }

  static long doubleToLongBits(double value) {
    // Check for NaN and return canonical NaN value
    if (value != value) return 0x7ff8000000000000L;
    else return VM_Magic.doubleAsLongBits(value);
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
