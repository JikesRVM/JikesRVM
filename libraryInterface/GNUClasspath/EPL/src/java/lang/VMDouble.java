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
import org.vmmagic.pragma.Pure;

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
    else return Magic.doubleAsLongBits(value);
  }

  static long doubleToRawLongBits(double value) {
    return Magic.doubleAsLongBits(value);
  }

  static double longBitsToDouble(long bits) {
    return Magic.longBitsAsDouble(bits);
  }

  @Pure
  public static native String toString(double d, boolean isFloat);

  public static native void initIDs();

  @Pure
  public static native double parseDouble(String str);
}
