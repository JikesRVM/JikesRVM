/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * The offset type is used by the runtime system and collector to denote 
 * the directed distance between two machine addresses. 
 * We use a separate type instead of the Java int type for coding clarity.
 * machine-portability (it can map to 32 bit and 64 bit integral types), 
 * and access to unsigned operations (Java does not have unsigned int types).
 * <p>
 * For efficiency and to avoid meta-circularity, the VM_Offset class is intercepted like
 * magic and converted into the base type so no VM_Offset object is created run-time.
 *
 * @author Perry Cheng
 * @see VM_Address VM_Word
 */
public final class VM_Offset implements VM_Uninterruptible {

  // Do not try to create a static field containing special offset values.
  //   Suboptimal code will be generated.

  //-#if RVM_FOR_32_ADDR
  private int value;
  //-#elif RVM_FOR_64_ADDR
  private long value;
  //-#endif

  //-#if RVM_FOR_32_ADDR
  VM_Offset(int offset) {  
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED); 
    value = offset;
  }
  //-#elif RVM_FOR_64_ADDR
  VM_Offset(long offset) {  
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED); 
    value = offset;
  }
  //-#endif

  public boolean equals(Object o) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED); 
    return (o instanceof VM_Offset) && ((VM_Offset) o).value == value;
  }

  /**
   * @deprecated
   */
  public static VM_Offset fromInt(int address) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Offset(address);
  }

  public static VM_Offset fromIntSignExtend(int address) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Offset(address);
  }

  public static VM_Offset fromIntZeroExtend(int address) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new VM_Offset(address);
    //-#elif RVM_FOR_64_ADDR
    long val = ((long)address) & 0x00000000ffffffffL;
    return new VM_Offset(val);
    //-#endif
  }

  //-#if RVM_FOR_64_ADDR
  public static VM_Offset fromLong (long offset) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Offset(offset);
  }
  //-#endif

  public static VM_Offset zero () throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Offset(0);
  }

  public static VM_Offset max() throws VM_PragmaUninterruptibleNoWarn {
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

  public VM_Word toWord() throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(value);
  }

  public VM_Offset add (int byteSize) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Offset(value + byteSize);
  }

  public VM_Offset sub (int byteSize) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Offset(value - byteSize);
  }

  public VM_Offset sub (VM_Offset off2) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Offset(value - off2.value);
  }

  public boolean EQ (VM_Offset off2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value == off2.value;
  }

  public boolean NE (VM_Offset off2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value != off2.value;
  }

  public boolean sLT (VM_Offset off2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value < off2.value;
  }

  public boolean sLE (VM_Offset off2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value <= off2.value;
  }

  public boolean sGT (VM_Offset off2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value > off2.value;
  }

  public boolean sGE (VM_Offset off2) {
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

