/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * This class provides a set of static method entrypoints used in the
 * implementation of standard library floating point operations.
 *
 * @author Stephen Fink
 */

package com.ibm.JikesRVM.librarySupport;
import VM_FloatingPoint;
import VM_Math;
import VM_Magic;

public class FloatingPointSupport {

  /**
   * Covert a double to a string.
   */
  public static String toString(double value) {
    return VM_FloatingPoint.toString(value);
  }

  /**
   * Covert a float to a string.
   */
  public static String toString(float value) {
    return VM_FloatingPoint.toString((double)value);
  }

  /**
   * Convert a string to a Double
   */
  public static Double toDouble(String string) throws NumberFormatException {
    return new Double(VM_Math.stringToDouble(string));
  }

  /**
   * Convert a string to a Float
   */
  public static Float toFloat(String string) throws NumberFormatException {
    return new Float(VM_Math.stringToDouble(string));
  }

  /**
   * Answers the binary representation of the argument, as
   * a long.
   *
   * @param		value	The double value to convert
   * @return		the bits of the double.
   */
  public static long doubleToLongBits(double value) { 
    return VM_Magic.doubleAsLongBits(value); 
  }

  /**
   * Answers the binary representation of the argument, as
   * an int.
   *
   * @param		value	The float value to convert
   * @return		the bits of the float .
   */
  public static int floatToIntBits(float value) { 
    return VM_Magic.floatAsIntBits(value); 
  }

  /**
   * Answers a double built from the binary representation
   * given in the argument.
   *
   * @param		bits		the bits of the double
   * @return		the double which matches the bits
   */
  public static double longBitsToDouble(long bits)
  { 
    return VM_Magic.longBitsAsDouble(bits); 
  }

  /**
   * Answers a float built from the binary representation
   * given in the argument.
   *
   * @param		bits		the bits of the float
   * @return		the float which matches the bits
   */
  public static float intBitsToFloat(int bits)
  {
    return VM_Magic.intBitsAsFloat(bits);
  }
}
