/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * The word type is used by the runtime system and collector to denote machine 
 * word-sized quantities.
 * We use a separate type instead of the Java int type for coding clarity.
 * machine-portability (it can map to 32 bit and 64 bit integral types), 
 * and access to unsigned operations (Java does not have unsigned int types).
 * <p>
 * For efficiency and to avoid meta-circularity, the VM_Word class is intercepted like
 * magic and converted into the base type so no VM_Word object is created run-time.
 *
 * @author Perry Cheng
 * @see VM_Address
 */
public final class VM_Word implements VM_Uninterruptible {

  // Do not try to create a static field containing special values.
  //   Suboptimal code will be generated.

  //-#if RVM_FOR_32_ADDR
  private int value;  
  //-#elif RVM_FOR_64_ADDR
  private long value;  
  //-#endif

  //-#if RVM_FOR_32_ADDR
  VM_Word (int val) { 
    value = val; 
  }
  //-#elif RVM_FOR_64_ADDR
  VM_Word (long val) { 
    value = val; 
  }
  //-#endif

  public boolean equals(Object o) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED); 
    return (o instanceof VM_Word) && ((VM_Word) o).value == value;
  }

  /**
   * @deprecated
   */
  public static VM_Word fromInt (int val) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(val);
  }

  public static VM_Word fromIntSignExtend (int val) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(val);
  }
  
  public static VM_Word fromIntZeroExtend (int val) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new VM_Word(val);
    //-#elif RVM_FOR_64_ADDR
    long ans = ((long)val) & 0x00000000ffffffffL;
    return new VM_Word(ans);
    //-#endif
  }
     
  //-#if RVM_FOR_64_ADDR
  public static VM_Word fromLong (long val) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(val);
  }
  //-#endif

  public static VM_Word zero () throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(0);
  }

  public static VM_Word one () throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(1);
  }

  public static VM_Word max() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return fromIntSignExtend(-1);
  }

  public int toInt () {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (int) value;
  }

  public long toLong () throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    if (VM.BuildFor64Addr) {
      return value;
    } else {
      return 0x00000000ffffffffL & ((long) value);
    }
  }

  public VM_Address toAddress() throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Address(value);
  }

  public VM_Offset toOffset () throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Offset(value);
  }

  public VM_Extent toExtent () throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Extent(value);
  }

  public VM_Word add (VM_Word w2) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(value + w2.value);
  }

  public VM_Word add (VM_Offset w2) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(value + w2.toWord().value);
  }

  public VM_Word add (VM_Extent w2) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(value + w2.toWord().value);
  }

  public VM_Word sub (VM_Word w2) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(value - w2.value);
  }
  public VM_Word sub (VM_Offset w2) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(value - w2.toWord().value);
  }
  public VM_Word sub (VM_Extent w2) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(value - w2.toWord().value);
  }

  public boolean isZero() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return EQ(zero());
  }

  public boolean isMax() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return EQ(max());
  }

  public boolean LT (VM_Word addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    if (value >= 0 && addr2.value >= 0) return value < addr2.value;
    if (value < 0 && addr2.value < 0) return value < addr2.value;
    if (value < 0) return true;
    return false;
  }

  public boolean LE (VM_Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (value == w2.value) || LT(w2);
  }

  public boolean GT (VM_Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return w2.LT(this);
  }

  public boolean GE (VM_Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return w2.LE(this);
  }

  public boolean EQ (VM_Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value == w2.value;
  }

  public boolean NE (VM_Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return !EQ(w2);
  }

  public VM_Word and(VM_Word w2) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(value & w2.value);
  }

  public VM_Word or(VM_Word w2) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(value | w2.value);
  }

  public VM_Word not() throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(~value);
  }

  public VM_Word xor(VM_Word w2) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(value ^ w2.value);
  }

  public VM_Word lsh (int amt) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(value << amt);
  }

  public VM_Word rshl (int amt) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(value >>> amt);
  }

  public VM_Word rsha (int amt) throws VM_PragmaUninterruptibleNoWarn {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_Word(value >> amt);
  }

}

