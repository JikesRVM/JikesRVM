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
package org.mmtk.policy.immix;

import org.mmtk.utility.ForwardingWord;
import org.mmtk.utility.HeaderByte;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;

@Uninterruptible
public class ObjectHeader {
  /** number of header bits we may use */
  static final int AVAILABLE_LOCAL_BITS = 8 - HeaderByte.USED_GLOBAL_BITS;

  /* header requirements */

  /**
   *
   */
  public static final int LOCAL_GC_BITS_REQUIRED = AVAILABLE_LOCAL_BITS;
  public static final int GLOBAL_GC_BITS_REQUIRED = 0;
  public static final int GC_HEADER_WORDS_REQUIRED = 0;

  /* local status bits */
  static final byte NEW_OBJECT_MARK = 0; // using zero means no need for explicit initialization on allocation

  public static final int PINNED_BIT_NUMBER = ForwardingWord.FORWARDING_BITS;
  public static final byte PINNED_BIT = 1<<PINNED_BIT_NUMBER;

  private static final int STRADDLE_BIT_NUMBER = PINNED_BIT_NUMBER + 1;
  public static final byte STRADDLE_BIT = 1<<STRADDLE_BIT_NUMBER;

  /* mark bits */

  /**
   *
   */
  private static final int  MARK_BASE = STRADDLE_BIT_NUMBER+1;
  static final int  MAX_MARKCOUNT_BITS = AVAILABLE_LOCAL_BITS-MARK_BASE;
  private static final byte MARK_INCREMENT = 1<<MARK_BASE;
  public static final byte MARK_MASK = (byte) (((1<<MAX_MARKCOUNT_BITS)-1)<<MARK_BASE);
  private static final byte MARK_AND_FORWARDING_MASK = (byte) (MARK_MASK | ForwardingWord.FORWARDING_MASK);
  public static final byte MARK_BASE_VALUE = MARK_INCREMENT;


  /****************************************************************************
   *
   * Marking
   */

  /**
   * Non-atomically test and set the mark bit of an object.
   *
   * @param object The object whose mark bit is to be written
   * @param markState The value to which the mark bits will be set
   * @return the old mark state
   */
  static byte testAndMark(ObjectReference object, byte markState) {
    byte oldValue, newValue, oldMarkState;

    oldValue = VM.objectModel.readAvailableByte(object);
    oldMarkState = (byte) (oldValue & MARK_MASK);
    if (oldMarkState != markState) {
      newValue = (byte) ((oldValue & ~MARK_MASK) | markState);
      if (HeaderByte.NEEDS_UNLOGGED_BIT)
        newValue |= HeaderByte.UNLOGGED_BIT;
      VM.objectModel.writeAvailableByte(object, newValue);
    }
    return oldMarkState;
  }

  static void setMarkStateUnlogAndUnlock(ObjectReference object, byte gcByte, byte markState) {
    byte oldGCByte = gcByte;
    byte newGCByte = (byte) ((oldGCByte & ~MARK_AND_FORWARDING_MASK) | markState);
    if (HeaderByte.NEEDS_UNLOGGED_BIT)
      newGCByte |= HeaderByte.UNLOGGED_BIT;
    VM.objectModel.writeAvailableByte(object, newGCByte);
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert((oldGCByte & MARK_MASK) != markState);
  }

  /**
   * Return {@code true} if the mark count for an object has the given value.
   *
   * @param object The object whose mark bit is to be tested
   * @param value The value against which the mark bit will be tested
   * @return {@code true} if the mark bit for the object has the given value.
   */
  static boolean testMarkState(ObjectReference object, byte value) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert((value & MARK_MASK) == value);
    return (VM.objectModel.readAvailableByte(object) & MARK_MASK) == value;
   }

  static boolean testMarkState(byte gcByte, byte value) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert((value & MARK_MASK) == value);
    return (gcByte & MARK_MASK) == value;
  }

  static boolean isNewObject(ObjectReference object) {
    return (VM.objectModel.readAvailableByte(object) & MARK_AND_FORWARDING_MASK) == NEW_OBJECT_MARK;
  }

  static boolean isMatureObject(ObjectReference object) {
    byte status = (byte) (VM.objectModel.readAvailableByte(object) & MARK_AND_FORWARDING_MASK);
    boolean unforwarded = (status & ForwardingWord.FORWARDING_MASK) == 0;
    boolean newObj = (status == NEW_OBJECT_MARK);
    return unforwarded && !newObj;
  }

  @Inline
  static void markAsStraddling(ObjectReference object) {
    byte old = VM.objectModel.readAvailableByte(object);
    VM.objectModel.writeAvailableByte(object, (byte) (old | STRADDLE_BIT));
  }

  @Inline
  static boolean isStraddlingObject(ObjectReference object) {
    return (VM.objectModel.readAvailableByte(object) & STRADDLE_BIT) == STRADDLE_BIT;
  }

  @Inline
  public static void pinObject(ObjectReference object) {
    byte old = VM.objectModel.readAvailableByte(object);
    VM.objectModel.writeAvailableByte(object, (byte) (old | PINNED_BIT));
  }

  @Inline
  static boolean isPinnedObject(ObjectReference object) {
    return (VM.objectModel.readAvailableByte(object) & PINNED_BIT) == PINNED_BIT;
  }

  /**
   * Write the allocState into the mark state fields of an object non-atomically.
   * This is appropriate for collection time initialization.
   *
   * @param object The object whose mark state is to be written
   * @param markState TODO: what am I?
   * @param straddle TODO: what am I?
   */
  static void writeMarkState(ObjectReference object, byte markState, boolean straddle) {
    byte oldValue = VM.objectModel.readAvailableByte(object);
    byte markValue = markState;
    byte newValue = (byte) (oldValue & ~MARK_AND_FORWARDING_MASK);
    if (HeaderByte.NEEDS_UNLOGGED_BIT)
      newValue |= HeaderByte.UNLOGGED_BIT;
    newValue |= markValue;
    if (straddle)
      newValue |= STRADDLE_BIT;
    VM.objectModel.writeAvailableByte(object, newValue);
  }

  static void returnToPriorStateAndEnsureUnlogged(ObjectReference object, byte status) {
    if (HeaderByte.NEEDS_UNLOGGED_BIT) status |= HeaderByte.UNLOGGED_BIT;
    VM.objectModel.writeAvailableByte(object, status);
  }

  /**
   * Return the mark state incremented or decremented by one.
   *
   * @param increment If {@code true}, then return the incremented value else return the decremented value
   * @return the mark state incremented or decremented by one.
   */
  static byte deltaMarkState(byte state, boolean increment) {
    byte rtn = state;
    do {
      rtn = (byte) (increment ? rtn + MARK_INCREMENT : rtn - MARK_INCREMENT);
      rtn &= MARK_MASK;
      } while (rtn < MARK_BASE_VALUE);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(rtn != state);
    return rtn;
  }
}
