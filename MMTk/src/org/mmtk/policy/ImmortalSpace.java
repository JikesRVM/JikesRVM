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

import org.mmtk.plan.Plan;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.heap.MonotonePageResource;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.Constants;
import org.mmtk.utility.HeaderByte;

import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements tracing for a simple immortal collection
 * policy.  Under this policy all that is required is for the
 * "collector" to propogate marks in a liveness trace.  It does not
 * actually collect.  This class does not hold any state, all methods
 * are static.
 */
@Uninterruptible public final class ImmortalSpace extends Space
  implements Constants {

  /****************************************************************************
   *
   * Class variables
   */
  static final byte GC_MARK_BIT_MASK = 1;
  private static final int META_DATA_PAGES_PER_REGION = CARD_META_PAGES_PER_REGION;

  /****************************************************************************
   *
   * Instance variables
   */
  private byte markState = 0; // when GC off, the initialization value

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
  public ImmortalSpace(String name, VMRequest vmRequest) {
    this(name, true, vmRequest);
  }

  /**
   * The caller specifies the region of virtual memory to be used for
   * this space.  If this region conflicts with an existing space,
   * then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param zeroed if true, allocations return zeroed memory.
   * @param vmRequest An object describing the virtual memory requested.
   */
  public ImmortalSpace(String name, boolean zeroed, VMRequest vmRequest) {
    super(name, false, true, zeroed, vmRequest);
    if (vmRequest.isDiscontiguous()) {
      pr = new MonotonePageResource(this, META_DATA_PAGES_PER_REGION);
    } else {
      pr = new MonotonePageResource(this, start, extent, META_DATA_PAGES_PER_REGION);
    }
  }

  /** @return the current mark state */
  @Inline
  public Word getMarkState() { return Word.fromIntZeroExtend(markState); }

  /****************************************************************************
   *
   * Object header manipulations
   */

  /**
   * Initialize the object header post-allocation.  We need to set the mark state
   * correctly and set the logged bit if necessary.
   *
   * @param object The newly allocated object instance whose header we are initializing
   */
  public void initializeHeader(ObjectReference object) {
    byte oldValue = VM.objectModel.readAvailableByte(object);
    byte newValue = (byte) ((oldValue & GC_MARK_BIT_MASK) | markState);
    if (HeaderByte.NEEDS_UNLOGGED_BIT) newValue |= HeaderByte.UNLOGGED_BIT;
    VM.objectModel.writeAvailableByte(object, newValue);
  }

  /**
   * Used to mark boot image objects during a parallel scan of objects during GC
   * Returns true if marking was done.
   */
  @Inline
  private static boolean testAndMark(ObjectReference object, byte value) {
    Word oldValue;
    do {
      oldValue = VM.objectModel.prepareAvailableBits(object);
      byte markBit = (byte) (oldValue.toInt() & GC_MARK_BIT_MASK);
      if (markBit == value) return false;
    } while (!VM.objectModel.attemptAvailableBits(object, oldValue,
        oldValue.xor(Word.fromIntZeroExtend(GC_MARK_BIT_MASK))));
    return true;
  }

  /**
   * Trace a reference to an object under an immortal collection
   * policy.  If the object is not already marked, enqueue the object
   * for subsequent processing. The object is marked as (an atomic)
   * side-effect of checking whether already marked.
   *
   * @param trace The trace being conducted.
   * @param object The object to be traced.
   */
  @Inline
  public ObjectReference traceObject(TransitiveClosure trace, ObjectReference object) {
    if (testAndMark(object, markState))
      trace.processNode(object);
    return object;
  }

  /**
   * Prepare for a new collection increment.  For the immortal
   * collector we must flip the state of the mark bit between
   * collections.
   */
  public void prepare() {
    markState = (byte) (GC_MARK_BIT_MASK - markState);
  }

  public void release() {}

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

  @Inline
  public boolean isLive(ObjectReference object) {
    return true;
  }

  /**
   * Returns if the object in question is currently thought to be reachable.
   * This is done by comparing the mark bit to the current mark state. For the
   * immortal collector reachable and live are different, making this method
   * necessary.
   *
   * @param object The address of an object in immortal space to test
   * @return True if <code>ref</code> may be a reachable object (e.g., having
   *         the current mark state).  While all immortal objects are live,
   *         some may be unreachable.
   */
  public boolean isReachable(ObjectReference object) {
    if (Plan.SCAN_BOOT_IMAGE && this == Plan.vmSpace)
      return true;  // ignore boot image "reachabilty" if we're not tracing it
    else
      return (VM.objectModel.readAvailableByte(object) & GC_MARK_BIT_MASK) == markState;
  }
}
