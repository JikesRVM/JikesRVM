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
package org.vmmagic.unboxed;

import org.vmmagic.Unboxed;
import org.vmmagic.pragma.RawStorage;

/**
 * Represents a pointer-sized unsigned integer used for describing a length in bytes.
 * Typical uses include "length" or "size" arguments (e.g., for memcpy).
 */
@Unboxed
@RawStorage(lengthInWords = true, length = 1)
public final class Extent {
  public static Extent fromIntSignExtend(int address) {
    return null;
  }

  public static Extent fromIntZeroExtend(int address) {
    return null;
  }

  public static Extent zero() {
    return null;
  }

  public static Extent one() {
    return null;
  }

  public static Extent max() {
    return null;
  }

  public int toInt() {
    return 0;
  }

  public long toLong() {
    return 0L;
  }

  public Word toWord() {
    return null;
  }

  public Extent plus(int byteSize) {
    return null;
  }

  public Extent plus(Extent byteSize) {
    return null;
  }

  public Extent minus(int byteSize) {
    return null;
  }

  public Extent minus(Extent byteSize) {
    return null;
  }

  public boolean LT(Extent extent2) {
    return false;
  }

  public boolean LE(Extent extent2) {
    return false;
  }

  public boolean GT(Extent extent2) {
    return false;
  }

  public boolean GE(Extent extent2) {
    return false;
  }

  public boolean EQ(Extent extent2) {
    return false;
  }

  public boolean NE(Extent extent2) {
    return false;
  }
}

