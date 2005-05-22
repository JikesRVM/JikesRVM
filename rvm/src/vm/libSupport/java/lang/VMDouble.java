/*
 * (C) Copyright IBM Corp 2003
 */
//$Id$
package java.lang;

import com.ibm.JikesRVM.VM_Magic;

/**
 * Double <==> long bit transfer for Jikes RVM.
 * 
 * @author Dave Grove
 */
final class VMDouble {

  static {
    //-#if RVM_WITH_CLASSPATH_0_15 || RVM_WITH_CLASSPATH_CVS_HEAD
    initIDs();
    //-#endif
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
