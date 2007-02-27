/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm;

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
@Uninterruptible public class VM_Synchronization {

  @Inline
  public static boolean tryCompareAndSwap(Object base, Offset offset, int testValue, int newValue) {
    int oldValue;
    do {
      oldValue = VM_Magic.prepareInt(base, offset);
      if (oldValue != testValue) return false;
    } while (!VM_Magic.attemptInt(base, offset, oldValue, newValue));
    return true;
  }

  @Inline
  public static boolean testAndSet(Object base, Offset offset, int newValue) {
    int oldValue;
    do {
      oldValue = VM_Magic.prepareInt(base, offset);
      if (oldValue != 0) return false;
    } while (!VM_Magic.attemptInt(base, offset, oldValue, newValue));
    return true;
  }

  @Inline
  public static int fetchAndStore(Object base, Offset offset, int newValue) {
    int oldValue;
    do {
      oldValue = VM_Magic.prepareInt(base, offset);
    } while (!VM_Magic.attemptInt(base, offset, oldValue, newValue));
    return oldValue;
  }

  @Inline
  public static Address fetchAndStoreAddress(Object base, Offset offset, Address newValue) {
    Address oldValue;
    do {
      oldValue = VM_Magic.prepareAddress(base, offset);
    } while (!VM_Magic.attemptAddress(base, offset, oldValue, newValue));
    return oldValue;
  }

  @Inline
  public static int fetchAndAdd(Object base, Offset offset, int increment) {
    int oldValue;
    do {
      oldValue = VM_Magic.prepareInt(base, offset);
    } while (!VM_Magic.attemptInt(base, offset, oldValue, oldValue+increment));
    return oldValue;
  }

  @Inline
  public static int fetchAndDecrement(Object base, Offset offset, int decrement) {
    int oldValue;
    do {
      oldValue = VM_Magic.prepareInt(base, offset);
    } while (!VM_Magic.attemptInt(base, offset, oldValue, oldValue-decrement));
    return oldValue;
  }

  @Inline
  public static Address fetchAndAddAddress(Address addr, int increment) {
    Address oldValue;
    do {
      oldValue = VM_Magic.prepareAddress(VM_Magic.addressAsObject(addr), Offset.zero());
    } while (!VM_Magic.attemptAddress(VM_Magic.addressAsObject(addr), Offset.zero(), oldValue, oldValue.plus(increment)));
    return oldValue;
  }

  @Inline
  public static Address fetchAndAddAddressWithBound(Address addr, int increment, Address bound) {
    Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(increment > 0);
    do {
      oldValue = VM_Magic.prepareAddress(VM_Magic.addressAsObject(addr), Offset.zero());
      newValue = oldValue.plus(increment);
      if (newValue.GT(bound)) return Address.max();
    } while (!VM_Magic.attemptAddress(VM_Magic.addressAsObject(addr), Offset.zero(), oldValue, newValue));
    return oldValue;
  }

  @Inline
  public static Address fetchAndSubAddressWithBound(Address addr, int decrement, Address bound) {
    Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(decrement > 0);
    do {
      oldValue = VM_Magic.prepareAddress(VM_Magic.addressAsObject(addr), Offset.zero());
      newValue = oldValue.minus(decrement);
      if (newValue.LT(bound)) return Address.max();
    } while (!VM_Magic.attemptAddress(VM_Magic.addressAsObject(addr), Offset.zero(), oldValue, newValue));
    return oldValue;
  }

  @Inline
  public static Address fetchAndAddAddressWithBound(Object base, Offset offset,
                                                             int increment, Address bound) { 
    Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(increment > 0);
    do {
      oldValue = VM_Magic.prepareAddress(base, offset);
      newValue = oldValue.plus(increment);
      if (newValue.GT(bound)) return Address.max();
    } while (!VM_Magic.attemptAddress(base, offset, oldValue, newValue));
    return oldValue;
  }

  @Inline
  public static Address fetchAndSubAddressWithBound(Object base, Offset offset,
                                                             int decrement, Address bound) { 
    Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(decrement > 0);
    do {
      oldValue = VM_Magic.prepareAddress(base, offset);
      newValue = oldValue.minus(decrement);
      if (newValue.LT(bound)) return Address.max();
    } while (!VM_Magic.attemptAddress(base, offset, oldValue, newValue));
    return oldValue;
  }
}
