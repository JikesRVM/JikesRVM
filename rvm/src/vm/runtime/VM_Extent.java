/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * The extent type is used by the runtime system and collector to denote the 
 * undirected distance between two machine addresses. It is most similar 
 * to an unsigned int and as such, comparison are unsigned.
 * <p>
 * For efficiency and to avoid meta-circularity, the class is intercepted like
 * magic and converted into the base type so no objects are created run-time.
 *
 * @author Perry Cheng
 * @see VM_Address VM_Word VM_Offset
 */
public final class VM_Extent implements VM_Uninterruptible {

  // Do not try to create a static field containing special offset values.
  //   Suboptimal code will be generated.

  //-#if RVM_FOR_32_ADDR
  private int value;
  //-#elif RVM_FOR_64_ADDR
  private long value;
  //-#endif

  //-#if RVM_FOR_32_ADDR
  VM_Extent(int offset) {  
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    value = offset;
  }
  //-#elif RVM_FOR_64_ADDR
  VM_Extent(long offset) {  
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    value = offset;
  }
  //-#endif


  public boolean equals(Object o) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (o instanceof VM_Extent) && ((VM_Extent) o).value == value;
  }

  /**
   * @deprecated
   */
  public static VM_Extent fromInt(int address) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Extent(address);
  }

  public static VM_Extent fromIntSignExtend(int address) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Extent(address);
  }

  public static VM_Extent fromIntZeroExtend(int address) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new VM_Extent(address);
    //-#elif RVM_FOR_64_ADDR
    long val = ((long)address) & 0x00000000ffffffffL;
    return new VM_Extent(val);
    //-#endif
  }

  //-#if RVM_FOR_64_ADDR
  public static VM_Extent fromLong (long offset) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Extent(offset);
  }
  //-#endif

  public static VM_Extent zero () throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Extent(0);
  }

  public static VM_Extent one () throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Extent(1);
  }

  public static VM_Extent max() throws VM_PragmaUninterruptibleNoWarn {
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

  public VM_Word toWord() throws VM_PragmaUninterruptibleNoWarn {
    return new VM_Word(value);
  }

  public VM_Extent add (int byteSize) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Extent(value + byteSize);
  }

  public VM_Extent sub (int byteSize) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Extent(value - byteSize);
  }

  public VM_Extent add (VM_Extent byteSize) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Extent(value + byteSize.value);
  }

  public VM_Extent sub (VM_Extent byteSize) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Extent(value - byteSize.value);
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

