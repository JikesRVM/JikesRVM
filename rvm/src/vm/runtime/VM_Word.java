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

final public class VM_Word {

  // Do not try to create a static field containing special values.
  //   Suboptimal code will be generated.

  private int value;  

  public boolean equals(Object o) {
      return (o instanceof VM_Word) && ((VM_Word) o).value == value;
  }

  private VM_Word (int val) {
    value = val;
  }

  static public VM_Word fromInt (int val) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(val);
  }

  static public VM_Word zero () {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(0);
  }

  static public VM_Word max() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(-1);
  }

  public int toInt () {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return value;
  }

  public VM_Word add (VM_Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(value + w2.value);
  }

  public VM_Word sub (VM_Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(value - w2.value);
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

  public VM_Address toAddress() {
    return new VM_Address(value);
  }

  public VM_Word and(VM_Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(value & w2.value);
  }

  public VM_Word or(VM_Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(value | w2.value);
  }

  public VM_Word not() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(~value);
  }

  public VM_Word xor(VM_Word w2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Word(value ^ w2.value);
  }

}

