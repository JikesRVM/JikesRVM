/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package org.vmmagic.unboxed;

import org.vmmagic.pragma.*;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_SizeConstants;

/**
 * The address type is used by the runtime system and collector to denote machine addresses.
 * We use a separate type instead of the Java int type for coding clarity.
 * machine-portability (it can map to 32 bit and 64 bit integral types), 
 * and access to unsigned operations (Java does not have unsigned int types).
 * <p>
 * For efficiency and to avoid meta-circularity, the Address class is intercepted like
 * magic and converted into the base type so no Address object is created run-time.
 *
 * @author Perry Cheng
 * @modified Daniel Frampton
 */
public final class Address implements Uninterruptible, VM_SizeConstants {

  // Do not try to create a static field containing special address values.
  //   Suboptimal code will be generated.

  //-#if RVM_FOR_32_ADDR
  private int value;
  //-#elif RVM_FOR_64_ADDR
  private long value;
  //-#endif

  //-#if RVM_FOR_32_ADDR
  Address(int address) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    value = address;
  }
  //-#elif RVM_FOR_64_ADDR
  Address(long address) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    value = address;
  }
  //-#endif

  public boolean equals(Object o) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (o instanceof Address) && ((Address) o).value == value;
  }

  /**
   * @deprecated
   */
  public static Address fromInt(int address) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(address);
  }

  public static Address fromIntSignExtend(int address) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(address);
  }

  public static Address fromIntZeroExtend(int address) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new Address(address);
    //-#elif RVM_FOR_64_ADDR
    long val = ((long)address) & 0x00000000ffffffffL;
    return new Address(val);
    //-#endif
  }

  //-#if RVM_FOR_64_ADDR
  public static Address fromLong (long address) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED); 
    return new Address(address);
  }
  //-#endif

  public static Address zero () throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(0);
  }

  public static Address max() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return fromIntSignExtend(-1);
  }

  public ObjectReference toObjectReference() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return null;
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

  public Word toWord() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Word(value);
  }

  public Address add (int v) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(value + v);
  }

  public Address add (Offset offset) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new Address(value + offset.toInt());
    //-#elif RVM_FOR_64_ADDR
    return new Address(value + offset.toLong());
    //-#endif
  }

  public Address add (Extent extent) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new Address(value + extent.toInt());
    //-#elif RVM_FOR_64_ADDR
    return new Address(value + extent.toLong());
    //-#endif
  }

  public Address sub (Extent extent) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new Address(value - extent.toInt());
    //-#elif RVM_FOR_64_ADDR
    return new Address(value - extent.toLong());
    //-#endif
  }

  public Address sub (Offset offset) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return new Address(value - offset.toInt());
    //-#elif RVM_FOR_64_ADDR
    return new Address(value - offset.toLong());
    //-#endif
  }

  public Address sub (int v) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new Address(value - v);
  }

  public Offset diff (Address addr2) throws UninterruptibleNoWarnPragma {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    //-#if RVM_FOR_32_ADDR
    return Offset.fromIntZeroExtend(value - addr2.value);
    //-#elif RVM_FOR_64_ADDR
    return Offset.fromLong(value - addr2.value);
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

  public boolean LT (Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    if (value >= 0 && addr2.value >= 0) return value < addr2.value;
    if (value < 0 && addr2.value < 0) return value < addr2.value;
    if (value < 0) return false; 
    return true;
  }

  public boolean LE (Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return (value == addr2.value) || LT(addr2);
  }

  public boolean GT (Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return addr2.LT(this);
  }

  public boolean GE (Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return addr2.LE(this);
  }

  public boolean EQ (Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return value == addr2.value;
  }

  public boolean NE (Address addr2) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return !EQ(addr2);
  }

  /** 
   * Loads a reference from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public ObjectReference loadObjectReference() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Loads a reference from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public ObjectReference loadObjectReference(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /** 
   * Loads a byte from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public byte loadByte() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return (byte)0;
  }

  /** 
   * Loads a byte from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public byte loadByte(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return (byte)0;
  }

  /** 
   * Loads a char from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public char loadChar() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return (char)0;
  }

  /** 
   * Loads a char from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public char loadChar(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return (char)0;
  }

  /** 
   * Loads a short from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public short loadShort() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return (short)0;
  }

  /** 
   * Loads a short from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public short loadShort(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return (short)0;
  }

  /**
   * Loads a float from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public float loadFloat() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return (float)0;
  }

  /** 
   * Loads a float from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public float loadFloat(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return (float)0;
  }

  /** 
   * Loads an int from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public int loadInt() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return 0;
  }

  /** 
   * Loads an int from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public int loadInt(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return 0;
  }


  /** 
   * Loads a long from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public long loadLong() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return 0L;
  }

  /** 
   * Loads a long from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public long loadLong(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return 0L;
  }

  /** 
   * Loads a double from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public double loadDouble() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return 0;
  }

  /** 
   * Loads a double from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read value
   */
  public double loadDouble(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return 0;
  }


  /** 
   * Loads an address value from the memory location pointed to by the
   * current instance.
   *
   * @return the read address value.
   */
  public Address loadAddress() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /** 
   * Loads an address value from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read address value.
   */
  public Address loadAddress(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /** 
   * Loads a word value from the memory location pointed to by the
   * current instance.
   *
   * @return the read word value.
   */
  public Word loadWord() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /** 
   * Loads a word value from the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @return the read word value.
   */
  public Word loadWord(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @return the old value to be passed to an attempt call.
   */
  public Word prepareWord() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @param offset the offset to the value.
   * @return the old value to be passed to an attempt call.
   */
  public Word prepareWord(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @return the old value to be passed to an attempt call.
   */
  public ObjectReference prepareObjectReference() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @param offset the offset to the value.
   * @return the old value to be passed to an attempt call.
   */
  public ObjectReference prepareObjectReference(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @return the old value to be passed to an attempt call.
   */
  public Address prepareAddress() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @param offset the offset to the value.
   * @return the old value to be passed to an attempt call.
   */
  public Address prepareAddress(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @return the old value to be passed to an attempt call.
   */
  public int prepareInt() {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return 0;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @param offset the offset to the value.
   * @return the old value to be passed to an attempt call.
   */
  public int prepareInt(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return 0;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param word the new value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(int old, int value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param word the new value.
   * @param offset the offset to the value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(int old, int value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param value the new value.
   * @return true if the attempt was successful.
   */ 
  public boolean attempt(Word old, Word value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param value the new value.
   * @param offset the offset to the value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(Word old, Word value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param value the new value.
   * @return true if the attempt was successful.
   */ 
  public boolean attempt(ObjectReference old, ObjectReference value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param value the new value.
   * @param offset the offset to the value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(ObjectReference old, ObjectReference value, 
                         Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param value the new value.
   * @return true if the attempt was successful.
   */ 
  public boolean attempt(Address old, Address value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  /**
   * Attempt an atomic store operation. This must be associated with a
   * related call to prepare.
   *
   * @param old the old value.
   * @param value the new value.
   * @param offset the offset to the value.
   * @return true if the attempt was successful.
   */
  public boolean attempt(Address old, Address value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  /**
   * Stores the address value in the memory location pointed to by the
   * current instance.
   *
   * @param value The address value to store.
   */
  public void store(ObjectReference ref) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores the address value in the memory location pointed to by the
   * current instance.
   *
   * @param value The address value to store.
   * @param offset the offset to the value.
   */
  public void store(ObjectReference ref, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }
 
  /**
   * Stores the address value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The address value to store.
   */
  public void store(Address address) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores the address value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The address value to store.
   * @param offset the offset to the value.
   */
  public void store(Address address, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores the float value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The float value to store.
   */
  public void store(float value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores the float value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The float value to store.
   * @param offset the offset to the value.
   */
  public void store(float value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }


  /**
   * Stores the word value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The word value to store.
   */
  public void store(Word value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores the word value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The word value to store.
   * @param offset the offset to the value.
   */
  public void store(Word value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores the byte value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The byte value to store.
   */
  public void store(byte value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Stores the byte value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The byte value to store.
   * @param offset the offset to the value.
   */
  public void store(byte value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }


  /** 
   * Stores an int value in memory location pointed to by the
   * current instance.
   *
   * @param value The int value to store.
   */
  public void store(int value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores an int value in memory location pointed to by the
   * current instance.
   *
   * @param value The int value to store.
   * @param offset the offset to the value.
   */
  public void store(int value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param value The double value to store.
   */
  public void store(double value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param value The double value to store.
   * @param offset the offset to the value.
   */
  public void store(double value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }


  /** 
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param value The double value to store.
   */
  public void store(long value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @param value The double value to store.
   */
  public void store(long value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores a char value in the memory location pointed to by the
   * current instance.
   *
   * @param value the char value to store. 
   */
  public void store(char value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores a char value in the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @param value the char value to store. 
   */
  public void store(char value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores a short value in the memory location pointed to by the
   * current instance.
   *
   * @param value the short value to store. 
   */
  public void store(short value) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /** 
   * Stores a short value in the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @param value the short value to store. 
   */
  public void store(short value, Offset offset) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }
}
