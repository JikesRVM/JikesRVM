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
 * The extent type is used by the runtime system and collector to denote the
 * undirected distance between two machine addresses. It is most similar
 * to an unsigned int and as such, comparison are unsigned.
 * <p>
 * For efficiency and to avoid meta-circularity, the class is intercepted like
 * magic and converted into the base type so no objects are created run-time.
 *
 * @see Address Word Offset
 */
@Uninterruptible
public final class Extent extends ArchitecturalWord {

  Extent(int value) {
    super(value, false);
  }

  Extent(int value, boolean zeroExtend) {
    super(value, zeroExtend);
  }

  Extent(long value) {
    super(value);
  }

  /* Compensate for some java compilers helpfully defining this synthetically */
  @Interruptible
  public String toString() {
    return super.toString();
  }

  public boolean equals(Object o) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (o instanceof Extent) && ((Extent) o).value == value;
  }

  @UninterruptibleNoWarn
  public static Extent fromIntSignExtend(int address) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(address);
  }

  @UninterruptibleNoWarn
  public static Extent fromIntZeroExtend(int address) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(address, true);
  }

  @UninterruptibleNoWarn
  public static Extent fromLong(long offset) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(offset);
  }

  @UninterruptibleNoWarn
  public static Extent zero() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(0);
  }

  @UninterruptibleNoWarn
  public static Extent one() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(1);
  }

  @UninterruptibleNoWarn
  public static Extent max() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return fromIntSignExtend(-1);
  }

  public int toInt() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (int)value;
  }

  public long toLong() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    if (VM.BuildFor64Addr) {
      return value;
    } else {
      return 0x00000000ffffffffL & ((long) value);
    }
  }

  @UninterruptibleNoWarn
  public Word toWord() {
    return new Word(value);
  }

  @UninterruptibleNoWarn
  public Extent plus(int byteSize) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(value + byteSize);
  }

  @UninterruptibleNoWarn
  public Extent plus(Extent byteSize) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(value + byteSize.value);
  }

  @UninterruptibleNoWarn
  public Extent minus(int byteSize) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(value - byteSize);
  }

  @UninterruptibleNoWarn
  public Extent minus(Extent byteSize) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(value - byteSize.value);
  }

  public boolean LT(Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    if (value >= 0 && extent2.value >= 0) return value < extent2.value;
    if (value < 0 && extent2.value < 0) return value < extent2.value;
    if (value < 0) return false;
    return true;
  }

  public boolean LE(Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return (value == extent2.value) || LT(extent2);
  }

  public boolean GT(Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return extent2.LT(this);
  }

  public boolean GE(Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return extent2.LE(this);
  }

  public boolean EQ(Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return value == extent2.value;
  }

  public boolean NE(Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return !EQ(extent2);
  }

}

