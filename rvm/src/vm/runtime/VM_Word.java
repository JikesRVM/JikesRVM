/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * The word type is used by the runtime system and collector to denote machine word-sized quantities.
 * We use a separate type instead of the Java int type for coding clarity.
 *   machine-portability (it can map to 32 bit and 64 bit integral types), 
 *   and access to unsigned operations (Java does not have unsigned int types).
 *
 * For efficiency and to avoid meta-circularity, the VM_Word class is intercepted like
 *   magic and converted into the base type so no VM_Word object is created run-time.
 *
 * @author Perry Cheng
 * @see VM_Address
 */

final public class VM_Word implements VM_Uninterruptible {

  // Do not try to create a static field containing special values.
  //   Suboptimal code will be generated.

//-#if RVM_FOR_32_ADDR
  private int value;  
  private VM_Word (int val) { value = val; }
//-#endif

//-#if RVM_FOR_64_ADDR
  private long value;  
  private VM_Word (long val) { value = val; }
//-#endif

  public boolean equals(Object o) {
      return (o instanceof VM_Word) && ((VM_Word) o).value == value;
  }

  /**
  * fromInt()
  * @deprecated use fromIntSignExtend() or fromIntZeroExtend()
  */
  static public VM_Word fromInt (int val) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(val);
  }

//-#if RVM_FOR_64_ADDR
  static public VM_Word fromLong (long val) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(val);
  }
//-#endif

  static public VM_Word fromIntSignExtend (int val) throws VM_PragmaLogicallyUninterruptible {
    return VM_Address.fromIntSignExtend(val).toWord(); // TODO
  }
  
  static public VM_Word fromIntZeroExtend (int val) throws VM_PragmaLogicallyUninterruptible {
    return VM_Address.fromIntZeroExtend(val).toWord(); // TODO
  }
     
  static public VM_Word zero () throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(0);
  }

  static public VM_Word max() throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
//-#if RVM_FOR_32_ADDR
    return new VM_Word(-1);
//-#endif
//-#if RVM_FOR_64_ADDR
    return new VM_Word(-1L);
//-#endif
  }

  public int toInt () throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return (int) value;
  }

  public long toLong () throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    if (VM.BuildFor32Addr) {
      return 0x00000000ffffffffL & ((long) value);
    } else {
      return (long)value;
    }
  }

  public VM_Word add (VM_Word w2) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(value + w2.value);
  }

  public VM_Word add (VM_Offset w2) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(value + w2.toWord().value);
  }

  public VM_Word add (VM_Extent w2) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(value + w2.toWord().value);
  }

  public VM_Word sub (VM_Word w2) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(value - w2.value);
  }
  public VM_Word sub (VM_Offset w2) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(value - w2.toWord().value);
  }
  public VM_Word sub (VM_Extent w2) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(value - w2.toWord().value);
  }

  public boolean isZero() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return EQ(zero());
  }

  public boolean isMax() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return EQ(max());
  }

  public boolean LT (VM_Word addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    if (value >= 0 && addr2.value >= 0) return value < addr2.value;
    if (value < 0 && addr2.value < 0) return value < addr2.value;
    if (value < 0) return true;
    return false;
  }

  public boolean LE (VM_Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return (value == w2.value) || LT(w2);
  }

  public boolean GT (VM_Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return w2.LT(this);
  }

  public boolean GE (VM_Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return w2.LE(this);
  }

  public boolean EQ (VM_Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return value == w2.value;
  }

  public boolean NE (VM_Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return !EQ(w2);
  }

  public VM_Address toAddress() throws VM_PragmaLogicallyUninterruptible {
    return new VM_Address(value);
  }

  public VM_Word and(VM_Word w2) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(value & w2.value);
  }

  public VM_Word or(VM_Word w2) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(value | w2.value);
  }

  public VM_Word not() throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(~value);
  }

  public VM_Word xor(VM_Word w2) throws VM_PragmaLogicallyUninterruptible {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(value ^ w2.value);
  }

}

