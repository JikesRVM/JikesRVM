/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * The address type is used by the runtime system and collector to denote machine addresses.
 * We use a separate type instead of the Java int type for coding clarity.
 *   machine-portability (it can map to 32 bit and 64 bit integral types), 
 *   and access to unsigned operations (Java does not have unsigned int types).
 *
 * For efficiency and to avoid meta-circularity, the VM_Address class is intercepted like
 *   magic and converted into the base type so no VM_Address object is created run-time.
 *
 * @author Perry Cheng
 */

final public class VM_Address implements VM_Uninterruptible , VM_SizeConstants {

  // Do not try to create a static field containing special address values.
  //   Suboptimal code will be generated.

//-#if RVM_FOR_32_ADDR
  private int value;

  public VM_Address(int address) {
      if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
      value = address;
  }
//-#elif RVM_FOR_64_ADDR
  private long value;
  
  public VM_Address(long address) {
      if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
      value = address;
  }

  static public VM_Address fromLong (long address) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(address);  //TODO: implement in magic
  }
//-#endif

  public long toLong () throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    if (VM.BuildFor64Addr) {
      return value;
    } else {
      return 0x00000000ffffffffL & ((long) value);
    }
  }

  public boolean equals(Object o) {
      return (o instanceof VM_Address) && ((VM_Address) o).value == value;
  }
  
  /**
  * fromInt()
  * @deprecated use fromIntSignExtend() or fromIntZeroExtend()
  */
  static public VM_Address fromInt (int address) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(address);
  }

  static public VM_Address fromIntSignExtend (int address) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(address);
  }

  static public VM_Address fromIntZeroExtend (int address) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(address);
  }

  static public VM_Address zero () throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(0);
  }

  static public VM_Address max() throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(-1);
  }

  public int toInt () {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return (int) value;
  }

  public VM_Address add (int v) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(value + v);
  }

  public VM_Address add (VM_Offset offset) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(value + offset.toInt());
  }

  public VM_Address add (VM_Extent extent) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(value + extent.toInt());
  }

  public VM_Address sub (VM_Extent extent) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(value - extent.toInt());
  }

  public VM_Address sub (VM_Offset offset) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(value - offset.toInt());
  }

  public VM_Address sub (int v) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(value - v);
  }

  public VM_Offset diff (VM_Address addr2) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
//-#if RVM_FOR_32_ADDR
    return VM_Offset.fromInt(value - addr2.value);
//-#endif
//-#if RVM_FOR_64_ADDR
    return VM_Offset.fromLong(value - addr2.value);
//-#endif
  }

  public boolean isZero() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return EQ(zero());
  }

  public boolean isMax() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return EQ(max());
  }

  public boolean LT (VM_Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    if (value >= 0 && addr2.value >= 0) return value < addr2.value;
    if (value < 0 && addr2.value < 0) return value < addr2.value;
    if (value < 0) return false; 
    return true;
  }

  public boolean LE (VM_Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return (value == addr2.value) || LT(addr2);
  }

  public boolean GT (VM_Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return addr2.LT(this);
  }

  public boolean GE (VM_Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return addr2.LE(this);
  }

  public boolean EQ (VM_Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return value == addr2.value;
  }

  public boolean NE (VM_Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return !EQ(addr2);
  }

  public VM_Word toWord() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
//-#if RVM_FOR_32_ADDR
    return VM_Word.fromInt(value);
//-#endif
//-#if RVM_FOR_64_ADDR
    return VM_Word.fromLong(value);
//-#endif
  }
}

