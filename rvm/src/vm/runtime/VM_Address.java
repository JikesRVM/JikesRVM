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

final public class VM_Address {

  // Do not try to create a static field containing special address values.
  //   Suboptimal code will be generated.

  private int value;

  // must be public for jdp's sake
  public VM_Address(int address) {  
      if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
      value = address;
  }

  public boolean equals(Object o) {
      return (o instanceof VM_Address) && ((VM_Address) o).value == value;
  }

  static public VM_Address fromInt (int address) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(address);
  }

  static public VM_Address zero () {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(0);
  }

  static public VM_Address max() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(-1);
  }

  public int toInt () {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return value;
  }

  public VM_Address add (int v) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(value + v);
  }

  public VM_Address add (VM_Offset offset) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(value + offset.toInt());
  }

  public VM_Address sub (VM_Offset offset) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(value - offset.toInt());
  }

  public VM_Address sub (int v) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return new VM_Address(value - v);
  }

  public VM_Offset diff (VM_Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // call site should have been hijacked by magic in compiler
    return VM_Offset.fromInt(value - addr2.value);
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
    if (value < 0) return true;
    return false;
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
    return VM_Word.fromInt(value);
  }

}

