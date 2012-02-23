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
package org.mmtk.utility;

import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Word;

/**
 * This class provides generic support for object forwarding, which is specific
 * to a few policies that support copying.  The broad idea is two-fold: 1) the
 * two low-order bits of the GC byte (which are also the two low-order bits of
 * the header word) are used to indicate whether an object has been forwarded
 * or is being forwarded, and 2) if an object has been forwarded then the entire
 * header word of the dead object is used to store a pointer to the forwarded
 * pointer.   This is a standard implementation of forwarding.<p>
 *
 * The two lowest order bits are used for object forwarding because forwarding
 * generally must steal the unused two low order bits of the forwarding pointer.
 */
@Uninterruptible
public class ForwardingWord {
  /*
   *  The forwarding process uses three states to deal with a GC race:
   *  1.      !FORWARDED: Unforwarded
   *  2. BEING_FORWARDED: Being forwarded (forwarding is underway)
   *  3.       FORWARDED: Forwarded
   */
  /** If this bit is set, then forwarding of this object is incomplete */
  private static final byte BEING_FORWARDED = 2; // ...10
  /** If this bit is set, then forwarding of this object has commenced */
  private static final byte FORWARDED =       3; // ...11
  /** This mask is used to reveal which state this object is in with respect to forwarding */
  public static final byte FORWARDING_MASK =  3; // ...11

  public static final int FORWARDING_BITS = 2;


  /**
   * Either return the forwarding pointer if the object is already
   * forwarded (or being forwarded) or write the bit pattern that
   * indicates that the object is being forwarded
   *
   * @param object The object to be forwarded
   * @return The forwarding pointer for the object if it has already
   * been forwarded.
   */
  @Inline
  public static Word attemptToForward(ObjectReference object) {
    Word oldValue;
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      if ((byte) (oldValue.toInt() & FORWARDING_MASK) == FORWARDED)
        return oldValue;
    } while (!VM.objectModel.attemptAvailableBits(object, oldValue,
                                                  oldValue.or(Word.fromIntZeroExtend(BEING_FORWARDED))));
    return oldValue;
  }

  public static ObjectReference spinAndGetForwardedObject(ObjectReference object, Word statusWord) {
    /* We must wait (spin) if the object is not yet fully forwarded */
    while ((statusWord.toInt() & FORWARDING_MASK) == BEING_FORWARDED)
      statusWord = VM.objectModel.readAvailableBitsWord(object);

    /* Now extract the object reference from the forwarding word and return it */
    if ((statusWord.toInt() & FORWARDING_MASK) == FORWARDED)
      return statusWord.and(Word.fromIntZeroExtend(FORWARDING_MASK).not()).toAddress().toObjectReference();
    else
      return object;
  }

  public static ObjectReference forwardObject(ObjectReference object, int allocator) {
    ObjectReference newObject = VM.objectModel.copy(object, allocator);
    VM.objectModel.writeAvailableBitsWord(object, newObject.toAddress().toWord().or(Word.fromIntZeroExtend(FORWARDED)));
    return newObject;
  }

  /**
   * Non-atomic write of forwarding pointer word (assumption, thread
   * doing the set has done attempt to forward and owns the right to
   * copy the object)
   *
   * @param object The object whose forwarding pointer is to be set
   * @param ptr The forwarding pointer to be stored in the object's
   * forwarding word
   */
  @Inline
  public static void setForwardingPointer(ObjectReference object,
                                           ObjectReference ptr) {
    VM.objectModel.writeAvailableBitsWord(object, ptr.toAddress().toWord().or(Word.fromIntZeroExtend(FORWARDED)));
  }

  /**
   * Has an object been forwarded?
   *
   * @param object The object to be checked
   * @return True if the object has been forwarded
   */
  @Inline
  public static boolean isForwarded(ObjectReference object) {
    return (VM.objectModel.readAvailableByte(object) & FORWARDING_MASK) == FORWARDED;
  }

  /**
   * Has an object been forwarded or is it being forwarded?
   *
   * @param object The object to be checked
   * @return True if the object has been forwarded
   */
  @Inline
  public static boolean isForwardedOrBeingForwarded(ObjectReference object) {
    return (VM.objectModel.readAvailableByte(object) & FORWARDING_MASK) != 0;
  }

  /**
   * Has an object been forwarded or being forwarded?
   *
   * @param header The object header to be checked
   * @return True if the object has been forwarded
   */
  @Inline
  public static boolean stateIsForwardedOrBeingForwarded(Word header) {
    return (header.toInt() & FORWARDING_MASK) != 0;
  }

  /**
   * Has an object been forwarded or being forwarded?
   *
   * @param header The object header to be checked
   * @return True if the object has been forwarded
   */
  @Inline
  public static boolean stateIsBeingForwarded(Word header) {
    return (header.toInt() & FORWARDING_MASK) == BEING_FORWARDED;
  }

  /**
   * Clear the GC forwarding portion of the header for an object.
   *
   * @param object the object ref to the storage to be initialized
   */
  @Inline
  public static void clearForwardingBits(ObjectReference object) {
    VM.objectModel.writeAvailableByte(object, (byte) (VM.objectModel.readAvailableByte(object) & ~FORWARDING_MASK));
  }

  @Inline
  public static ObjectReference extractForwardingPointer(Word forwardingWord) {
    return forwardingWord.and(Word.fromIntZeroExtend(FORWARDING_MASK).not()).toAddress().toObjectReference();
  }
}
