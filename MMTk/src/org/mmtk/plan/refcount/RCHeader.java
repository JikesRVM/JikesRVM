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
package org.mmtk.plan.refcount;

import org.mmtk.utility.Constants;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Word;

@Uninterruptible
public class RCHeader implements Constants {

  /* Requirements */
  public static final int LOCAL_GC_BITS_REQUIRED = 0;
  public static final int GLOBAL_GC_BITS_REQUIRED = 8;
  public static final int GC_HEADER_WORDS_REQUIRED = 0;

  /****************************************************************************
   * Object Logging (applies to *all* objects)
   */

  /* Mask bits to signify the start/finish of logging an object */

  /**
   *
   */
  public static final int      LOG_BIT  = 0;
  public static final Word       LOGGED = Word.zero();                          //...00000
  public static final Word    UNLOGGED  = Word.one();                           //...00001
  public static final Word BEING_LOGGED = Word.one().lsh(2).minus(Word.one());  //...00011
  public static final Word LOGGING_MASK = LOGGED.or(UNLOGGED).or(BEING_LOGGED); //...00011

  /**
   * Return <code>true</code> if <code>object</code> is yet to be logged (for
   * coalescing RC).
   *
   * @param object The object in question
   * @return <code>true</code> if <code>object</code> needs to be logged.
   */
  @Inline
  @Uninterruptible
  public static boolean logRequired(ObjectReference object) {
    Word value = VM.objectModel.readAvailableBitsWord(object);
    return value.and(LOGGING_MASK).EQ(UNLOGGED);
  }

  /**
   * Attempt to log <code>object</code> for coalescing RC. This is
   * used to handle a race to log the object, and returns
   * <code>true</code> if we are to log the object and
   * <code>false</code> if we lost the race to log the object.
   *
   * <p>If this method returns <code>true</code>, it leaves the object
   * in the <code>BEING_LOGGED</code> state.  It is the responsibility
   * of the caller to change the object to <code>LOGGED</code> once
   * the logging is complete.
   *
   * @see #makeLogged(ObjectReference)
   * @param object The object in question
   * @return <code>true</code> if the race to log
   * <code>object</code>was won.
   */
  @Inline
  @Uninterruptible
  public static boolean attemptToLog(ObjectReference object) {
    Word oldValue;
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      if (oldValue.and(LOGGING_MASK).EQ(LOGGED)) {
        return false;
      }
    } while ((oldValue.and(LOGGING_MASK).EQ(BEING_LOGGED)) ||
             !VM.objectModel.attemptAvailableBits(object, oldValue, oldValue.or(BEING_LOGGED)));
    if (VM.VERIFY_ASSERTIONS) {
      Word value = VM.objectModel.readAvailableBitsWord(object);
      VM.assertions._assert(value.and(LOGGING_MASK).EQ(BEING_LOGGED));
    }
    return true;
  }


  /**
   * Signify completion of logging <code>object</code>.
   *
   * <code>object</code> is left in the <code>LOGGED</code> state.
   *
   * @see #attemptToLog(ObjectReference)
   * @param object The object whose state is to be changed.
   */
  @Inline
  @Uninterruptible
  public static void makeLogged(ObjectReference object) {
    Word value = VM.objectModel.readAvailableBitsWord(object);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(value.and(LOGGING_MASK).NE(LOGGED));
    VM.objectModel.writeAvailableBitsWord(object, value.and(LOGGING_MASK.not()));
  }

  /**
   * Change <code>object</code>'s state to <code>UNLOGGED</code>.
   *
   * @param object The object whose state is to be changed.
   */
  @Inline
  @Uninterruptible
  public static void makeUnlogged(ObjectReference object) {
    Word oldValue, newValue;
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(oldValue.and(LOGGING_MASK).EQ(LOGGED));
      newValue = oldValue.or(UNLOGGED);
    } while(!VM.objectModel.attemptAvailableBits(object, oldValue, newValue));
  }

  /************************************************************************
   * RC header word
   */

  /** The mark bit used for backup tracing. */
  public static final int MARK_BIT = LOG_BIT + 2;
  public static final Word MARK_BIT_MASK = Word.one().lsh(MARK_BIT);

  /** Current not using any bits for cycle detection, etc */
  public static final int BITS_USED = MARK_BIT + 1;

  /* Reference counting increments */

  public static final int INCREMENT_SHIFT = BITS_USED;
  public static final Word INCREMENT = Word.one().lsh(INCREMENT_SHIFT);
  public static final Word LIVE_THRESHOLD = INCREMENT;

  /* Return values from decRC */

  public static final int DEC_KILL = 0;
  public static final int DEC_ALIVE = 1;

  /* Limited bit thresholds and masks */

  public static final Word refSticky = Word.one().lsh(BITS_IN_BYTE - BITS_USED).minus(Word.one()).lsh(INCREMENT_SHIFT);
  public static final int refStickyValue = refSticky.rshl(INCREMENT_SHIFT).toInt();
  public static final Word WRITE_MASK = refSticky.not();
  public static final Word READ_MASK = refSticky;

  /**
   * Has this object been marked by the most recent backup trace.
   */
  @Inline
  public static boolean isMarked(ObjectReference object) {
    return isHeaderMarked(VM.objectModel.readAvailableBitsWord(object));
  }

  /**
   * Has this object been marked by the most recent backup trace.
   */
  @Inline
  public static void clearMarked(ObjectReference object) {
    Word oldValue, newValue;
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isHeaderMarked(oldValue));
      newValue = oldValue.and(MARK_BIT_MASK.not());
    } while (!VM.objectModel.attemptAvailableBits(object, oldValue, newValue));
  }

  /**
   * Has this object been marked by the most recent backup trace.
   */
  @Inline
  private static boolean isHeaderMarked(Word header) {
    return header.and(MARK_BIT_MASK).EQ(MARK_BIT_MASK);
  }

  /**
   * Attempt to atomically mark this object. Return <code>true</code> if the mark was performed.
   */
  @Inline
  public static boolean testAndMark(ObjectReference object) {
    Word oldValue, newValue;
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      if (isHeaderMarked(oldValue)) {
        return false;
      }
      newValue = oldValue.or(MARK_BIT_MASK);
    } while (!VM.objectModel.attemptAvailableBits(object, oldValue, newValue));
    return true;
  }

  /**
   * Perform any required initialization of the GC portion of the header.
   *
   * @param object the object
   * @param initialInc start with a reference count of 1 (0 if <code>false</code>)
   */
  @Inline
  public static void initializeHeader(ObjectReference object, boolean initialInc) {
    Word existingValue = VM.objectModel.readAvailableBitsWord(object);
    Word initialValue = existingValue.and(WRITE_MASK).or((initialInc)? INCREMENT : Word.zero());
    VM.objectModel.writeAvailableBitsWord(object, initialValue);
  }

  /**
   * Return <code>true</code> if given object is live
   *
   * @param object The object whose liveness is to be tested
   * @return <code>true</code> if the object is alive
   */
  @Inline
  @Uninterruptible
  public static boolean isLiveRC(ObjectReference object) {
    Word value = VM.objectModel.readAvailableBitsWord(object);
    if (isStuck(value)) return true;
    return value.and(READ_MASK).GE(LIVE_THRESHOLD);
  }

  /**
   * Return the reference count for the object.
   *
   * @param object The object whose liveness is to be tested
   * @return <code>true</code> if the object is alive
   */
  @Inline
  @Uninterruptible
  public static int getRC(ObjectReference object) {
    Word value = VM.objectModel.readAvailableBitsWord(object);
    if (isStuck(value)) return refStickyValue;
    return value.and(READ_MASK).rshl(INCREMENT_SHIFT).toInt();
  }

  /**
   * Increment the reference count of an object.
   *
   * @param object The object whose reference count is to be incremented.
   */
  @Inline
  public static void incRC(ObjectReference object) {
    Word oldValue, newValue;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(RCBase.isRCObject(object));
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      if (isStuck(oldValue)) return;
      newValue = oldValue.plus(INCREMENT);
    } while (!VM.objectModel.attemptAvailableBits(object, oldValue, newValue));
  }

  /**
   * Decrement the reference count of an object.  Return either
   * <code>DEC_KILL</code> if the count went to zero,
   * <code>DEC_ALIVE</code> if the count did not go to zero.
   *
   * @param object The object whose RC is to be decremented.
   * @return <code>DEC_KILL</code> if the count went to zero,
   * <code>DEC_ALIVE</code> if the count did not go to zero.
   */
  @Inline
  @Uninterruptible
  public static int decRC(ObjectReference object) {
    Word oldValue, newValue;
    int rtn;
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(RCBase.isRCObject(object));
      VM.assertions._assert(isLiveRC(object));
    }
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      if (isStuck(oldValue)) return DEC_ALIVE;
      newValue = oldValue.minus(INCREMENT);
      if (newValue.and(READ_MASK).LT(LIVE_THRESHOLD)) {
        rtn = DEC_KILL;
      } else {
        rtn = DEC_ALIVE;
      }
    } while (!VM.objectModel.attemptAvailableBits(object, oldValue, newValue));
    return rtn;
  }

  /**
   * Initialize the reference count of an object.
   *
   * @param object The object whose reference count is to be incremented.
   */
  @Inline
  public static void initRC(ObjectReference object) {
    Word oldValue, newValue;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(RCBase.isRCObject(object));
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      newValue = oldValue.and(WRITE_MASK).or(INCREMENT);
    } while (!VM.objectModel.attemptAvailableBits(object, oldValue, newValue));
  }

  /**
   * Has this object been stuck
   */
  @Inline
  private static boolean isStuck(Word value) {
    return value.and(refSticky).EQ(refSticky);
  }
}
