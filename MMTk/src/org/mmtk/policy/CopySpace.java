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
package org.mmtk.policy;

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.Constants;
import org.mmtk.utility.ForwardingWord;
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


  /****************************************************************************
   *
   * Instance variables
   */
  private boolean fromSpace = true;

  public boolean isFromSpace() {
    return fromSpace;
  }

  /** fromSpace CopySpace can always move, toSpace will not move during current GC */
  @Override
  public boolean isMovable() {
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
   * @param fromSpace The does this instance start life as from-space
   * (or to-space)?
   * @param vmRequest An object describing the virtual memory requested.
   */
  public CopySpace(String name, boolean fromSpace, VMRequest vmRequest) {
    super(name, true, false, vmRequest);
    this.fromSpace = fromSpace;
    if (vmRequest.isDiscontiguous()) {
      pr = new MonotonePageResource(this, META_DATA_PAGES_PER_REGION);
    } else {
      pr = new MonotonePageResource(this, start, extent, META_DATA_PAGES_PER_REGION);
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
    headDiscontiguousRegion = Address.zero();
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
    Word forwardingWord = ForwardingWord.attemptToForward(object);

    if (ForwardingWord.stateIsForwardedOrBeingForwarded(forwardingWord)) {
      /* Somebody else got to it first. */

      /* We must wait (spin) if the object is not yet fully forwarded */
      while (ForwardingWord.stateIsBeingForwarded(forwardingWord))
        forwardingWord = VM.objectModel.readAvailableBitsWord(object);

      /* Now extract the object reference from the forwarding word and return it */
      return ForwardingWord.extractForwardingPointer(forwardingWord);
    } else {
      /* We are the designated copier, so forward it and enqueue it */
      ObjectReference newObject = VM.objectModel.copy(object, allocator);
      ForwardingWord.setForwardingPointer(object, newObject);
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
    return ForwardingWord.isForwarded(object);
  }

  /**
   * Has the object in this space been reached during the current collection.
   * This is used for GC Tracing.
   *
   * @param object The object reference.
   * @return True if the object is reachable.
   */
  public boolean isReachable(ObjectReference object) {
    return !fromSpace || ForwardingWord.isForwarded(object);
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

}
