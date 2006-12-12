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
 * The extent type is used by the runtime system and collector to denote the 
 * undirected distance between two machine addresses. It is most similar 
 * to an unsigned int and as such, comparison are unsigned.
 * <p>
 * For efficiency and to avoid meta-circularity, the class is intercepted like
 * magic and converted into the base type so no objects are created run-time.
 *
 * @author Perry Cheng
 * @see Address Word Offset
 */
@Uninterruptible public final class Extent extends ArchitecturalWord {
  Extent(int value) {
    super(value, false);
  }
  Extent(int value, boolean zeroExtend) {
    super(value, zeroExtend);
  }
  Extent(long value) {
    super(value);
  }

  public boolean equals(Object o) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (o instanceof Extent) && ((Extent) o).value == value;
  }

  public static Extent fromIntSignExtend(int address) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(address);
  }

  public static Extent fromIntZeroExtend(int address) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(address, true);
  }

  public static Extent fromLong (long offset) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(offset);
  }

  public static Extent zero () throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(0);
  }

  public static Extent one () throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(1);
  }

  public static Extent max() throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return fromIntSignExtend(-1);
  }

  public int toInt () {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (int)value;
  }

  public long toLong () {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    if (VM.BuildFor64Addr) {
      return value;
    } else {
      return 0x00000000ffffffffL & ((long) value);
    }
  }

  public Word toWord() throws UninterruptibleNoWarnPragma {
    return new Word(value);
  }

  public Extent plus(int byteSize) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(value + byteSize);
  }

  public Extent plus(Extent byteSize) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(value + byteSize.value);
  }

  public Extent minus(int byteSize) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(value - byteSize);
  }

  public Extent minus(Extent byteSize) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Extent(value - byteSize.value);
  }

  public boolean LT (Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    if (value >= 0 && extent2.value >= 0) return value < extent2.value;
    if (value < 0 && extent2.value < 0) return value < extent2.value;
    if (value < 0) return false; 
    return true;
  }

  public boolean LE (Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return (value == extent2.value) || LT(extent2);
  }

  public boolean GT (Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return extent2.LT(this);
  }

  public boolean GE (Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return extent2.LE(this);
  }

  public boolean EQ (Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return value == extent2.value;
  }

  public boolean NE (Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return !EQ(extent2);
  }

}

