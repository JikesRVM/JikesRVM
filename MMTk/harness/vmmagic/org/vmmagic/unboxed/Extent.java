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
package org.vmmagic.unboxed;

import org.vmmagic.Unboxed;
import org.vmmagic.pragma.RawStorage;

@Unboxed
@RawStorage(lengthInWords = true, length = 1)
public final class Extent {

  final int value;

  Extent(int value) {
    this.value = value;
  }

  public static Extent fromIntSignExtend(int value) {
    return new Extent(value);
  }

  public static Extent fromIntZeroExtend(int value) {
    return new Extent(value);
  }

  public static Extent zero() {
    return new Extent(0);
  }

  public static Extent one() {
    return new Extent(1);
  }

  public static Extent max() {
    return new Extent(0xFFFFFFFF);
  }

  public int toInt() {
    return value;
  }

  public long toLong() {
    return ((long)value) & 0x00000000FFFFFFFFL;
  }

  public Word toWord() {
    return new Word(value);
  }

  public Extent plus(int byteSize) {
    return new Extent(value + byteSize);
  }

  public Extent plus(Extent byteSize) {
    return new Extent(value + byteSize.value);
  }

  public Extent minus(int byteSize) {
    return new Extent(value - byteSize);
  }

  public Extent minus(Extent byteSize) {
    return new Extent(value - byteSize.value);
  }

  public boolean LT(Extent extent2) {
    if (value >= 0 && extent2.value >= 0) return value < extent2.value;
    if (value < 0 && extent2.value < 0) return value < extent2.value;
    if (value < 0) return false;
    return true;
  }

  public boolean LE(Extent extent2) {
    return EQ(extent2) || LT(extent2);
  }

  public boolean GT(Extent extent2) {
    return extent2.LT(this);
  }

  public boolean GE(Extent extent2) {
    return EQ(extent2) || GT(extent2);
  }

  public boolean EQ(Extent extent2) {
    return value == extent2.value;
  }

  public boolean NE(Extent extent2) {
    return !EQ(extent2);
  }

  public String toString() {
    return Address.formatInt(value);
  }
}

