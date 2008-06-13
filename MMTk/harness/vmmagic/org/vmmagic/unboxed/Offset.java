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

/**
 * To be commented
 */
@Unboxed
@RawStorage(lengthInWords = true, length = 1)
public final class Offset {

  final int value;

  Offset(int value) {
    this.value = value;
  }

  public static Offset fromIntSignExtend(int value) {
    return new Offset(value);
  }

  public static Offset fromIntZeroExtend(int value) {
    return new Offset(value);
  }

  public static Offset zero() {
    return new Offset(0);
  }

  public static Offset max() {
    return new Offset(Integer.MAX_VALUE);
  }

  public int toInt() {
    return value;
  }

  public long toLong() {
    return value;
  }

  public Word toWord() {
    return new Word(value);
  }

  public Offset plus(int byteSize) {
    return new Offset(value + byteSize);
  }

  public Offset minus(int byteSize) {
    return new Offset(value - byteSize);
  }

  public Offset minus(Offset off2) {
    return new Offset(value + off2.value);
  }

  public boolean EQ(Offset off2) {
    return value == off2.value;
  }

  public boolean NE(Offset off2) {
    return !EQ(off2);
  }

  public boolean sLT(Offset off2) {
    return value < off2.value;
  }

  public boolean sLE(Offset off2) {
    return value <= off2.value;
  }

  public boolean sGT(Offset off2) {
    return value > off2.value;
  }

  public boolean sGE(Offset off2) {
    return value >= off2.value;
  }

  public boolean isZero() {
    return value == 0;
  }

  public boolean isMax() {
    return value == Integer.MAX_VALUE;
  }

  public String toString() {
    return Address.formatInt(value);
  }
}

