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
package org.mmtk.plan.semispace.gcspy;

import org.mmtk.plan.Trace;
import org.mmtk.plan.semispace.SSTraceLocal;
import org.mmtk.policy.Space;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implments the core functionality for a transitive
 * closure over the heap graph.
 */
@Uninterruptible
public final class SSGCspyTraceLocal extends SSTraceLocal {
  /**
   * Constructor
   */
  public SSGCspyTraceLocal(Trace trace) {
    super(trace);
  }

  /****************************************************************************
   *
   * Externally visible Object processing and tracing
   */

  /**
   * This method is the core method during the trace of the object graph.
   * The role of this method is to:
   *
   * 1. Ensure the traced object is not collected.
   * 2. If this is the first visit to the object enqueue it to be scanned.
   * 3. Return the forwarded reference to the object.
   *
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    if (object.isNull()) return object;
    if (Space.isInSpace(SSGCspy.GCSPY, object))
      return SSGCspy.gcspySpace.traceObject(this, object);
    return super.traceObject(object);
  }

  /**
   * Will this object move from this point on, during the current trace ?
   *
   * @param object The object to query.
   * @return True if the object will not move.
   */
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    if (Space.isInSpace(SSGCspy.GCSPY, object))
      return true;
    return super.willNotMoveInCurrentCollection(object);
  }
}
