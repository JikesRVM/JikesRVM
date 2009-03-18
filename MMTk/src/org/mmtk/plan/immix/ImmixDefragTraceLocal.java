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
package org.mmtk.plan.immix;

import static org.mmtk.policy.immix.ImmixConstants.MARK_LINE_AT_SCAN_TIME;

import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.Space;
import org.mmtk.policy.immix.ImmixSpace;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the thread-local functionality for a defragmenting
 * transitive closure over an immix space.
 */
@Uninterruptible
public final class ImmixDefragTraceLocal extends TraceLocal {

  /****************************************************************************
  *
  * Instance fields
  */
 private final ObjectReferenceDeque modBuffer;

 /**
   * Constructor
   * @param modBuffer TODO
   */
  public ImmixDefragTraceLocal(Trace trace, ObjectReferenceDeque modBuffer) {
    super(Immix.SCAN_DEFRAG, trace);
    this.modBuffer = modBuffer;
  }

  /****************************************************************************
   *
   * Externally visible Object processing and tracing
   */

  /**
   * Is the specified object live?
   *
   * @param object The object.
   * @return True if the object is live.
   */
  public boolean isLive(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Immix.immixSpace.inImmixDefragCollection());
    if (object.isNull()) return false;
    if (Space.isInSpace(Immix.IMMIX, object)) {
      return Immix.immixSpace.isLive(object);
    }
    return super.isLive(object);
  }

  /**
   * This method is the core method during the trace of the object graph.
   * The role of this method is to:
   *
   * 1. Ensure the traced object is not collected.
   * 2. If this is the first visit to the object enqueue it to be scanned.
   * 3. Return the forwarded reference to the object.
   *
   * In this instance, we refer objects in the mark-sweep space to the
   * immixSpace for tracing, and defer to the superclass for all others.
   *
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Immix.immixSpace.inImmixDefragCollection());
    if (object.isNull()) return object;
    if (Space.isInSpace(Immix.IMMIX, object))
      return Immix.immixSpace.traceObject(this, object, Plan.ALLOC_DEFAULT);
    return super.traceObject(object);
  }

  @Inline
  @Override
  public ObjectReference precopyObject(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Immix.immixSpace.inImmixDefragCollection());
    ObjectReference rtn = traceObject(object);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(willNotMoveInCurrentCollection(rtn));
    return rtn;
  }

  /**
   * Return true if this object is guaranteed not to move during this
   * collection (i.e. this object is defintely not an unforwarded
   * object).
   *
   * @param object
   * @return True if this object is guaranteed not to move during this
   *         collection.
   */
  @Override
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Immix.immixSpace.inImmixDefragCollection());
    if (Space.isInSpace(Immix.IMMIX, object))
      return Immix.immixSpace.willNotMoveThisGC(object);
    return true;
  }

  /**
   * Collectors that move objects <b>must</b> override this method.
   * It performs the deferred scanning of objects which are forwarded
   * during bootstrap of each copying collection.  Because of the
   * complexities of the collection bootstrap (such objects are
   * generally themselves gc-critical), the forwarding and scanning of
   * the objects must be dislocated.  It is an error for a non-moving
   * collector to call this method.
   *
   * @param object The forwarded object to be scanned
   */
  @Inline
  @Override
  protected void scanObject(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Immix.immixSpace.inImmixDefragCollection());
    super.scanObject(object);
    if (MARK_LINE_AT_SCAN_TIME && Space.isInSpace(Immix.IMMIX, object))
       ImmixSpace.markLines(object);
  }

  /**
   * Process any remembered set entries.  This means enumerating the
   * mod buffer and for each entry, marking the object as unlogged
   * (we don't enqueue for scanning since we're doing a full heap GC).
   */
  protected void processRememberedSets() {
    if (modBuffer != null) {
      logMessage(5, "clearing modBuffer");
      while (!modBuffer.isEmpty()) {
        ObjectReference src = modBuffer.pop();
        Plan.markAsUnlogged(src);
      }
    }
  }
}
