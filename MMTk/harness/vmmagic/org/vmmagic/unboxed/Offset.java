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

/**
 * TODO To be commented
 */
@Unboxed
@RawStorage(lengthInWords = true, length = 1)
public final class Offset {

  final ArchitecturalWord value;

  Offset(ArchitecturalWord value) {
    this.value = value;
  }

  public static Offset fromIntSignExtend(int value) {
    return new Offset(ArchitecturalWord.fromIntSignExtend(value));
  }

  public static Offset fromIntZeroExtend(int value) {
    return new Offset(ArchitecturalWord.fromIntZeroExtend(value));
  }

  public static Offset zero() {
    return new Offset(ArchitecturalWord.fromIntSignExtend(0));
  }

  public static Offset max() {
    return new Offset(ArchitecturalWord.fromIntSignExtend(-1).rshl(1));
  }

  public int toInt() {
    return value.toInt();
  }

  public long toLong() {
    return value.toLongSignExtend();
  }

  public Word toWord() {
    return new Word(value);
  }

  public Offset plus(int byteSize) {
    return new Offset(value.plus(byteSize));
  }

  public Offset minus(int byteSize) {
    return new Offset(value.minus(byteSize));
  }

  public Offset minus(Offset off2) {
    return new Offset(value.minus(off2.toLong()));
  }

  public boolean EQ(Offset off2) {
    return value.EQ(off2.value);
  }

  public boolean NE(Offset off2) {
    return value.NE(off2.value);
  }

  public boolean sLT(Offset off2) {
    return value.sLT(off2.value);
  }

  public boolean sLE(Offset off2) {
    return value.sLE(off2.value);
  }

  public boolean sGT(Offset off2) {
    return value.sGT(off2.value);
  }

  public boolean sGE(Offset off2) {
    return value.sGE(off2.value);
  }

  public boolean isZero() {
    return value.isZero();
  }

  public boolean isMax() {
    return EQ(max());
  }

  @Override
  public String toString() {
    return toWord().toString();
  }
}

