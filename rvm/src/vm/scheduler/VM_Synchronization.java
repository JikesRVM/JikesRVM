/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Class to provide synchronization methods where java language 
 * synchronization is insufficient and VM_Magic.prepare and VM_Magic.attempt 
 * are at too low a level
 *
 * Do not add a method to this class without there is a compelling performance
 * or correctness need.
 *
 * 07/25/2000 Bowen Alpern and Tony Cocchi (initially stolen from VM_MagicMacros by Mauricio J. Serrano)
 *
 * @author Bowen Alpern
 * @author Anthony Cocchi
 */
public class VM_Synchronization implements VM_Uninterruptible {

  public static final boolean testAndSet(Object base, int offset, int newValue) throws VM_PragmaInline {
    int oldValue;
    do {
      oldValue = VM_Magic.prepare(base, offset);
      if (oldValue != 0) return false;
    } while (!VM_Magic.attempt(base, offset, oldValue, newValue));
    return true;
  }

  public static final int fetchAndStore(Object base, int offset, int newValue) throws VM_PragmaInline {
    int oldValue;
    do {
      oldValue = VM_Magic.prepare(base, offset);
    } while (!VM_Magic.attempt(base, offset, oldValue, newValue));
    return oldValue;
  }

  public static final VM_Address fetchAndStoreAddress(Object base, int offset, VM_Address newValue) throws VM_PragmaInline {
    VM_Address oldValue;
    do {
      oldValue = VM_Address.fromInt(VM_Magic.prepare(base, offset));
    } while (!VM_Magic.attempt(base, offset, oldValue.toInt(), newValue.toInt()));
    return oldValue;
  }

  public static final int fetchAndAdd(Object base, int offset, int increment) throws VM_PragmaInline {
    int oldValue;
    do {
      oldValue = VM_Magic.prepare(base, offset);
    } while (!VM_Magic.attempt(base, offset, oldValue, oldValue+increment));
    return oldValue;
  }

  public static final int fetchAndDecrement(Object base, int offset, int decrement) throws VM_PragmaInline {
    int oldValue;
    do {
      oldValue = VM_Magic.prepare(base, offset);
    } while (!VM_Magic.attempt(base, offset, oldValue, oldValue-decrement));
    return oldValue;
  }

  public static final VM_Address fetchAndAddAddress(VM_Address addr, int increment) throws VM_PragmaInline {
    VM_Address oldValue;
    do {
      oldValue = VM_Address.fromInt(VM_Magic.prepare(VM_Magic.addressAsObject(addr), 0));
    } while (!VM_Magic.attempt(VM_Magic.addressAsObject(addr), 0, oldValue.toInt(), oldValue.add(increment).toInt()));
    return oldValue;
  }

  public static final VM_Address fetchAndAddAddressWithBound(VM_Address addr, int increment, VM_Address bound) throws VM_PragmaInline {
    VM_Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(increment > 0);
    do {
      oldValue = VM_Address.fromInt(VM_Magic.prepare(VM_Magic.addressAsObject(addr), 0));
      newValue = oldValue.add(increment);
      if (newValue.GT(bound)) return VM_Address.max();
    } while (!VM_Magic.attempt(VM_Magic.addressAsObject(addr), 0, oldValue.toInt(), newValue.toInt()));
    return oldValue;
  }

  public static final VM_Address fetchAndSubAddressWithBound(VM_Address addr, int decrement, VM_Address bound) throws VM_PragmaInline {
    VM_Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(decrement > 0);
    do {
      oldValue = VM_Address.fromInt(VM_Magic.prepare(VM_Magic.addressAsObject(addr), 0));
      newValue = oldValue.sub(decrement);
      if (newValue.LT(bound)) return VM_Address.max();
    } while (!VM_Magic.attempt(VM_Magic.addressAsObject(addr), 0, oldValue.toInt(), newValue.toInt()));
    return oldValue;
  }

  public static final VM_Address fetchAndAddAddressWithBound(Object base, int offset, 
                                                      int increment, VM_Address bound) throws VM_PragmaInline {
    VM_Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(increment > 0);
    do {
      oldValue = VM_Address.fromInt(VM_Magic.prepare(base, offset));
      newValue = oldValue.add(increment);
      if (newValue.GT(bound)) return VM_Address.max();
    } while (!VM_Magic.attempt(base, offset, oldValue.toInt(), newValue.toInt()));
    return oldValue;
  }

  public static final VM_Address fetchAndSubAddressWithBound(Object base, int offset, 
                                                      int decrement, VM_Address bound) throws VM_PragmaInline {
    VM_Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(decrement > 0);
    do {
      oldValue = VM_Address.fromInt(VM_Magic.prepare(base, offset));
      newValue = oldValue.sub(decrement);
      if (newValue.LT(bound)) return VM_Address.max();
    } while (!VM_Magic.attempt(base, offset, oldValue.toInt(), newValue.toInt()));
    return oldValue;
  }
}
