/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * The address type is used by the runtime system and collector to denote machine addresses.
 * We use a separate type instead of the Java int type for coding clarity.
 * machine-portability (it can map to 32 bit and 64 bit integral types), 
 * and access to unsigned operations (Java does not have unsigned int types).
 * <p>
 * For efficiency and to avoid meta-circularity, the VM_Address class is intercepted like
 * magic and converted into the base type so no VM_Address object is created run-time.
 *
 * @author Perry Cheng
 */
public final class VM_Address implements VM_Uninterruptible , VM_SizeConstants {

  // Do not try to create a static field containing special address values.
  //   Suboptimal code will be generated.

  //-#if RVM_FOR_32_ADDR
  private int value;
  //-#elif RVM_FOR_64_ADDR
  private long value;
  //-#endif

  //-#if RVM_FOR_32_ADDR
  VM_Address(int address) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    value = address;
  }
  //-#elif RVM_FOR_64_ADDR
  VM_Address(long address) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    value = address;
  }
  //-#endif

  public boolean equals(Object o) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (o instanceof VM_Address) && ((VM_Address) o).value == value;
  }

  /**
   * @deprecated
   */
  public static VM_Address fromInt (int address) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Address(address);
  }

  public static VM_Address fromIntSignExtend (int address) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Address(address);
  }

  public static VM_Address fromIntZeroExtend (int address) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new VM_Address(address);
    //-#elif RVM_FOR_64_ADDR
    long val = ((long)address) & 0x00000000ffffffffL;
    return new VM_Address(val);
    //-#endif
  }

  //-#if RVM_FOR_64_ADDR
  public static VM_Address fromLong (long address) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED); 
    return new VM_Address(address);
  }
  //-#endif

  public static VM_Address zero () throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Address(0);
  }

  public static VM_Address max() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return fromIntSignExtend(-1);
  }

  public int toInt () {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (int) value;
  }

  public long toLong () {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    if (VM.BuildFor64Addr) {
      return value;
    } else {
      return 0x00000000ffffffffL & ((long) value);
    }
  }

  public VM_Word toWord() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(value);
  }

  public VM_Address add (int v) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Address(value + v);
  }

  public VM_Address add (VM_Offset offset) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new VM_Address(value + offset.toInt());
    //-#elif RVM_FOR_64_ADDR
    return new VM_Address(value + offset.toLong());
    //-#endif
  }

  public VM_Address add (VM_Extent extent) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new VM_Address(value + extent.toInt());
    //-#elif RVM_FOR_64_ADDR
    return new VM_Address(value + extent.toLong());
    //-#endif
  }

  public VM_Address sub (VM_Extent extent) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new VM_Address(value - extent.toInt());
    //-#elif RVM_FOR_64_ADDR
    return new VM_Address(value - extent.toLong());
    //-#endif
  }

  public VM_Address sub (VM_Offset offset) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new VM_Address(value - offset.toInt());
    //-#elif RVM_FOR_64_ADDR
    return new VM_Address(value - offset.toLong());
    //-#endif
  }

  public VM_Address sub (int v) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Address(value - v);
  }

  public VM_Offset diff (VM_Address addr2) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return VM_Offset.fromIntZeroExtend(value - addr2.value);
    //-#elif RVM_FOR_64_ADDR
    return VM_Offset.fromLong(value - addr2.value);
    //-#endif
  }

  public boolean isZero() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return EQ(zero());
  }

  public boolean isMax() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return EQ(max());
  }

  public boolean LT (VM_Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    if (value >= 0 && addr2.value >= 0) return value < addr2.value;
    if (value < 0 && addr2.value < 0) return value < addr2.value;
    if (value < 0) return false; 
    return true;
  }

  public boolean LE (VM_Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (value == addr2.value) || LT(addr2);
  }

  public boolean GT (VM_Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return addr2.LT(this);
  }

  public boolean GE (VM_Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return addr2.LE(this);
  }

  public boolean EQ (VM_Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value == addr2.value;
  }

  public boolean NE (VM_Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return !EQ(addr2);
  }
}

