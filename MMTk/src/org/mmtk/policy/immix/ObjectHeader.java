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

import org.mmtk.plan.Plan;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Word;

@Uninterruptible
public class ObjectHeader {
  /** number of header bits we may use */
  static final int MAX_BITS = 8;

  /* header requirements */
  public static final int LOCAL_GC_BITS_REQUIRED = MAX_BITS;
  public static final int GLOBAL_GC_BITS_REQUIRED = 0;
  public static final int GC_HEADER_WORDS_REQUIRED = 0;

  /* stolen bits */
  static final Word NEW_OBJECT_MARK = Word.fromIntZeroExtend(0); // using zero means no need for explicit initialization on allocation
  private static final Word BEING_FORWARDED = Word.fromIntZeroExtend(2); // second bit indicates an object is in the process of being forwarded
  private static final Word FORWARDED       = Word.fromIntZeroExtend(3); // second bit indicates an object is in the process of being forwarded
  private static final Word FORWARDING_MASK = Word.fromIntZeroExtend(3);

  private static final int UNLOGGED_BIT_NUMBER = 3;
  public static final Word UNLOGGED_BIT = Word.one().lsh(UNLOGGED_BIT_NUMBER);
  public static final Word LOG_SET_MASK = UNLOGGED_BIT;

  private static final int STOLEN_LO_BITS = UNLOGGED_BIT_NUMBER + 1; // both used for forwarding, also the all zero state is used to represent new objects
  private static final int STOLEN_HI_BITS = 2;

  static final int  MAX_MARKCOUNT_BITS = MAX_BITS-(STOLEN_LO_BITS+STOLEN_HI_BITS);
  private static final int FIRST_STOLEN_HI_BIT_NUMBER = STOLEN_LO_BITS + MAX_MARKCOUNT_BITS;

  public static final int PINNED_BIT_NUMBER = FIRST_STOLEN_HI_BIT_NUMBER;
  public static final Word PINNED_BIT = Word.one().lsh(PINNED_BIT_NUMBER);

  private static final int STRADDLE_BIT_NUMBER = PINNED_BIT_NUMBER + 1;
  public static final Word STRADDLE_BIT = Word.one().lsh(STRADDLE_BIT_NUMBER);

  /* mark bits */
  private static final int  MARK_BASE = STOLEN_LO_BITS;
  private static final Word MARK_INCREMENT = Word.one().lsh(MARK_BASE);
  public static final Word MARK_MASK = Word.one().lsh(MAX_MARKCOUNT_BITS).minus(Word.one()).lsh(MARK_BASE);
  public static final Word MARK_AND_LOG_BITS_MASK = Word.one().lsh(MAX_BITS-STOLEN_HI_BITS).minus(Word.one());
  public static final Word MARK_BITS_MASK = MARK_AND_LOG_BITS_MASK.xor(UNLOGGED_BIT);

  public static final Word MARK_BASE_VALUE = MARK_INCREMENT;



  /****************************************************************************
   *
   * Marking
   */

  /**
   * Non-atomically test and set the mark bit of an object.  Return true
   * if successful, false if the mark bit was already set.
   *
   * @param object The object whose mark bit is to be written
   * @param markState The value to which the mark bits will be set
   */
  static Word testAndMark(ObjectReference object, Word markState) {
    int oldValue, newValue, oldMarkState;

    oldValue = VM.objectModel.readAvailableByte(object);
    oldMarkState = oldValue & MARK_AND_LOG_BITS_MASK.toInt();
    if (oldMarkState != markState.toInt()) {
      newValue = (oldValue & ~MARK_AND_LOG_BITS_MASK.toInt()) | markState.toInt();
      VM.objectModel.writeAvailableByte(object, (byte) newValue);
    }
    return Word.fromIntZeroExtend(oldMarkState);
  }

  static void setMarkStateUnlogAndUnlock(ObjectReference object, Word originalForwardingWord, Word markState) {
    Word markValue = Plan.NEEDS_LOG_BIT_IN_HEADER ? markState.or(ObjectHeader.UNLOGGED_BIT) : markState;
    int oldValue = originalForwardingWord.toInt();
    int newValue = (oldValue & (MARK_AND_LOG_BITS_MASK.or(FORWARDING_MASK)).not().toInt()) | markValue.toInt();
    VM.objectModel.writeAvailableByte(object, (byte) newValue);
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert((oldValue & MARK_AND_LOG_BITS_MASK.toInt()) != markValue.toInt());
  }

  /**
   * Return true if the mark count for an object has the given value.
   *
   * @param object The object whose mark bit is to be tested
   * @param value The value against which the mark bit will be tested
   * @return True if the mark bit for the object has the given value.
   */
  static boolean testMarkState(ObjectReference object, Word value) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(value.and(MARK_MASK).EQ(value));
    return testMarkState(VM.objectModel.readAvailableBitsWord(object), value);
  }

  static boolean testMarkState(Word forwardingWord, Word value) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(value.and(MARK_MASK).EQ(value));
    return forwardingWord.and(MARK_MASK).EQ(value);
  }

  static boolean isNewObject(ObjectReference object) {
    Word markBits = VM.objectModel.readAvailableBitsWord(object).and(MARK_AND_LOG_BITS_MASK);
    return markBits.EQ(NEW_OBJECT_MARK);
  }

  static boolean isMatureObject(ObjectReference object) {
    Word markBits = VM.objectModel.readAvailableBitsWord(object).and(MARK_AND_LOG_BITS_MASK);
    boolean unforwarded = markBits.and(FORWARDING_MASK).isZero();
    boolean newObj = markBits.EQ(NEW_OBJECT_MARK);
    return unforwarded && !newObj;
  }

  @Inline
  static void markAsStraddling(ObjectReference object) {
    Word old = VM.objectModel.readAvailableBitsWord(object);
    VM.objectModel.writeAvailableBitsWord(object, old.or(STRADDLE_BIT));
  }

  @Inline
  static boolean isStraddlingObject(ObjectReference object) {
    return isStraddleBitSet(VM.objectModel.readAvailableBitsWord(object));
  }

  @Inline
  private static boolean isStraddleBitSet(Word header) {
    return header.and(STRADDLE_BIT).EQ(STRADDLE_BIT);
  }

  @Inline
  static boolean isUnloggedObject(ObjectReference object) {
    return isUnloggedBitSet(VM.objectModel.readAvailableBitsWord(object));
  }

  @Inline
  static boolean isUnloggedBitSet(Word header) {
    return header.and(UNLOGGED_BIT).EQ(UNLOGGED_BIT);
  }

  @Inline
  public static void pinObject(ObjectReference object) {
    byte header = VM.objectModel.readAvailableByte(object);
    VM.objectModel.writeAvailableByte(object, (byte)(header | PINNED_BIT.toInt()));
  }

  @Inline
  static boolean isPinnedObject(ObjectReference object) {
    return isPinnedBitSet(VM.objectModel.readAvailableBitsWord(object));
  }

  @Inline
  private static boolean isPinnedBitSet(Word header) {
    return header.and(PINNED_BIT).EQ(PINNED_BIT);
  }

  /**
   * Write the allocState into the mark state fields of an object non-atomically.
   * This is appropriate for collection time initialization.
   *
   * @param object The object whose mark state is to be written
   * @param markState TODO: what am I?
   * @param straddle TODO: what am I?
   */
  static void writeMarkState(ObjectReference object, Word markState, boolean straddle) {
    Word oldValue = VM.objectModel.readAvailableBitsWord(object);
    Word markValue = Plan.NEEDS_LOG_BIT_IN_HEADER ? markState.or(ObjectHeader.UNLOGGED_BIT) : markState;
    Word newValue = oldValue.and(MARK_AND_LOG_BITS_MASK.not()).or(markValue);
    if (straddle)
      newValue = newValue.or(STRADDLE_BIT);
    VM.objectModel.writeAvailableBitsWord(object, newValue);
  }

  /****************************************************************************
   *
   * Forwarding
   */

  /**
   * Either return the forwarding pointer if the object is already
   * forwarded (or being forwarded) or write the bit pattern that
   * indicates that the object is being forwarded
   *
   * @param object The object to be forwarded
   * @return The forwarding pointer for the object if it has already
   * been forwarded.
   */
  static Word attemptToBeForwarder(ObjectReference object) {
    Word oldValue;
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      if (oldValue.and(FORWARDING_MASK).EQ(FORWARDED))
        return oldValue;
    } while (!VM.objectModel.attemptAvailableBits(object, oldValue, oldValue.or(BEING_FORWARDED)));
    return oldValue;
  }

  static ObjectReference forwardObject(ObjectReference object, int allocator) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!isPinnedObject(object));
    ObjectReference newObject = VM.objectModel.copy(object, allocator);
    VM.objectModel.writeAvailableBitsWord(object, newObject.toAddress().toWord().or(FORWARDED));
    return newObject;
  }

  static void setForwardingWordAndEnsureUnlogged(ObjectReference object, Word forwardingWord) {
    if (Plan.NEEDS_LOG_BIT_IN_HEADER) forwardingWord = forwardingWord.or(UNLOGGED_BIT);
    VM.objectModel.writeAvailableBitsWord(object, forwardingWord);
  }

  static boolean isForwardedOrBeingForwarded(ObjectReference object) {
    return isForwardedOrBeingForwarded(VM.objectModel.readAvailableBitsWord(object));
  }

  static boolean isForwardedOrBeingForwarded(Word forwardingWord) {
    return !forwardingWord.and(FORWARDING_MASK).isZero();
  }

  static ObjectReference spinAndGetForwardedObject(ObjectReference object, Word forwardingWord) {
    /* We must wait (spin) if the object is not yet fully forwarded */
    while (forwardingWord.and(FORWARDING_MASK).EQ(BEING_FORWARDED))
      forwardingWord = VM.objectModel.readAvailableBitsWord(object);

    /* Now extract the object reference from the forwarding word and return it */
    if (forwardingWord.and(FORWARDING_MASK).EQ(FORWARDED))
      return forwardingWord.and(FORWARDING_MASK.not()).toAddress().toObjectReference();
    else
      return object;
  }

  /**
   * Return the mark state incremented or decremented by one.
   *
   * @param increment If true, then return the incremented value else return the decremented value
   * @return the mark state incremented or decremented by one.
   */
  static Word deltaMarkState(Word state, boolean increment) {
    Word rtn = state;
    do {
      rtn = increment ? rtn.plus(MARK_INCREMENT) : rtn.minus(MARK_INCREMENT);
      rtn = rtn.and(MARK_MASK);
      } while (rtn.LT(MARK_BASE_VALUE));
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(rtn.NE(state));
    return rtn;
  }
}
