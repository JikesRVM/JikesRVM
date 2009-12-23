/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.scheduler;

import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.Barriers;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Word;
import org.vmmagic.unboxed.Offset;

/**
 * Class to provide synchronization methods where java language
 * synchronization is insufficient and Magic.prepare and Magic.attempt
 * are at too low a level
 */
@Uninterruptible
public class Synchronization {

  /**
   * Atomically swap test value to new value in the specified object and the specified field
   * @param base object containing field
   * @param offset position of field
   * @param testValue expected value of field
   * @param newValue new value of field
   * @return true => successful swap, false => field not equal to testValue
   */
  @Inline
  public static boolean tryCompareAndSwap(Object base, Offset offset, int testValue, int newValue) {
    if (Barriers.NEEDS_INT_PUTFIELD_BARRIER || Barriers.NEEDS_INT_GETFIELD_BARRIER) {
      return Barriers.intTryCompareAndSwap(base, offset, testValue, newValue);
    } else {
      int oldValue;
      do {
        oldValue = Magic.prepareInt(base, offset);
        if (oldValue != testValue) return false;
      } while (!Magic.attemptInt(base, offset, oldValue, newValue));
      return true;
    }
  }

  /**
   * Atomically swap test value to new value in the specified object and the specified field
   * @param base object containing field
   * @param offset position of field
   * @param testValue expected value of field
   * @param newValue new value of field
   * @return true => successful swap, false => field not equal to testValue
   */
  @Inline
  public static boolean tryCompareAndSwap(Object base, Offset offset, long testValue, long newValue) {
    if (Barriers.NEEDS_LONG_PUTFIELD_BARRIER || Barriers.NEEDS_LONG_GETFIELD_BARRIER) {
      return Barriers.longTryCompareAndSwap(base, offset, testValue, newValue);
    } else {
      long oldValue;
      do {
        oldValue = Magic.prepareLong(base, offset);
        if (oldValue != testValue) return false;
      } while (!Magic.attemptLong(base, offset, oldValue, newValue));
      return true;
    }
  }

  /**
   * Atomically swap test value to new value in the specified object and the specified field
   * @param base object containing field
   * @param offset position of field
   * @param testValue expected value of field
   * @param newValue new value of field
   * @return true => successful swap, false => field not equal to testValue
   */
  @Inline
  public static boolean tryCompareAndSwap(Object base, Offset offset, Word testValue, Word newValue) {
    if (Barriers.NEEDS_WORD_PUTFIELD_BARRIER || Barriers.NEEDS_WORD_GETFIELD_BARRIER) {
      return Barriers.wordTryCompareAndSwap(base, offset, testValue, newValue);
    } else {
      Word oldValue;
      do {
        oldValue = Magic.prepareWord(base, offset);
        if (oldValue != testValue) return false;
      } while (!Magic.attemptWord(base, offset, oldValue, newValue));
      return true;
    }
  }

  /**
   * Atomically swap test value to new value in the specified object and the specified field
   * @param base object containing field
   * @param offset position of field
   * @param testValue expected value of field
   * @param newValue new value of field
   * @return true => successful swap, false => field not equal to testValue
   */
  @Inline
  public static boolean tryCompareAndSwap(Object base, Offset offset, Address testValue, Address newValue) {
    if (Barriers.NEEDS_ADDRESS_PUTFIELD_BARRIER || Barriers.NEEDS_ADDRESS_GETFIELD_BARRIER) {
      return Barriers.addressTryCompareAndSwap(base, offset, testValue, newValue);
    } else {
      Address oldValue;
      do {
        oldValue = Magic.prepareAddress(base, offset);
        if (oldValue != testValue)
          return false;
      } while (!Magic.attemptAddress(base, offset, oldValue, newValue));
      return true;
    }
  }

  /**
   * Atomically swap test value to new value in the specified object and the specified field
   * @param base object containing field
   * @param offset position of field
   * @param testValue expected value of field
   * @param newValue new value of field
   * @return true => successful swap, false => field not equal to testValue
   */
  @Inline
  public static boolean tryCompareAndSwap(Object base, Offset offset, Object testValue, Object newValue) {
    if (Barriers.NEEDS_OBJECT_PUTFIELD_BARRIER || Barriers.NEEDS_OBJECT_GETFIELD_BARRIER) {
      return Barriers.objectTryCompareAndSwap(base, offset, testValue, newValue);
    } else {
      Object oldValue;
      do {
        oldValue = Magic.prepareObject(base, offset);
        if (oldValue != testValue) return false;
      } while (!Magic.attemptObject(base, offset, oldValue, newValue));
      return true;
    }
  }

  @Inline
  public static boolean testAndSet(Object base, Offset offset, int newValue) {
    return tryCompareAndSwap(base, offset, 0, newValue);
  }

  @Inline
  public static int fetchAndStore(Object base, Offset offset, int newValue) {
    int oldValue;
    do {
      if (Barriers.NEEDS_INT_GETFIELD_BARRIER) {
        oldValue = Barriers.intFieldRead(base, offset, 0);
      } else {
        oldValue = Magic.getIntAtOffset(base, offset);
      }
    } while (!tryCompareAndSwap(base, offset, oldValue, newValue));
    return oldValue;
  }

  @Inline
  public static Address fetchAndStoreAddress(Object base, Offset offset, Address newValue) {
    Address oldValue;
    do {
      if (Barriers.NEEDS_ADDRESS_GETFIELD_BARRIER) {
        oldValue = Barriers.addressFieldRead(base, offset, 0);
      } else {
        oldValue = Magic.getAddressAtOffset(base, offset);
      }
    } while (!tryCompareAndSwap(base, offset, oldValue, newValue));
    return oldValue;
  }

  @Inline
  public static int fetchAndAdd(Object base, Offset offset, int increment) {
    int oldValue;
    do {
      if (Barriers.NEEDS_INT_GETFIELD_BARRIER) {
        oldValue = Barriers.intFieldRead(base, offset, 0);
      } else {
        oldValue = Magic.getIntAtOffset(base, offset);
      }
    } while (!tryCompareAndSwap(base, offset, oldValue, oldValue + increment));
    return oldValue;
  }

  @Inline
  public static int fetchAndDecrement(Object base, Offset offset, int decrement) {
    int oldValue;
    do {
      if (Barriers.NEEDS_INT_GETFIELD_BARRIER) {
        oldValue = Barriers.intFieldRead(base, offset, 0);
      } else {
        oldValue = Magic.getIntAtOffset(base, offset);
      }
    } while (!tryCompareAndSwap(base, offset, oldValue, oldValue - decrement));
    return oldValue;
  }

  @Inline
  public static Address fetchAndAddAddressWithBound(Object base, Offset offset, int increment, Address bound) {
    Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(increment > 0);
    do {
      if (Barriers.NEEDS_ADDRESS_GETFIELD_BARRIER) {
        oldValue = Barriers.addressFieldRead(base, offset, 0);
      } else {
        oldValue = Magic.getAddressAtOffset(base, offset);
      }
      newValue = oldValue.plus(increment);
      if (newValue.GT(bound)) return Address.max();
    } while (!tryCompareAndSwap(base, offset, oldValue, newValue));
    return oldValue;
  }

  @Inline
  public static Address fetchAndSubAddressWithBound(Object base, Offset offset, int decrement, Address bound) {
    Address oldValue, newValue;
    if (VM.VerifyAssertions) VM._assert(decrement > 0);
    do {
      if (Barriers.NEEDS_ADDRESS_GETFIELD_BARRIER) {
        oldValue = Barriers.addressFieldRead(base, offset, 0);
      } else {
        oldValue = Magic.getAddressAtOffset(base, offset);
      }
      newValue = oldValue.minus(decrement);
      if (newValue.LT(bound)) return Address.max();
    } while (!tryCompareAndSwap(base, offset, oldValue, newValue));
    return oldValue;
  }
}
