/*
 * (C) Copyright Australian National University. 2004
 */
//$Id$
package org.vmmagic.unboxed;

/**
 * Stub implementation of an Address type. Needs commenting.
 *
 * @author Daniel Frampton
 */
public final class Address {

  public static Address fromInt(int address)  {
    return null;
  }

  public static Address fromIntSignExtend(int address)  {
    return null;
  }

  public static Address fromIntZeroExtend(int address)  {
    return null;
  }

  public static Address zero ()  {
    return null;
  }

  public static Address max() {
    return null;
  }

  public ObjectReference toObjectReference() {
    return null;
  }

  public int toInt() {
    return 0;
  }

  public long toLong () {
    return 0;
  }

  public Word toWord() {
    return null;
  }

  public Address add (int v)  {
    return null;
  }

  public Address add (Offset offset)  {
    return null;
  }

  public Address add (Extent extent)  {
    return null;
  }

  public Address sub (Extent extent)  {
    return null;
  }

  public Address sub (Offset offset)  {
    return null;
  }

  public Address sub (int v)  {
    return null;
  }

  public Offset diff (Address addr2)  {
    return null;
  }

  public boolean isZero() {
    return false;
  }

  public boolean isMax() {
    return false;
  }

  public boolean LT (Address addr2) {
    return false;
  }

  public boolean LE (Address addr2) {
    return false;
  }

  public boolean GT (Address addr2) {
    return false;
  }

  public boolean GE (Address addr2) {
    return false;
  }

  public boolean EQ (Address addr2) {
    return false;
  }

  public boolean NE (Address addr2) {
    return false;
  }

  /** 
   * Loads a reference from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public ObjectReference loadObjectReference() {
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
    return null;
  }

  /** 
   * Loads a byte from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public byte loadByte() {
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
    return (byte)0;
  }

  /** 
   * Loads a char from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public char loadChar() {
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
    return (char)0;
  }

  /** 
   * Loads a short from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public short loadShort() {
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
    return (short)0;
  }

  /**
   * Loads a float from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public float loadFloat() {
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
    return (float)0;
  }

  /** 
   * Loads an int from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public int loadInt() {
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
    return 0;
  }


  /** 
   * Loads a long from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public long loadLong() {
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
    return 0L;
  }

  /** 
   * Loads a double from the memory location pointed to by the
   * current instance.
   *
   * @return the read value
   */
  public double loadDouble() {
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
    return 0;
  }


  /** 
   * Loads an address value from the memory location pointed to by the
   * current instance.
   *
   * @return the read address value.
   */
  public Address loadAddress() {
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
    return null;
  }

  /** 
   * Loads a word value from the memory location pointed to by the
   * current instance.
   *
   * @return the read word value.
   */
  public Word loadWord() {
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
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @return the old value to be passed to an attempt call.
   */
  public Word prepareWord() {
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
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @return the old value to be passed to an attempt call.
   */
  public ObjectReference prepareObjectReference() {
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
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @return the old value to be passed to an attempt call.
   */
  public Address prepareAddress() {
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
    return null;
  }

  /**
   * Prepare for an atomic store operation. This must be associated with
   * a related call to attempt.
   *
   * @return the old value to be passed to an attempt call.
   */
  public int prepareInt() {
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
    return false;
  }

  /**
   * Stores the address value in the memory location pointed to by the
   * current instance.
   *
   * @param value The address value to store.
   */
  public void store(ObjectReference ref) {
  }

  /**
   * Stores the address value in the memory location pointed to by the
   * current instance.
   *
   * @param value The address value to store.
   * @param offset the offset to the value.
   */
  public void store(ObjectReference ref, Offset offset) {
  }
 
  /**
   * Stores the address value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The address value to store.
   */
  public void store(Address address) {
  }

  /**
   * Stores the address value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The address value to store.
   * @param offset the offset to the value.
   */
  public void store(Address address, Offset offset) {
  }

  /** 
   * Stores the float value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The float value to store.
   */
  public void store(float value) {
  }

  /**
   * Stores the float value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The float value to store.
   * @param offset the offset to the value.
   */
  public void store(float value, Offset offset) {
  }


  /**
   * Stores the word value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The word value to store.
   */
  public void store(Word value) {
  }

  /**
   * Stores the word value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The word value to store.
   * @param offset the offset to the value.
   */
  public void store(Word value, Offset offset) {
  }

  /**
   * Stores the byte value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The byte value to store.
   */
  public void store(byte value) {
  }

  /**
   * Stores the byte value in the memory location pointed to by the 
   * current instance.
   *
   * @param value The byte value to store.
   * @param offset the offset to the value.
   */
  public void store(byte value, Offset offset) {
  }


  /** 
   * Stores an int value in memory location pointed to by the
   * current instance.
   *
   * @param value The int value to store.
   */
  public void store(int value) {
  }

  /** 
   * Stores an int value in memory location pointed to by the
   * current instance.
   *
   * @param value The int value to store.
   * @param offset the offset to the value.
   */
  public void store(int value, Offset offset) {
  }

  /** 
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param value The double value to store.
   */
  public void store(double value) {
  }

  /** 
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param value The double value to store.
   * @param offset the offset to the value.
   */
  public void store(double value, Offset offset) {
  }


  /** 
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param value The double value to store.
   */
  public void store(long value) {
  }

  /** 
   * Stores a double value in memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @param value The double value to store.
   */
  public void store(long value, Offset offset) {
  }

  /** 
   * Stores a char value in the memory location pointed to by the
   * current instance.
   *
   * @param value the char value to store. 
   */
  public void store(char value) {
  }

  /** 
   * Stores a char value in the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @param value the char value to store. 
   */
  public void store(char value, Offset offset) {
  }

  /** 
   * Stores a short value in the memory location pointed to by the
   * current instance.
   *
   * @param value the short value to store. 
   */
  public void store(short value) {
  }

  /** 
   * Stores a short value in the memory location pointed to by the
   * current instance.
   *
   * @param offset the offset to the value.
   * @param value the short value to store. 
   */
  public void store(short value, Offset offset) {
  }
}
