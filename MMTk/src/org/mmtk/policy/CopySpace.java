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
package org.mmtk.policy;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;

import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements tracing functionality for a simple copying
 * space.  Since no state needs to be held globally or locally, all
 * methods are static.
 */
@Uninterruptible public final class CopySpace extends Space
  implements Constants {

  /****************************************************************************
   *
   * Class variables
   */
  public static final int LOCAL_GC_BITS_REQUIRED = 2;
  public static final int GLOBAL_GC_BITS_REQUIRED = 0;
  public static final int GC_HEADER_WORDS_REQUIRED = 0;

  private static final int META_DATA_PAGES_PER_REGION = CARD_META_PAGES_PER_REGION;

  /*
   *  The forwarding process uses three states to deal with a GC race:
   *  1.      !GC_FORWARDED: Unforwarded
   *  2. GC_BEING_FORWARDED: Being forwarded (forwarding is underway)
   *  3.       GC_FORWARDED: Forwarded
   */
  /** If this bit is set, then forwarding of this object has commenced */
  private static final Word GC_FORWARDED = Word.one().lsh(1); // ...10
  /** If this bit is set, then forwarding of this object is incomplete */
  private static final Word GC_BEING_FORWARDED  = Word.one().lsh(2).minus(Word.one());  // ...11
  /** This mask is used to reveal which state this object is in with respect to forwarding */
  private static final Word GC_FORWARDING_MASK  = GC_FORWARDED.or(GC_BEING_FORWARDED);

  /** A single bit is used to indicate a mark when tracing (but not copying) the space */
  private static final Word GC_MARK_BIT_MASK = Word.one();

  /****************************************************************************
   *
   * Instance variables
   */
  private boolean fromSpace = true;

  public boolean isFromSpace() {
    return fromSpace;
  }

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * The caller specifies the region of virtual memory to be used for
   * this space.  If this region conflicts with an existing space,
   * then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume
   * before consulting the plan
   * @param fromSpace The does this instance start life as from-space
   * (or to-space)?
   * @param vmRequest An object describing the virtual memory requested.
   */
  public CopySpace(String name, int pageBudget, boolean fromSpace, VMRequest vmRequest) {
    super(name, true, false, vmRequest);
    this.fromSpace = fromSpace;
    if (vmRequest.isDiscontiguous()) {
      pr = new MonotonePageResource(pageBudget, this, META_DATA_PAGES_PER_REGION);
    } else {
      pr = new MonotonePageResource(pageBudget, this, start, extent, META_DATA_PAGES_PER_REGION);
    }
  }

  /****************************************************************************
   *
   * Prepare and release
   */

  /**
   * Prepare this space instance for a collection.  Set the
   * "fromSpace" field according to whether this space is the
   * source or target of the collection.
   *
   * @param fromSpace Set the fromSpace field to this value
   */
  public void prepare(boolean fromSpace) { this.fromSpace = fromSpace; }

  /**
   * Release this copy space after a collection.  This means releasing
   * all pages associated with this (now empty) space.
   */
  public void release() {
    ((MonotonePageResource) pr).reset();
    lastDiscontiguousRegion = Address.zero();
    fromSpace = false;
  }

  /**
   * Release an allocated page or pages.  In this case we do nothing
   * because we only release pages enmasse.
   *
   * @param start The address of the start of the page or pages
   */
  @Inline
  public void release(Address start) {
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(false); // this policy only releases pages enmasse
  }

  /****************************************************************************
   *
   * Tracing and forwarding
   */

  /**
   * Trace an object under a copying collection policy.
   *
   * We use a tri-state algorithm to deal with races to forward
   * the object.  The tracer must wait if the object is concurrently
   * being forwarded by another thread.
   *
   * If the object is already forwarded, the copy is returned.
   * Otherwise, the object is forwarded and the copy is returned.
   *
   * @param trace The trace being conducted.
   * @param object The object to be forwarded.
   * @return The forwarded object.
   */
  @Inline
  public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object) {
    VM.assertions.fail("CopySpace.traceLocal called without allocator");
    return ObjectReference.nullReference();
  }

  /**
   * Trace an object under a copying collection policy.
   *
   * We use a tri-state algorithm to deal with races to forward
   * the object.  The tracer must wait if the object is concurrently
   * being forwarded by another thread.
   *
   * If the object is already forwarded, the copy is returned.
   * Otherwise, the object is forwarded and the copy is returned.
   *
   * @param trace The trace being conducted.
   * @param object The object to be forwarded.
   * @param allocator The allocator to use when copying.
   * @return The forwarded object.
   */
  @Inline
  public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object, int allocator) {
    /* If the object in question is already in to-space, then do nothing */
    if (!fromSpace) return object;

    /* Try to forward the object */
    Word forwardingPtr = attemptToForward(object);

    if (stateIsForwardedOrBeingForwarded(forwardingPtr)) {
      /* Somebody else got to it first. */

      /* We must wait (spin) if the object is not yet fully forwarded */
      while (stateIsBeingForwarded(forwardingPtr))
        forwardingPtr = getForwardingWord(object);

      /* Now extract the object reference from the forwarding word and return it */
      return forwardingPtr.and(GC_FORWARDING_MASK.not()).toAddress().toObjectReference();
    } else {
      /* We are the designated copier, so forward it and enqueue it */
      ObjectReference newObject = VM.objectModel.copy(object, allocator);
      setForwardingPointer(object, newObject);
      trace.processNode(newObject); // Scan it later

      if (VM.VERIFY_ASSERTIONS && Options.verbose.getValue() >= 9) {
        Log.write("C["); Log.write(object); Log.write("/");
        Log.write(getName()); Log.write("] -> ");
        Log.write(newObject); Log.write("/");
        Log.write(Space.getSpaceForObject(newObject).getName());
        Log.writeln("]");
      }
      return newObject;
    }
  }

  /**
   * Return true if this object is live in this GC
   *
   * @param object The object in question
   * @return True if this object is live in this GC (has it been forwarded?)
   */
  public boolean isLive(ObjectReference object) {
    return isForwarded(object);
  }

  /**
   * Has the object in this space been reached during the current collection.
   * This is used for GC Tracing.
   *
   * @param object The object reference.
   * @return True if the object is reachable.
   */
  public boolean isReachable(ObjectReference object) {
    return !fromSpace || isForwarded(object);
  }

  /****************************************************************************
   *
   * Non-copying tracing (just mark, don't forward)
   */

  /**
   * Mark an object as having been traversed, *WITHOUT* forwarding the object.
   * This is only used when
   *
   * @param object The object to be marked
   * @param markState The sense of the mark bit (flips from 0 to 1)
   */
  @Inline
  public static void markObject(TraceLocal trace, ObjectReference object,
      Word markState) {
    if (testAndMark(object, markState))
      trace.processNode(object);
  }

  /****************************************************************************
   *
   * Header manipulation
   */

  /**
   * Perform any required post-allocation initialization
   *
   * <i>Nothing to be done in this case</i>
   *
   * @param object the object ref to the storage to be initialized
   */
   @Inline
   public void postAlloc(ObjectReference object) {}

  /**
   * Clear the GC portion of the header for an object.
   *
   * @param object the object ref to the storage to be initialized
   */
  @Inline
  public static void clearGCBits(ObjectReference object) {
    Word header = VM.objectModel.readAvailableBitsWord(object);
    VM.objectModel.writeAvailableBitsWord(object, header.and(GC_FORWARDING_MASK.not()));
  }

  /**
   * Has an object been forwarded?
   *
   * @param object The object to be checked
   * @return True if the object has been forwarded
   */
  @Inline
  public static boolean isForwarded(ObjectReference object) {
    return stateIsForwarded(getForwardingWord(object));
  }

  /**
   * Has an object been forwarded or being forwarded?
   *
   * @param object The object to be checked
   * @return True if the object has been forwarded or is being forwarded
   */
  @Inline
  public static boolean isForwardedOrBeingForwarded(ObjectReference object) {
    return stateIsForwardedOrBeingForwarded(getForwardingWord(object));
  }

  /**
   * Non-atomic read of forwarding pointer word
   *
   * @param object The object whose forwarding word is to be read
   * @return The forwarding word stored in <code>object</code>'s
   * header.
   */
  @Inline
  private static Word getForwardingWord(ObjectReference object) {
    return VM.objectModel.readAvailableBitsWord(object);
  }

  /**
   * Non-atomic read of forwarding pointer
   *
   * @param object The object whose forwarding pointer is to be read
   * @return The forwarding pointer stored in <code>object</code>'s
   * header.
   */
  @Inline
  public static ObjectReference getForwardingPointer(ObjectReference object) {
    return getForwardingWord(object).and(GC_FORWARDING_MASK.not()).toAddress().toObjectReference();
  }

  /**
   * Used to mark boot image objects during a parallel scan of objects
   * during GC Returns true if marking was done.
   *
   * @param object The object to be marked
   * @param value The value to store in the mark bit
   */
  @Inline
  private static boolean testAndMark(ObjectReference object, Word value) {
    Word oldValue;
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      Word markBit = oldValue.and(GC_MARK_BIT_MASK);
      if (markBit.EQ(value)) return false;
    } while (!VM.objectModel.attemptAvailableBits(object, oldValue,
                                                oldValue.xor(GC_MARK_BIT_MASK)));
    return true;
  }

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
  private static Word attemptToForward(ObjectReference object) {
    Word oldValue;
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      if (oldValue.and(GC_FORWARDING_MASK).EQ(GC_FORWARDED)) return oldValue;
    } while (!VM.objectModel.attemptAvailableBits(object, oldValue,
                                                oldValue.or(GC_BEING_FORWARDED)));
    return oldValue;
  }

  /**
   * Is the state of the forwarding word being forwarded?
   *
   * @param fword A forwarding word.
   * @return True if the forwarding word's state is being forwarded.
   */
  @Inline
  private static boolean stateIsBeingForwarded(Word fword) {
    return fword.and(GC_FORWARDING_MASK).EQ(GC_BEING_FORWARDED);
  }

  /**
   * Is the state of the forwarding word forwarded?
   *
   * @param fword A forwarding word.
   * @return True if the forwarding word's state is forwarded.
   */
  @Inline
  private static boolean stateIsForwarded(Word fword) {
    return fword.and(GC_FORWARDING_MASK).EQ(GC_FORWARDED);
  }

  /**
   * Is the state of the forwarding word forwarded or being forwarded?
   *
   * @param fword A forwarding word.
   * @return True if the forwarding word's state is forwarded or being
   *         forwarded.
   */
  @Inline
  public static boolean stateIsForwardedOrBeingForwarded(Word fword) {
    return !(fword.and(GC_FORWARDED).isZero());
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
  private static void setForwardingPointer(ObjectReference object,
                                           ObjectReference ptr) {
    VM.objectModel.writeAvailableBitsWord(object, ptr.toAddress().toWord().or(GC_FORWARDED));
  }
}
