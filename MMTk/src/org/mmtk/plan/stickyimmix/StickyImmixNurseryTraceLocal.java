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
package org.mmtk.plan.stickyimmix;

import static org.mmtk.policy.immix.ImmixConstants.MARK_LINE_AT_SCAN_TIME;
import static org.mmtk.policy.immix.ImmixConstants.PREFER_COPY_ON_NURSERY_GC;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.Space;
import org.mmtk.utility.HeaderByte;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the thread-local functionality for a transitive
 * closure over a sticky-immix space.
 */
@Uninterruptible
public final class StickyImmixNurseryTraceLocal extends TraceLocal {

  /****************************************************************************
  *
  * Instance fields.
  */
 private final ObjectReferenceDeque modBuffer;

  /**
   * Constructor
   */
  public StickyImmixNurseryTraceLocal(Trace trace, ObjectReferenceDeque modBuffer) {
    super(StickyImmix.SCAN_NURSERY, trace);
    this.modBuffer = modBuffer;
  }

  /****************************************************************************
   *
   * Externally visible Object processing and tracing
   */

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (Space.isInSpace(StickyImmix.IMMIX, object))
      return PREFER_COPY_ON_NURSERY_GC ? StickyImmix.immixSpace.copyNurseryIsLive(object) : StickyImmix.immixSpace.fastIsLive(object);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(super.isLive(object));
    return true;
  }

  @Override
  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    if (object.isNull()) return object;
    if (Space.isInSpace(StickyImmix.IMMIX, object))
      return StickyImmix.immixSpace.nurseryTraceObject(this, object, StickyImmix.ALLOC_DEFAULT);
    else
      return object;
  }

  /**
   * Return true if this object is guaranteed not to move during this
   * collection (i.e. this object is definitely not an unforwarded
   * object).
   *
   * @param object
   * @return True if this object is guaranteed not to move during this
   *         collection.
   */
  @Override
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    if (Space.isInSpace(StickyImmix.IMMIX, object)) {
      if (!PREFER_COPY_ON_NURSERY_GC)
        return true;
      else
        return StickyImmix.immixSpace.willNotMoveThisNurseryGC(object);
    }
    return super.willNotMoveInCurrentCollection(object);
  }

  @Inline
  @Override
  protected void scanObject(ObjectReference object) {
    super.scanObject(object);
    if (MARK_LINE_AT_SCAN_TIME && Space.isInSpace(StickyImmix.IMMIX, object))
      StickyImmix.immixSpace.markLines(object);
  }

  /**
   * Process any remembered set entries.  This means enumerating the
   * mod buffer and for each entry, marking the object as unlogged
   * and enqueing it for scanning.
   */
  @Override
  protected void processRememberedSets() {
    logMessage(2, "processing modBuffer");
    while (!modBuffer.isEmpty()) {
      ObjectReference src = modBuffer.pop();
      HeaderByte.markAsUnlogged(src);
      processNode(src);
    }
  }
}
