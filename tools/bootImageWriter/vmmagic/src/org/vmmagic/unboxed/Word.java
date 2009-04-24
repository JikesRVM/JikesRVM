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

import org.jikesrvm.VM;
import org.vmmagic.pragma.*;

/**
 * The word type is used by the runtime system and collector to denote machine
 * word-sized quantities.
 * We use a separate type instead of the Java int type for coding clarity.
 * machine-portability (it can map to 32 bit and 64 bit integral types),
 * and access to unsigned operations (Java does not have unsigned int types).
 * <p>
 * For efficiency and to avoid meta-circularity, the Word class is intercepted like
 * magic and converted into the base type so no Word object is created run-time.
 *
 * @see Address
 */
@Uninterruptible
public final class Word extends ArchitecturalWord {
  Word(int value) {
    super(value, false);
  }

  Word(int value, boolean zeroExtend) {
    super(value, zeroExtend);
  }

  Word(long value) {
    super(value);
  }

  /* Compensate for some java compilers helpfully defining this synthetically */
  @Interruptible
  public String toString() {
    return super.toString();
  }

  public boolean equals(Object o) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (o instanceof Word) && ((Word) o).value == value;
  }

  @UninterruptibleNoWarn
  public static Word fromIntSignExtend(int val) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(val);
  }

  @UninterruptibleNoWarn
  public static Word fromIntZeroExtend(int val) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(val, true);
  }

  @UninterruptibleNoWarn
  public static Word fromLong(long val) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(val);
  }

  @UninterruptibleNoWarn
  public static Word zero() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(0);
  }

  @UninterruptibleNoWarn
  public static Word one() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(1);
  }

  public static Word max() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return fromIntSignExtend(-1);
  }

  public int toInt() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (int) value;
  }

  @UninterruptibleNoWarn
  public long toLong() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    if (VM.BuildFor64Addr) {
      return value;
    } else {
      return 0x00000000ffffffffL & ((long) value);
    }
  }

  @UninterruptibleNoWarn
  public Address toAddress() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(value);
  }

  @UninterruptibleNoWarn
  public Offset toOffset() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(value);
  }

  @UninterruptibleNoWarn
  public Extent toExtent() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(value);
  }

  @UninterruptibleNoWarn
  public Word plus(Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value + w2.value);
  }

  @UninterruptibleNoWarn
  public Word plus(Offset w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value + w2.toWord().value);
  }

  @UninterruptibleNoWarn
  public Word plus(Extent w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value + w2.toWord().value);
  }

  @UninterruptibleNoWarn
  public Word minus(Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value - w2.value);
  }
  @UninterruptibleNoWarn
  public Word minus(Offset w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value - w2.toWord().value);
  }
  @UninterruptibleNoWarn
  public Word minus(Extent w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value - w2.toWord().value);
  }

  public boolean isZero() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return EQ(zero());
  }

  public boolean isMax() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return EQ(max());
  }

  public boolean LT(Word addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    if (value >= 0 && addr2.value >= 0) return value < addr2.value;
    if (value < 0 && addr2.value < 0) return value < addr2.value;
    if (value < 0) return true;
    return false;
  }

  public boolean LE(Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (value == w2.value) || LT(w2);
  }

  public boolean GT(Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return w2.LT(this);
  }

  public boolean GE(Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return w2.LE(this);
  }

  public boolean EQ(Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value == w2.value;
  }

  public boolean NE(Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return !EQ(w2);
  }

  @UninterruptibleNoWarn
  public Word and(Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value & w2.value);
  }

  @UninterruptibleNoWarn
  public Word or(Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value | w2.value);
  }

  @UninterruptibleNoWarn
  public Word not() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(~value);
  }

  @UninterruptibleNoWarn
  public Word xor(Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value ^ w2.value);
  }

  @UninterruptibleNoWarn
  public Word lsh(int amt) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value << amt);
  }

  @UninterruptibleNoWarn
  public Word rshl(int amt) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value >>> amt);
  }

  @UninterruptibleNoWarn
  public Word rsha(int amt) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value >> amt);
  }
}
