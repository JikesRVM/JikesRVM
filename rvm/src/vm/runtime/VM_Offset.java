/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * The offset type is used by the runtime system and collector to denote the directed distance between
 * two machine addresses. We use a separate type instead of the Java int type for coding clarity.
 *   machine-portability (it can map to 32 bit and 64 bit integral types), 
 *   and access to unsigned operations (Java does not have unsigned int types).
 *
 * For efficiency and to avoid meta-circularity, the VM_Offset class is intercepted like
 *   magic and converted into the base type so no VM_Offset object is created run-time.
 *
 * @author Perry Cheng
 * @see VM_Address VM_Word
 */
final public class VM_Offset implements VM_Uninterruptible {

  // Do not try to create a static field containing special offset values.
  //   Suboptimal code will be generated.

  private int value;

  public VM_Offset(int offset) {  
      if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
      value = offset;
  }

  public boolean equals(Object o) {
      return (o instanceof VM_Offset) && ((VM_Offset) o).value == value;
  }

  static public VM_Offset fromInt (int offset) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Offset(offset);
  }

  static public VM_Offset zero () throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Offset(0);
  }

  static public VM_Offset max() throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Offset(-1);
  }

  public int toInt () {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return value;
  }

  public long toLong () {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return 0x00000000ffffffffL & ((long) value);
  }

  public VM_Offset add (int byteSize) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Offset(value + byteSize);
  }

  public VM_Offset sub (int byteSize) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Offset(value - byteSize);
  }

  public VM_Word toWord() {
    return VM_Word.fromInt(value);
  }


}

