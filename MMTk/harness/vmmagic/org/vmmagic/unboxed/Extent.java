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
import org.vmmagic.unboxed.harness.ArchitecturalWord;

@Unboxed
@RawStorage(lengthInWords = true, length = 1)
public final class Extent {

  final ArchitecturalWord value;

  Extent(ArchitecturalWord value) {
    this.value = value;
  }

  public static Extent fromIntSignExtend(int value) {
    return new Extent(ArchitecturalWord.fromIntSignExtend(value));
  }

  public static Extent fromIntZeroExtend(int value) {
    return new Extent(ArchitecturalWord.fromIntZeroExtend(value));
  }

  public static Extent zero() {
    return fromIntSignExtend(0);
  }

  public static Extent one() {
    return fromIntSignExtend(1);
  }

  public static Extent max() {
    return fromIntSignExtend(0xFFFFFFFF);
  }

  public int toInt() {
    return value.toInt();
  }

  public long toLong() {
    return value.toLongZeroExtend();
  }

  public Word toWord() {
    return new Word(value);
  }

  public Extent plus(int byteSize) {
    return new Extent(value.plus(byteSize));
  }

  public Extent plus(Extent byteSize) {
    return new Extent(value.plus(byteSize.toLong()));
  }

  public Extent minus(int byteSize) {
    return new Extent(value.minus(byteSize));
  }

  public Extent minus(Extent byteSize) {
    return new Extent(value.minus(byteSize.toLong()));
  }

  public boolean LT(Extent extent2) {
    return value.LT(extent2.value);
  }

  public boolean LE(Extent extent2) {
    return value.LE(extent2.value);
  }

  public boolean GT(Extent extent2) {
    return value.GT(extent2.value);
  }

  public boolean GE(Extent extent2) {
    return value.GE(extent2.value);
  }

  public boolean EQ(Extent extent2) {
    return value.EQ(extent2.value);
  }

  public boolean NE(Extent extent2) {
    return value.NE(extent2.value);
  }

  public String toString() {
    return value.toString();
  }
}

