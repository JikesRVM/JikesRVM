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

import org.vmmagic.pragma.*;
import com.ibm.jikesrvm.VM;

/**
 * The offset type is used by the runtime system and collector to denote 
 * the directed distance between two machine addresses. 
 * We use a separate type instead of the Java int type for coding clarity.
 * machine-portability (it can map to 32 bit and 64 bit integral types), 
 * and access to unsigned operations (Java does not have unsigned int types).
 * <p>
 * For efficiency and to avoid meta-circularity, the Offset class is intercepted like
 * magic and converted into the base type so no Offset object is created run-time.
 *
 * @author Perry Cheng
 * @see Address Word
 */
@Uninterruptible public final class Offset {

  // Do not try to create a static field containing special offset values.
  //   Suboptimal code will be generated.

  //-#if RVM_FOR_32_ADDR
  private int value;
  //-#else
  private long value;
  //-#endif

  Offset(int offset) {  
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED); 
    //-#if RVM_FOR_32_ADDR
    value = offset;
    //-#else
    if (VM.VerifyAssertions) VM._assert(false);
    //-#endif
  }

  Offset(long offset) {  
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED); 
    //-#if RVM_FOR_32_ADDR
    if (VM.VerifyAssertions) VM._assert(false);
    //-#else
    value = offset;
    //-#endif
  }

  public boolean equals(Object o) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED); 
    return (o instanceof Offset) && ((Offset) o).value == value;
  }

  /**
   * @deprecated
   */
  public static Offset fromInt(int address) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(address);
  }

  public static Offset fromIntSignExtend(int address) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(address);
  }

  public static Offset fromIntZeroExtend(int address) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    if (VM.BuildFor32Addr)
      return new Offset(address);
    else {
      long val = ((long)address) & 0x00000000ffffffffL;
      return new Offset(val);
    }
  }

  public static Offset fromLong(long offset) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor64Addr);
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(offset);
  }

  public static Offset zero() throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(0);
  }

  public static Offset max() throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return fromIntSignExtend(-1);
  }

  public int toInt() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (int) value;
  }

  public long toLong() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    if (VM.BuildFor64Addr) {
      return value;
    } else {
      return 0x00000000ffffffffL & ((long) value);
    }
  }

  public Word toWord() throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value);
  }

  public Offset plus(int byteSize) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(value + byteSize);
  }

  public Offset plus(Offset off2) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(value + off2.value);
  }

  public Offset minus(int byteSize) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(value - byteSize);
  }

  public Offset minus(Offset off2) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Offset(value - off2.value);
  }

  public boolean EQ(Offset off2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value == off2.value;
  }

  public boolean NE(Offset off2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value != off2.value;
  }

  public boolean sLT(Offset off2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value < off2.value;
  }

  public boolean sLE(Offset off2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value <= off2.value;
  }

  public boolean sGT(Offset off2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value > off2.value;
  }

  public boolean sGE(Offset off2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value >= off2.value;
  }

  public boolean isZero() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return EQ(zero());
  }

  public boolean isMax() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return EQ(max());
  }
}

