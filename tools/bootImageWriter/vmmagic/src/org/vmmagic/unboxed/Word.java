/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package org.vmmagic.unboxed; 

import com.ibm.jikesrvm.VM;
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
 * @author Perry Cheng
 * @modified Daniel Frampton
 * @see Address
 */
@Uninterruptible public final class Word extends ArchitecturalWord {
  Word(int value) {
    super(value, false);
  }
  Word(int value, boolean zeroExtend) {
    super(value, zeroExtend);
  }
  Word(long value) {
    super(value);
  }
  public boolean equals(Object o) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED); 
    return (o instanceof Word) && ((Word) o).value == value;
  }

  public static Word fromIntSignExtend(int val) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(val);
  }
  
  public static Word fromIntZeroExtend(int val) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(val, true);
  }
     
  public static Word fromLong(long val) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(val);
  }

  public static Word zero() throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(0);
  }

  public static Word one() throws UninterruptibleNoWarnPragma {
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

  public long toLong() throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    if (VM.BuildFor64Addr) {
      return value;
    } else {
      return 0x00000000ffffffffL & ((long) value);
    }
  }

  public Address toAddress() throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(value);
  }

  public Offset toOffset() throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(value);
  }

  public Extent toExtent() throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(value);
  }

  public Word plus(Word w2) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value + w2.value);
  }

  public Word plus(Offset w2) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value + w2.toWord().value);
  }

  public Word plus(Extent w2) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value + w2.toWord().value);
  }

  public Word minus(Word w2) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value - w2.value);
  }
  public Word minus(Offset w2) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value - w2.toWord().value);
  }
  public Word minus(Extent w2) throws UninterruptibleNoWarnPragma {
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

  public boolean LT (Word addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    if (value >= 0 && addr2.value >= 0) return value < addr2.value;
    if (value < 0 && addr2.value < 0) return value < addr2.value;
    if (value < 0) return true;
    return false;
  }

  public boolean LE (Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (value == w2.value) || LT(w2);
  }

  public boolean GT (Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return w2.LT(this);
  }

  public boolean GE (Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return w2.LE(this);
  }

  public boolean EQ (Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value == w2.value;
  }

  public boolean NE (Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return !EQ(w2);
  }

  public Word and(Word w2) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value & w2.value);
  }

  public Word or(Word w2) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value | w2.value);
  }

  public Word not() throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(~value);
  }

  public Word xor(Word w2) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value ^ w2.value);
  }

  public Word lsh (int amt) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value << amt);
  }

  public Word rshl (int amt) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value >>> amt);
  }

  public Word rsha (int amt) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value >> amt);
  }

}

