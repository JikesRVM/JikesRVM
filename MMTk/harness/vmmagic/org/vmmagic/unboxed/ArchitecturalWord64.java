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

public final class ArchitecturalWord64 extends ArchitecturalWord {

  private final long value;
  private static final long SIGN_BIT = 1L<<63;

  ArchitecturalWord64(long value) {
    assert getModel() == Architecture.BITS64;
    this.value = value;
  }

  @Override
  public boolean isZero() {
    return value == 0;
  }

  @Override
  public boolean isMax() {
    return value == -1;
  }

  @Override
  public int toInt() {
    return (int)(value & 0x00000000FFFFFFFFL);
  }

  @Override
  public long toLongSignExtend() {
    return value;
  }

  public long toLongZeroExtend() {
    return value;
  }

  @Override
  boolean EQ(ArchitecturalWord word) {
    return value == word.toLongSignExtend();
  }

  @Override
  boolean LT(ArchitecturalWord word) {
    return (value ^ SIGN_BIT) < (word.toLongSignExtend() ^ SIGN_BIT);
  }

  @Override
  ArchitecturalWord minus(long offset) {
    return fromLong(value-offset);
  }

  @Override
  ArchitecturalWord plus(long offset) {
    return fromLong(value + offset);
  }

  @Override
  ArchitecturalWord and(ArchitecturalWord w) {
    return fromLong(value & w.toLongSignExtend());
  }

  @Override
  ArchitecturalWord lsh(int amt) {
    return fromLong(value << amt);
  }

  @Override
  ArchitecturalWord not() {
    return fromLong(~value);
  }

  @Override
  ArchitecturalWord or(ArchitecturalWord w) {
    return fromLong(value | w.toLongSignExtend());
  }

  @Override
  ArchitecturalWord rsha(int amt) {
    return fromLong(value >> amt);
  }

  @Override
  ArchitecturalWord rshl(int amt) {
    return fromLong(value >>> amt);
  }

  @Override
  ArchitecturalWord xor(ArchitecturalWord w) {
    return fromLong(value ^ w.toLongSignExtend());
  }

  @Override
  ArchitecturalWord diff(ArchitecturalWord w) {
    return fromLong(value - w.toLongSignExtend());
  }

  @Override
  boolean sLT(ArchitecturalWord word) {
    return value < word.toLongSignExtend();
  }

  /**
   * Create a string representation of the given int value as an address.
   */
  public String toString() {
    char[] chars = new char[18];
    long v = value;
    chars[0] = '0';
    chars[1] = 'x';
    for(int x = 17; x > 1; x--) {
      long thisValue = v & 0x0FL;
      if (thisValue > 9) {
        chars[x] = (char)('A' + thisValue - 10);
      } else {
        chars[x] = (char)('0' + thisValue);
      }
      v >>>= 4;
    }
    return new String(chars);
  }

}
