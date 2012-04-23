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
package org.mmtk.plan.refcount.fullheap;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.plan.refcount.RCBase;
import org.mmtk.plan.refcount.RCHeader;
import org.mmtk.utility.deque.ObjectReferenceDeque;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the thread-local core functionality for a transitive
 * closure over the heap graph.
 */
@Uninterruptible
public final class RCFindRootSetTraceLocal extends TraceLocal {

  private final ObjectReferenceDeque rootBuffer;

  /**
   * Constructor
   */
  public RCFindRootSetTraceLocal(Trace trace, ObjectReferenceDeque rootBuffer) {
    super(trace);
    this.rootBuffer = rootBuffer;
  }

  /****************************************************************************
   *
   * Externally visible Object processing and tracing
   */

  /**
   * Is the specified object reachable?
   *
   * @param object The object.
   * @return <code>true</code> if the object is reachable.
   */
  @Override
  public boolean isLive(ObjectReference object) {
    return RCBase.isRCObject(object) && RCHeader.isLiveRC(object) || super.isLive(object);
  }

  /**
   * When we trace a non-root object we do nothing.
   *
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  @Override
  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    return object;
  }

  /**
   * When we trace a root object we remember it.
   *
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  @Override
  @Inline
  public ObjectReference traceObject(ObjectReference object, boolean root) {
    if (root && RCBase.isRCObject(object)) {
      rootBuffer.push(object);
    }
    return object;
  }
}
