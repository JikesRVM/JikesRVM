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
package org.mmtk.vm.gcspy;

import org.mmtk.utility.Log;
import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Abstract class that provides generally useful
 * methods.
 */
@Uninterruptible public abstract class Util {

  /**
   * Allocate an array of bytes with malloc
   *
   * @param size The size to allocate
   * @return The start address of the memory allocated in C space
   * @see #free
  */
  public abstract Address malloc(int size);

  /**
   * Free an array of bytes previously allocated with malloc
   *
   * @param addr The address of some memory previously allocated with malloc
   * @see #malloc
   */
  public abstract void free(Address addr);

  /**
   * Dump a range in format [start,end)
   *
   * @param start The start of the range
   * @param end The end of the range
   */
  public static void dumpRange(Address start, Address end) {
    Log.write("["); Log.write(start);
    Log.write(","); Log.write(end);
    Log.write(')');
  }

  /**
   * Convert a String to a 0-terminated array of bytes
   *
   * @param str The string to convert
   * @return The address of a null-terminated array in C-space
   */
  public abstract Address getBytes(String str);

  /**
   * Pretty print a size, converting from bytes to kilo- or mega-bytes as appropriate
   *
   * @param buffer The buffer (in C space) in which to place the formatted size
   * @param size The size in bytes
   */
  public abstract void formatSize(Address buffer, int size);

  /**
   * Pretty print a size, converting from bytes to kilo- or mega-bytes as appropriate
   *
   * @param format A format string
   * @param bufsize The size of a buffer large enough to hold the formatted result
   * @param size The size in bytes
   */
  public abstract Address formatSize(String format, int bufsize, int size);

//  public abstract Address formatSize(String format, int bufsize, int size);

  /**
   * Place a string representation of a long in an array of bytes
   * without incurring allocation
   *
   * @param buffer The byte array
   * @param value The long to convert
   * @return The length of the string representation of the integer
   *         -1 indicates some problem (e.g the char buffer was too small)
   */
  public static int numToBytes(byte[] buffer, long value) {
    return numToBytes(buffer, value, 10);
  }

  /**
   * Place a string representation of a long in an array of bytes
   * without incurring allocation
   *
   * @param buffer The byte array
   * @param value The long to convert
   * @param radix the base to use for conversion
   * @return The length of the string representation of the integer
   *         -1 indicates some problem (e.g the char buffer was too small)
   */
  public static int numToBytes(byte[] buffer, long value, int radix) {

    if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX)
      radix = 10;

    if (value == 0) {
      buffer[0] = (byte)'0';
      return 1;
    }

    boolean negative;
    long longValue;
    int count;
    if (!(negative = (value < 0))) {
      longValue = -value;
      count = 1;
    } else {
      longValue = value;
      count = 2;
    }

    long j = longValue;
    while ((j /= radix) != 0) count++;
    if (count > buffer.length)
      return -1; // overflow

    int i = count;
    do {
      int ch = (int) -(longValue % radix);
      if (ch > 9)
        ch -= (10 - (int) 'a');
      else
        ch += (int) '0';
      buffer [--i] = (byte) ch;
    } while ((longValue /= radix) != 0);
    if (negative) buffer [0] = (byte)'-';

    return count;

  }

  /**
   * sprintf(char *str, char *format, char* value)
   *
   * @param str The destination 'string' (memory in C space)
   * @param format The format 'string' (memory in C space)
   * @param value The value 'string' (memory in C space)
   * @return The number of characters printed (as returned by C's sprintf
   */
  public abstract int sprintf(Address str, Address format, Address value);

  /**
   * Create an array of a particular type.
   * The easiest way to use this is:
   *     Foo[] x = (Foo [])Stream.createDataArray(new Foo[0], numElements);
   * @param templ a data array to use as a template
   * @param numElements number of elements in new array
   * @return the new array
   */
  @Interruptible
  public abstract Object createDataArray(Object templ, int numElements);
}

