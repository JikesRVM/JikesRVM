/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * The extent type is used by the runtime system and collector to denote the undirected distance between
 * two machine addresses. It is most similar to an unsigned int and as such, comparison are unsigned.
 *
 * For efficiency and to avoid meta-circularity, the class is intercepted like
 *   magic and converted into the base type so no objects are created run-time.
 *
 * @author Perry Cheng
 * @see VM_Address VM_Word VM_Offset
 */
final public class VM_Extent implements VM_Uninterruptible {

  // Do not try to create a static field containing special offset values.
  //   Suboptimal code will be generated.

  private int value;

  public VM_Extent(int offset) {  
      if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
      value = offset;
  }

  static public VM_Extent fromInt (int offset) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Extent(offset);
  }

  static public VM_Extent zero () throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Extent(0);
  }

  static public VM_Extent max() throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Extent(-1);
  }

  public int toInt () {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return value;
  }

  public long toLong () {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return 0x00000000ffffffffL & ((long) value);
  }

  public VM_Extent add (int byteSize) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Extent(value + byteSize);
  }

  public VM_Extent sub (int byteSize) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Extent(value - byteSize);
  }

  public VM_Extent add (VM_Extent byteSize) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Extent(value + byteSize.toInt());
  }

  public VM_Extent sub (VM_Extent byteSize) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Extent(value - byteSize.toInt());
  }

  public VM_Word toWord() {
    return VM_Word.fromInt(value);
  }


  public boolean LT (VM_Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    if (value >= 0 && extent2.value >= 0) return value < extent2.value;
    if (value < 0 && extent2.value < 0) return value < extent2.value;
    if (value < 0) return false; 
    return true;
  }

  public boolean LE (VM_Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return (value == extent2.value) || LT(extent2);
  }

  public boolean GT (VM_Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return extent2.LT(this);
  }

  public boolean GE (VM_Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return extent2.LE(this);
  }

  public boolean EQ (VM_Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return value == extent2.value;
  }

  public boolean NE (VM_Extent extent2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return !EQ(extent2);
  }

}

