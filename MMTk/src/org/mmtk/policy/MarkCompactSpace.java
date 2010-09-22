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

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;

import org.mmtk.vm.Lock;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements functionality for a simple sliding mark-compact
 * space.
 */
@Uninterruptible public final class MarkCompactSpace extends Space
  implements Constants {

  /****************************************************************************
   *
   * Class variables
   */
  public static final int LOCAL_GC_BITS_REQUIRED = 1;
  public static final int GLOBAL_GC_BITS_REQUIRED = 0;
  public static final int GC_HEADER_WORDS_REQUIRED = 1;

  private static final Word GC_MARK_BIT_MASK = Word.one();
  private static final Offset FORWARDING_POINTER_OFFSET = VM.objectModel.GC_HEADER_OFFSET();

  private static final Lock lock = VM.newLock("mcSpace");

  /** The list of occupied regions */
  private Address regionList = Address.zero();

  // TODO - maintain a separate list of partially allocated regions
  // for threads to allocate into immediately after a collection.

  /****************************************************************************
   *
   * Instance variables
   */

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
   * @param vmRequest An object describing the virtual memory requested.
   */
  public MarkCompactSpace(String name, VMRequest vmRequest) {
    super(name, true, false, vmRequest);
    if (vmRequest.isDiscontiguous()) {
      pr = new FreeListPageResource(this, 0);
    } else {
      pr = new FreeListPageResource(this, start, extent, 0);
    }
  }

  /**
   * Prepare for a collection
   */
  public void prepare() {
  }

  /**
   * Release after a collection
   */
  public void release() {
    // nothing to do
  }


  /**
   * Release an allocated page or pages.  In this case we do nothing
   * because we only release pages enmasse.
   *
   * @param start The address of the start of the page or pages
   */
  @Override
  @Inline
  public void release(Address start) {
    ((FreeListPageResource)pr).releasePages(start);
  }

  /**
   * Trace an object under a copying collection policy.
   * If the object is already copied, the copy is returned.
   * Otherwise, a copy is created and returned.
   * In either case, the object will be marked on return.
   *
   * @param trace The trace being conducted.
   * @param object The object to be forwarded.
   * @return The forwarded object.
   */
  @Override
  @Inline
  public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(false);
    return null;
  }

  /**
   * Trace an object under a copying collection policy.
   * If the object is already copied, the copy is returned.
   * Otherwise, a copy is created and returned.
   * In either case, the object will be marked on return.
   *
   * @param trace The trace being conducted.
   * @param object The object to be forwarded.
   * @return The forwarded object.
   */
  @Inline
  public ObjectReference traceMarkObject(TraceLocal trace, ObjectReference object) {
    if (MarkCompactCollector.VERY_VERBOSE) {
      Log.write("marking "); Log.write(object);
    }
    if (testAndMark(object)) {
      trace.processNode(object);
    } else if (!getForwardingPointer(object).isNull()) {
      if (MarkCompactCollector.VERY_VERBOSE) {
        Log.write(" -> "); Log.writeln(getForwardingPointer(object));
      }
      return getForwardingPointer(object);
    }
    if (MarkCompactCollector.VERY_VERBOSE) {
      Log.writeln();
    }
    return object;
  }

  /**
   * Trace an object under a copying collection policy.
   * If the object is already copied, the copy is returned.
   * Otherwise, a copy is created and returned.
   * In either case, the object will be marked on return.
   *
   * @param trace The trace being conducted.
   * @param object The object to be forwarded.
   * @return The forwarded object.
   */
  @Inline
  public ObjectReference traceForwardObject(TraceLocal trace, ObjectReference object) {
    if (testAndClearMark(object)) {
      trace.processNode(object);
    }
    ObjectReference newObject = getForwardingPointer(object);
    if (MarkCompactCollector.VERY_VERBOSE) {
      Log.write("forwarding "); Log.write(object);
      Log.write(" -> "); Log.writeln(newObject);
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!newObject.isNull());
    return getForwardingPointer(object);
  }

  /**
   * Is this object live?
   *
   * @param object The object
   * @return True if the object is live
   */
  @Override
  public boolean isLive(ObjectReference object) {
    return isMarked(object);
  }

  /**
   * Has the object in this space been reached during the current collection.
   * This is used for GC Tracing.
   *
   * @param object The object reference.
   * @return True if the object is reachable.
   */
  @Override
  public boolean isReachable(ObjectReference object) {
    return isMarked(object);
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
  public void postAlloc(ObjectReference object) {
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
    return object.toAddress().loadObjectReference(FORWARDING_POINTER_OFFSET);
  }

  /**
   * Initialise the header of the object.
   *
   * @param object The object to initialise
   */
  @Inline
  public void initializeHeader(ObjectReference object) {
    // nothing to do
  }

  /**
   * Used to mark boot image objects during a parallel scan of objects
   * during GC Returns true if marking was done.
   *
   * @param object The object to be marked
   */
  @Inline
  public static boolean testAndMark(ObjectReference object) {
    Word oldValue;
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      Word markBit = oldValue.and(GC_MARK_BIT_MASK);
      if (!markBit.isZero()) return false;
    } while (!VM.objectModel.attemptAvailableBits(object, oldValue,
                                                oldValue.or(GC_MARK_BIT_MASK)));
    return true;
  }

  /**
   * Used to mark boot image objects during a parallel scan of objects
   * during GC Returns true if marking was done.
   *
   * @param object The object to be marked
   */
  @Inline
  public static boolean isMarked(ObjectReference object) {
    Word oldValue = VM.objectModel.readAvailableBitsWord(object);
    Word markBit = oldValue.and(GC_MARK_BIT_MASK);
    return (!markBit.isZero());
  }

  /**
   * Used to mark boot image objects during a parallel scan of objects
   * during GC Returns true if marking was done.
   *
   * @param object The object to be marked
   */
  @Inline
  private static boolean testAndClearMark(ObjectReference object) {
    Word oldValue;
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      Word markBit = oldValue.and(GC_MARK_BIT_MASK);
      if (markBit.isZero()) return false;
    } while (!VM.objectModel.attemptAvailableBits(object, oldValue,
                                                oldValue.and(GC_MARK_BIT_MASK.not())));
    return true;
  }


  /**
   * Used to mark boot image objects during a parallel scan of objects
   * during GC Returns true if marking was done.
   *
   * @param object The object to be marked
   */
  @Inline
  public static boolean toBeCompacted(ObjectReference object) {
    Word oldValue = VM.objectModel.readAvailableBitsWord(object);
    Word markBit = oldValue.and(GC_MARK_BIT_MASK);
    return !markBit.isZero() && getForwardingPointer(object).isNull();
  }

  /**
   * Used to mark boot image objects during a parallel scan of objects
   * during GC Returns true if marking was done.
   *
   * @param object The object to be marked
   */
  @Inline
  public static void clearMark(ObjectReference object) {
    Word oldValue = VM.objectModel.readAvailableBitsWord(object);
    VM.objectModel.writeAvailableBitsWord(object, oldValue.and(GC_MARK_BIT_MASK.not()));
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
    object.toAddress().store(ptr.toAddress(), FORWARDING_POINTER_OFFSET);
  }

  /**
   * Non-atomic clear of forwarding pointer word (assumption, thread
   * doing the set has done attempt to forward and owns the right to
   * copy the object)
   *
   * @param object The object whose forwarding pointer is to be set
   */
  @Inline
  public static void clearForwardingPointer(ObjectReference object) {
    object.toAddress().store(Address.zero(), FORWARDING_POINTER_OFFSET);
  }

  /**
   * @return A region of this space that has net yet been compacted during
   *   the current collection
   */
  public Address getNextRegion() {
    lock.acquire();
    if (regionList.isZero()) {
      lock.release();
      return Address.zero();
    }
    Address result = regionList;
    regionList = BumpPointer.getNextRegion(regionList);
    BumpPointer.clearNextRegion(result);
    lock.release();
    return result;
  }

  /**
   * Append a region or list of regions to the global list
   * @param region
   */
  public void append(Address region) {
    lock.acquire();
    if (MarkCompactCollector.VERBOSE) {
      Log.write("Appending region "); Log.write(region);
      Log.writeln(" to global list");
    }
    if (regionList.isZero()) {
      regionList = region;
    } else {
      appendRegion(regionList,region);
    }
    lock.release();
  }

  public static void appendRegion(Address listHead, Address region) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!listHead.isZero());
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!region.isZero());
    Address cursor = listHead;
    while (!BumpPointer.getNextRegion(cursor).isZero()) {
      cursor = BumpPointer.getNextRegion(cursor);
    }
    BumpPointer.setNextRegion(cursor,region);
  }
}
