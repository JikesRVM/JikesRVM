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
package org.jikesrvm.util;

public class StringUtilities {

  /**
   * Convert a <code>String</code> filename to a byte array using the
   * deprecated ascii String method for cases where passing it through
   * a character set converter is too heavyweight.  Allocates an
   * additional null-terminator byte so that the resulting byte array
   * can be passed to native C functions.
   *
   * @param fileName File name
   * @return Byte-array representation
   */
  @SuppressWarnings("deprecation")
  public static byte[] stringToBytesNullTerminated(String fileName) {
    byte[] asciiName = new byte[fileName.length() + 1]; // +1 for \0 terminator
    fileName.getBytes(0, fileName.length(), asciiName, 0);
    return asciiName;
  }

  /**
   * Convert a <code>String</code> filename to a byte array using the
   * deprecated ascii String method for cases where passing it through
   * a character set converter is too heavyweight.
   *
   * @param fileName File name
   * @return Byte-array representation
   */
  @SuppressWarnings("deprecation")
  public static byte[] stringToBytes(String fileName) {
    byte[] asciiName = new byte[fileName.length()];
    fileName.getBytes(0, fileName.length(), asciiName, 0);
    return asciiName;
  }

  /**
   * Convert a byte array to a <code>String</code> assuming the ASCII
   * character set, for use in cases (such as early in the boot process)
   * where character set conversion is unavailable or inadvisable.
   *
   * @param val Byte array to convert
   * @return The resulting string
   */
  public static String asciiBytesToString(byte[] val) {
    return asciiBytesToString(val, 0, val.length);
  }

  /**
   * Convert a byte array to a <code>String</code> assuming the ASCII
   * character set, for use in cases (such as early in the boot process)
   * where character set conversion is unavailable or inadvisable.
   *
   * @param val Byte array to convert
   * @param start Start the string at this byte
   * @param length Use 'length' bytes
   * @return The resulting string
   */
  @SuppressWarnings("deprecation")
  public static String asciiBytesToString(byte[] val, int start, int length) {
    return new String(val, 0, start, length);
  }

}
