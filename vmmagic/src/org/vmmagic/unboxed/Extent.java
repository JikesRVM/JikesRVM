/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Australian National University. 2004
 */
package org.vmmagic.unboxed;

import org.vmmagic.Unboxed;

/**
 * Commenting required
 */
@Unboxed
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

