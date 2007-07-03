/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan.refcount;

import org.mmtk.plan.refcount.cd.CD;
import org.mmtk.utility.Log;
import org.mmtk.utility.Constants;

//import org.mmtk.vm.Assert;
//import org.mmtk.vm.ObjectModel;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Each instance of this class corresponds to one reference counted
 * *space*.  In other words, it maintains and performs actions with
 * respect to state that is global to a given reference counted space.
 * Each of the instance methods of this class may be called by any
 * thread (i.e. synchronization must be explicit in any instance or
 * class method).  This contrasts with the RefCountLocal, where
 * instances correspond to *plan* instances and therefore to kernel
 * threads.  Thus unlike this class, synchronization is not necessary
 * in the instance methods of RefCountLocal.
 */
@Uninterruptible public final class RCHeader implements Constants {

  /****************************************************************************
   *
   * Class variables
   */

  public static final int LOCAL_GC_BITS_REQUIRED = 2;
  public static final int GLOBAL_GC_BITS_REQUIRED = 0;
  /** How many bytes are used by all GC header fields? */
  public static final int GC_HEADER_WORDS_REQUIRED = 1;
  static final Offset RC_HEADER_OFFSET = VM.objectModel.GC_HEADER_OFFSET();


  /* Mask bits to signify the start/finish of logging an object */
  public static final Word LOGGING_MASK = Word.one().lsh(2).minus(Word.one()); //...00011
  public static final int      LOG_BIT  = 0;
  public static final Word       LOGGED = Word.zero();
  public static final Word    UNLOGGED  = Word.one();
  public static final Word BEING_LOGGED = Word.one().lsh(2).minus(Word.one()); //...00011

  public static final int DEC_KILL = 0;    // dec to zero RC --> reclaim obj
  public static final int DEC_PURPLE = 1;  // dec to non-zero RC, already buf'd
  public static final int DEC_BUFFER = -1; // dec to non-zero RC, need to bufr

  // See Bacon & Rajan ECOOP 2001 for notion of colors (purple, grey,
  // black, green).  See also Jones & Lins for a description of "Lins'
  // algorithm", on which Bacon & Rajan's is based.

  public static final int      FREED_OBJECT = 1 << 31;


  // The following are arranged to try to make the most common tests
  // fastest ("bufferd?", "green?" and "(green | purple)?")
  public static final int     BUFFERED_MASK = 0x1;  //  .. xx0001
  public static final int        COLOR_MASK = 0x1e;  //  .. x11110
  public static final int     LO_COLOR_MASK = 0x6;  //  .. x00110
  public static final int     HI_COLOR_MASK = 0x18; //  .. x11000
  public static final int             BLACK = 0x0;  //  .. xxxx0x
  public static final int              GREY = 0x2;  //  .. xxxx1x
  public static final int             WHITE = 0x4;  //  .. xx010x
  // green & purple *MUST* remain the highest colors in order to
  // preserve the (green | purple) test's precondition.
  public static final int            PURPLE = 0x8;  //  .. x01xxx
  public static final int             GREEN = 0x10;  // .. x10xxx

  public static final int            MARKED = GREY;

  // bits used to ensure retention of objects with zero RC
  public static final int       FINALIZABLE = 0x20; //  .. 100000
  public static final int    ROOT_REACHABLE = 0x40; //  .. x10000
  public static final int    HARD_THRESHOLD = ROOT_REACHABLE;
  public static final int    LIVE_THRESHOLD = FINALIZABLE;
  public static final int         BITS_USED = 7;

  static final int INCREMENT_SHIFT = BITS_USED;
  static final int INCREMENT = 1<<INCREMENT_SHIFT;
  static final int AVAILABLE_BITS = BITS_IN_ADDRESS - BITS_USED;
  static final int INCREMENT_LIMIT = ~(1<<(BITS_IN_ADDRESS-1));

  /****************************************************************************
   *
   * Object Logging Methods
   */

  /**
   * Return true if <code>object</code> is yet to be logged (for
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
      if (oldValue.and(LOGGING_MASK).EQ(LOGGED)) return false;
    } while ((oldValue.and(LOGGING_MASK).EQ(BEING_LOGGED)) ||
             !VM.objectModel.attemptAvailableBits(object, oldValue,
                                                oldValue.or(BEING_LOGGED)));
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
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(value.and(LOGGING_MASK).NE(LOGGED));
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
    Word value = VM.objectModel.readAvailableBitsWord(object);
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(value.and(LOGGING_MASK).EQ(LOGGED));
    VM.objectModel.writeAvailableBitsWord(object, value.or(UNLOGGED));
  }

  /****************************************************************************
   *
   * Header manipulation
   */

  /**
   * Perform any required initialization of the GC portion of the header.
   *
   * @param object the object ref to the storage to be initialized
   * @param typeRef the type reference for the instance being created
   * @param initialInc  do we want to initialize this header with an
   * initial increment?
   */
  @Inline
  public static void initializeHeader(ObjectReference object,
                                      ObjectReference typeRef,
                                      boolean initialInc) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(RCBase.isRCObject(object));
    }
    // all objects are birthed with an RC of INCREMENT
    int initialValue =  (initialInc) ? INCREMENT : 0;
    initialValue = CD.current().initializeHeader(typeRef, initialValue);
    object.toAddress().store(initialValue, RC_HEADER_OFFSET);
  }

  /**
   * Return true if given object is live
   *
   * @param object The object whose liveness is to be tested
   * @return True if the object is alive
   */
  @Inline
  @Uninterruptible
  public static boolean isLiveRC(ObjectReference object) {
    return object.toAddress().loadInt(RC_HEADER_OFFSET) >= LIVE_THRESHOLD;
  }

  /**
   * Return the reference count for an object
   *
   * @param object The object whose refcount is to be returned
   * @return The reference ocunt
   */
  @Inline
  @Uninterruptible
  public static int getRC(ObjectReference object) {
    return object.toAddress().loadInt(RC_HEADER_OFFSET) >> INCREMENT_SHIFT;
  }

  /**
   * Is the object live?
   *
   * @param object The object reference.
   * @return True if the object is live.
   */
  public boolean isLive(ObjectReference object) {
    return isLiveRC(object);
  }

  /**
   * Return true if given object is unreachable from roots or other
   * objects (i.e. ignoring the finalizer list).  Mark the object as a
   * finalizer object.
   *
   * @param object The object whose finalizability is to be tested
   * @return True if the object is finalizable
   */
  @Inline
  @Uninterruptible
  public static boolean isFinalizable(ObjectReference object) {
    setFinalizer(object);
    return object.toAddress().loadInt(RC_HEADER_OFFSET) < HARD_THRESHOLD;
  }

  @NoInline
  @Uninterruptible
  public static void incRCOOL(ObjectReference object) {
    incRC(object);
  }


  /**
   * Increment the reference count of an object, clearing the "purple"
   * status of the object (if it were already purple).  An object is
   * marked purple if it is a potential root of a garbage cycle.  If
   * an object's RC is incremented, it must be live and therefore
   * should not be considered as a potential garbage cycle.  This must
   * be an atomic operation if parallel GC is supported.
   *
   * @param object The object whose RC is to be incremented.
   */
  @Inline
  @Uninterruptible
  public static void incRC(ObjectReference object) {
    int oldValue, newValue;
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(RCBase.isRCObject(object));
    }
    do {
      oldValue = object.toAddress().prepareInt(RC_HEADER_OFFSET);
      newValue = oldValue + INCREMENT;
      if (VM.VERIFY_ASSERTIONS)
        VM.assertions._assert(newValue <= INCREMENT_LIMIT);
      newValue = CD.current().notifyIncRC(newValue);
    } while (!object.toAddress().attempt(oldValue, newValue, RC_HEADER_OFFSET));
  }

  /**
   * Decrement the reference count of an object.  Return either
   * <code>DEC_KILL</code> if the count went to zero,
   * <code>DEC_BUFFER</code> if the count did not go to zero and the
   * object was not already in the purple buffer, and
   * <code>DEC_PURPLE</code> if the count did not go to zero and the
   * object was already in the purple buffer.  This must be an atomic
   * operation if parallel GC is supported.
   *
   * @param object The object whose RC is to be decremented.
   * @return <code>DEC_KILL</code> if the count went to zero,
   * <code>DEC_BUFFER</code> if the count did not go to zero and the
   * object was not already in the purple buffer, and
   * <code>DEC_PURPLE</code> if the count did not go to zero and the
   * object was already in the purple buffer.
   */
  @Inline
  @Uninterruptible
  public static int decRC(ObjectReference object) {
    int oldValue, newValue;
    int rtn;
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(RCBase.isRCObject(object));
      if (!isLiveRC(object)) {
        Log.writeln(object);
      }
      VM.assertions._assert(isLiveRC(object));
    }
    do {
      oldValue = object.toAddress().prepareInt(RC_HEADER_OFFSET);
      newValue = oldValue - INCREMENT;
      if (newValue < LIVE_THRESHOLD) {
        rtn = DEC_KILL;
      } else if (CD.current().shouldBufferOnDecRC(newValue)) {
        newValue = CD.current().updateHeaderOnBufferedDec(newValue);
        rtn = DEC_BUFFER;
      } else {
        newValue = CD.current().updateHeaderOnUnbufferedDec(newValue);
        rtn = DEC_PURPLE;
      }
    } while (!object.toAddress().attempt(oldValue, newValue, RC_HEADER_OFFSET));
    return rtn;
  }

  @Inline
  @Uninterruptible
  public static boolean isBuffered(ObjectReference object) {
    return (object.toAddress().loadInt(RC_HEADER_OFFSET) & BUFFERED_MASK) == BUFFERED_MASK;
  }


  /****************************************************************************
   *
   * Finalization and dealing with roots
   */

  /**
   * Set the <code>ROOT_REACHABLE</code> bit for an object if it is
   * not already set.  Return true if it was not already set, false
   * otherwise.
   *
   * @param object The object whose <code>ROOT_REACHABLE</code> bit is
   * to be set.
   * @return <code>true</code> if it was set by this call,
   * <code>false</code> if the bit was already set.
   */
  @Inline
  @Uninterruptible
  public static boolean setRoot(ObjectReference object) {
    int oldValue, newValue;
    do {
      oldValue = object.toAddress().prepareInt(RC_HEADER_OFFSET);
      if ((oldValue & ROOT_REACHABLE) == ROOT_REACHABLE)
        return false;
      newValue = oldValue | ROOT_REACHABLE;
    } while (!object.toAddress().attempt(oldValue, newValue, RC_HEADER_OFFSET));
    return true;
  }

  /**
   * Clear the <code>ROOT_REACHABLE</code> bit for an object.
   *
   * @param object The object whose <code>ROOT_REACHABLE</code> bit is
   * to be cleared.
   */
  @Inline
  @Uninterruptible
  public static void unsetRoot(ObjectReference object) {
    int oldValue, newValue;
    do {
      oldValue = object.toAddress().prepareInt(RC_HEADER_OFFSET);
      newValue = oldValue & ~ROOT_REACHABLE;
    } while (!object.toAddress().attempt(oldValue, newValue, RC_HEADER_OFFSET));
  }

  /**
   * Set the <code>FINALIZABLE</code> bit for an object.
   *
   * @param object The object whose <code>FINALIZABLE</code> bit is
   * to be set.
   */
  @Inline
  @Uninterruptible
  static void setFinalizer(ObjectReference object) {
    int oldValue, newValue;
    do {
      oldValue = object.toAddress().prepareInt(RC_HEADER_OFFSET);
      newValue = oldValue | FINALIZABLE;
    } while (!object.toAddress().attempt(oldValue, newValue, RC_HEADER_OFFSET));
  }

  /**
   * Clear the <code>FINALIZABLE</code> bit for an object.
   *
   * @param object The object whose <code>FINALIZABLE</code> bit is
   * to be cleared.
   */
  @Inline
  @Uninterruptible
  public static void clearFinalizer(ObjectReference object) {
    int oldValue, newValue;
    do {
      oldValue = object.toAddress().prepareInt(RC_HEADER_OFFSET);
      newValue = oldValue & ~FINALIZABLE;
    } while (!object.toAddress().attempt(oldValue, newValue, RC_HEADER_OFFSET));
  }

  /****************************************************************************
   *
   * Trial deletion support
   */

  /**
   * Decrement the reference count of an object. This is unsychronized.
   *
   * @param object The object whose RC is to be decremented.
   */
  @Inline
  @Uninterruptible
  public static void unsyncDecRC(ObjectReference object) {
    int oldValue, newValue;
    oldValue = object.toAddress().loadInt(RC_HEADER_OFFSET);
    newValue = oldValue - INCREMENT;
    object.toAddress().store(newValue, RC_HEADER_OFFSET);
  }

  /**
   * Increment the reference count of an object. This is unsychronized.
   *
   * @param object The object whose RC is to be incremented.
   */
  @Inline
  @Uninterruptible
  public static void unsyncIncRC(ObjectReference object) {
    int oldValue, newValue;
    oldValue = object.toAddress().loadInt(RC_HEADER_OFFSET);
    newValue = oldValue + INCREMENT;
    object.toAddress().store(newValue, RC_HEADER_OFFSET);
  }

  @Inline
  @Uninterruptible
  public static void print(ObjectReference object) {
    if (object.isNull()) return;
    Log.write(' ');
    Log.write(object.toAddress().loadInt(RC_HEADER_OFFSET)>>INCREMENT_SHIFT);
    Log.write(' ');
    switch (getHiRCColor(object)) {
    case PURPLE: Log.write('p'); break;
    case GREEN: Log.write('g'); break;
    }
    switch (getLoRCColor(object)) {
    case BLACK: Log.write('b'); break;
    case WHITE: Log.write('w'); break;
    case GREY: Log.write('g'); break;
    }
    if (isBuffered(object))
      Log.write('b');
    else
      Log.write('u');
  }
  @Inline
  @Uninterruptible
  public static void clearBufferedBit(ObjectReference object) {
    int oldValue = object.toAddress().loadInt(RC_HEADER_OFFSET);
    int newValue = oldValue & ~BUFFERED_MASK;
    object.toAddress().store(newValue, RC_HEADER_OFFSET);
  }
  @Inline
  @Uninterruptible
  public static boolean isBlack(ObjectReference object) {
    return getLoRCColor(object) == BLACK;
  }
  @Inline
  @Uninterruptible
  public static boolean isWhite(ObjectReference object) {
    return getLoRCColor(object) == WHITE;
  }
  @Inline
  @Uninterruptible
  public static boolean isGreen(ObjectReference object) {
    return getHiRCColor(object) == GREEN;
  }
  @Inline
  @Uninterruptible
  public static boolean isPurple(ObjectReference object) {
    return getHiRCColor(object) == PURPLE;
  }
  @Inline
  @Uninterruptible
  public static boolean isPurpleNotGrey(ObjectReference object) {
    return (object.toAddress().loadInt(RC_HEADER_OFFSET) & (PURPLE | GREY)) == PURPLE;
  }
  @Inline
  @Uninterruptible
  public static boolean isGrey(ObjectReference object) {
    return getLoRCColor(object) == GREY;
  }
  @Inline
  @Uninterruptible
  private static int getLoRCColor(ObjectReference object) {
    return LO_COLOR_MASK & object.toAddress().loadInt(RC_HEADER_OFFSET);
  }
  @Inline
  @Uninterruptible
  private static int getHiRCColor(ObjectReference object) {
    return HI_COLOR_MASK & object.toAddress().loadInt(RC_HEADER_OFFSET);
  }
  @Inline
  @Uninterruptible
  public static void makeBlack(ObjectReference object) {
    changeRCLoColor(object, BLACK);
  }
  @Inline
  @Uninterruptible
  public static void makeWhite(ObjectReference object) {
    changeRCLoColor(object, WHITE);
  }
  @Inline
  @Uninterruptible
  public static void makeGrey(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(getHiRCColor(object) != GREEN);
    changeRCLoColor(object, GREY);
  }
  @Inline
  @Uninterruptible
  private static void changeRCLoColor(ObjectReference object, int color) {
    int oldValue = object.toAddress().loadInt(RC_HEADER_OFFSET);
    int newValue = (oldValue & ~LO_COLOR_MASK) | color;
    object.toAddress().store(newValue, RC_HEADER_OFFSET);
  }
  @Inline
  @Uninterruptible
  public static boolean testAndMark(ObjectReference object, Word markState) {
    int oldValue = object.toAddress().loadInt(RC_HEADER_OFFSET);
    if ((oldValue & MARKED) == markState.toInt()) return false;
    int newValue = oldValue ^ MARKED;
    object.toAddress().store(newValue, RC_HEADER_OFFSET);
    return true;
  }
  @Inline
  @Uninterruptible
  public static boolean isMarked(ObjectReference object, Word markState) {
    int oldValue = object.toAddress().loadInt(RC_HEADER_OFFSET);
    return (oldValue & MARKED) == markState.toInt();
  }

  /****************************************************************************
   *
   * Misc
   */
}
