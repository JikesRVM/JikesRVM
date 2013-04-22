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
package org.vmmagic.unboxed.harness;

public final class ArchitecturalWord32 extends ArchitecturalWord {

  private final int value;
  private static final int SIGN_BIT = 1<<31;

  ArchitecturalWord32(int value) {
    assert getModel() == Architecture.BITS32;
    this.value = value;
  }

  @Override
  public boolean isZero() {
    return value == 0;
  }

  @Override
  public boolean isMax() {
    return value == 0xFFFFFFFF;
  }

  @Override
  public int toInt() {
    return value;
  }

  @Override
  public long toLongSignExtend() {
    return value;
  }

  @Override
  public long toLongZeroExtend() {
    return value & 0xFFFFFFFFL;
  }

  @Override
  public boolean EQ(ArchitecturalWord word) {
    return value == word.toInt();
  }

  /**
   * Unsigned comparison - flip bit 31 and then use signed comparison.
   */
  @Override
  public boolean LT(ArchitecturalWord word) {
    return (value ^ SIGN_BIT) < (word.toInt() ^ SIGN_BIT);
  }

  @Override
  public ArchitecturalWord minus(long offset) {
    return fromLong(value-(int)offset);
  }

  @Override
  public ArchitecturalWord plus(long offset) {
    return fromLong(value + (int)offset);
  }

  @Override
  public ArchitecturalWord and(ArchitecturalWord w) {
    return fromLong(value & w.toInt());
  }

  @Override
  public ArchitecturalWord lsh(int amt) {
    return fromLong(value << amt);
  }

  @Override
  public ArchitecturalWord not() {
    return fromLong(~value);
  }

  @Override
  public ArchitecturalWord or(ArchitecturalWord w) {
    return fromLong(value | w.toInt());
  }

  @Override
  public ArchitecturalWord rsha(int amt) {
    return fromLong(value >> amt);
  }

  @Override
  public ArchitecturalWord rshl(int amt) {
    return fromLong(value >>> amt);
  }

  @Override
  public ArchitecturalWord xor(ArchitecturalWord w) {
    return fromLong(value ^ w.toInt());
  }

  @Override
  public ArchitecturalWord diff(ArchitecturalWord w) {
    return fromLong(value - w.toInt());
  }

  @Override
  public boolean sLT(ArchitecturalWord word) {
    return value < word.toInt();
  }

  /**
   * Create a string representation of the given int value as an address.
   */
  @Override
  public String toString() {
    char[] chars = new char[10];
    int v = value;
    chars[0] = '0';
    chars[1] = 'x';
    for(int x = 9; x > 1; x--) {
      int thisValue = v & 0x0F;
      if (thisValue > 9) {
        chars[x] = (char)('A' + thisValue - 10);
      } else {
        chars[x] = (char)('0' + thisValue);
      }
      v >>>= 4;
    }
    return new String(chars);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + value;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!super.equals(obj))
      return false;
    if (getClass() != obj.getClass())
      return false;
    ArchitecturalWord32 other = (ArchitecturalWord32) obj;
    if (value != other.value)
      return false;
    return true;
  }



}
