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
package org.mmtk.plan.copyms;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.Space;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the thread-local functionality for a
 * transitive closure over a coping/mark-sweep hybrid collector.
 */
@Uninterruptible
public final class CopyMSTraceLocal extends TraceLocal {

  /**
   * Constructor
   */
  public CopyMSTraceLocal(Trace trace) {
    super(CopyMS.SCAN_COPYMS, trace);
  }

  /****************************************************************************
   *
   * Externally visible Object processing and tracing
   */

  /**
   * Is the specified object reachable?
   *
   * @param object The object.
   * @return True if the object is live.
   */
  @Override
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (Space.isInSpace(CopyMS.NURSERY, object)) {
      return CopyMS.nurserySpace.isLive(object);
    }
    if (Space.isInSpace(CopyMS.MARK_SWEEP, object)) {
      return CopyMS.msSpace.isLive(object);
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
   * msSpace for tracing, and defer to the superclass for all others.
   *
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  @Inline
  @Override
  public ObjectReference traceObject(ObjectReference object) {
    if (object.isNull()) return object;
    if (Space.isInSpace(CopyMS.NURSERY, object))
      return CopyMS.nurserySpace.traceObject(this, object, CopyMS.ALLOC_MS);
    if (Space.isInSpace(CopyMS.MARK_SWEEP, object))
      return CopyMS.msSpace.traceObject(this, object);
    return super.traceObject(object);
  }

  /**
   * Will this object move from this point on, during the current collection ?
   *
   * @param object The object to query.
   * @return True if the object will not move during this collection.
   */
  @Override
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    return !Space.isInSpace(CopyMS.NURSERY, object);
  }
}
