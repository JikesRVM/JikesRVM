/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Class to provide synchronization methods where java language 
 * synchronization is insufficient and VM_Magic.prepare and VM_Magic.attempt
 * are at too low a level
 *
 * @author Bowen Alpern
 * @author Anthony Cocchi
 */
public class VM_Synchronization implements Uninterruptible {

  public static final boolean tryCompareAndSwap(Object base, int offset, int testValue, int newValue) throws InlinePragma {
    int oldValue;
    do {
      oldValue = VM_Magic.prepareInt(base, offset);
      if (oldValue != testValue) return false;
    } while (!VM_Magic.attemptInt(base, offset, oldValue, newValue));
    return true;
  }

  public static final boolean testAndSet(Object base, int offset, int newValue) throws InlinePragma {
    int oldValue;
    do {
      oldValue = VM_Magic.prepareInt(base, offset);
      if (oldValue != 0) return false;
    } while (!VM_Magic.attemptInt(base, offset, oldValue, newValue));
    return true;
  }

  public static final int fetchAndStore(Object base, int offset, int newValue) throws InlinePragma {
    int oldValue;
    do {
      oldValue = VM_Magic.prepareInt(base, offset);
    } while (!VM_Magic.attemptInt(base, offset, oldValue, newValue));
    return oldValue;
  }

  public static final Address fetchAndStoreAddress(Object base, int offset, Address newValue) throws InlinePragma {
    Address oldValue;
    do {
      oldValue = VM_Magic.prepareAddress(base, offset);
    } while (!VM_Magic.attemptAddress(base, offset, oldValue, newValue));
    return oldValue;
  }

  public static final int fetchAndAdd(Object base, int offset, int increment) throws InlinePragma {
    int oldValue;
    do {
      oldValue = VM_Magic.prepareInt(base, offset);
    } while (!VM_Magic.attemptInt(base, offset, oldValue, oldValue+increment));
    return oldValue;
  }

  public static final int fetchAndDecrement(Object base, int offset, int decrement) throws InlinePragma {
    int oldValue;
    do {
      oldValue = VM_Magic.prepareInt(base, offset);
    } while (!VM_Magic.attemptInt(base, offset, oldValue, oldValue-decrement));
    return oldValue;
  }

  public static final Address fetchAndAddAddress(Address addr, int increment) throws InlinePragma {
    Address oldValue;
    do {
      oldValue = VM_Magic.prepareAddress(VM_Magic.addressAsObject(addr), 0);
    } while (!VM_Magic.attemptAddress(VM_Magic.addressAsObject(addr), 0, oldValue, oldValue.add(increment)));
    return oldValue;
  }

  public static final Address fetchAndAddAddressWithBound(Address addr, int increment, Address bound) throws InlinePragma {
    Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(increment > 0);
    do {
      oldValue = VM_Magic.prepareAddress(VM_Magic.addressAsObject(addr), 0);
      newValue = oldValue.add(increment);
      if (newValue.GT(bound)) return Address.max();
    } while (!VM_Magic.attemptAddress(VM_Magic.addressAsObject(addr), 0, oldValue, newValue));
    return oldValue;
  }

  public static final Address fetchAndSubAddressWithBound(Address addr, int decrement, Address bound) throws InlinePragma {
    Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(decrement > 0);
    do {
      oldValue = VM_Magic.prepareAddress(VM_Magic.addressAsObject(addr), 0);
      newValue = oldValue.sub(decrement);
      if (newValue.LT(bound)) return Address.max();
    } while (!VM_Magic.attemptAddress(VM_Magic.addressAsObject(addr), 0, oldValue, newValue));
    return oldValue;
  }

  public static final Address fetchAndAddAddressWithBound(Object base, int offset, 
                                                             int increment, Address bound) throws InlinePragma {
    Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(increment > 0);
    do {
      oldValue = VM_Magic.prepareAddress(base, offset);
      newValue = oldValue.add(increment);
      if (newValue.GT(bound)) return Address.max();
    } while (!VM_Magic.attemptAddress(base, offset, oldValue, newValue));
    return oldValue;
  }

  public static final Address fetchAndSubAddressWithBound(Object base, int offset, 
                                                             int decrement, Address bound) throws InlinePragma {
    Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(decrement > 0);
    do {
      oldValue = VM_Magic.prepareAddress(base, offset);
      newValue = oldValue.sub(decrement);
      if (newValue.LT(bound)) return Address.max();
    } while (!VM_Magic.attemptAddress(base, offset, oldValue, newValue));
    return oldValue;
  }
}
