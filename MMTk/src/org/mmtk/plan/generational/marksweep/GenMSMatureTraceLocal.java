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
package org.mmtk.plan.generational.marksweep;

import org.mmtk.plan.generational.GenCollector;
import org.mmtk.plan.generational.GenMatureTraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.Space;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implments the core functionality for a transitive
 * closure over the heap graph, specifically in a Generational Mark-Sweep
 * collector.
 */
@Uninterruptible
public final class GenMSMatureTraceLocal extends GenMatureTraceLocal{

  /**
   * Constructor
   */
  public GenMSMatureTraceLocal(Trace global, GenCollector plan) {
    super(global, plan);
  }

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
  @Override
  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    if (object.isNull()) return object;

    if (Space.isInSpace(GenMS.MS, object)) {
      return GenMS.msSpace.traceObject(this, object);
    }
    return super.traceObject(object);
  }

  /**
   * Is the specified object live?
   *
   * @param object The object.
   * @return True if the object is live.
   */
  @Override
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (Space.isInSpace(GenMS.MS, object)) {
      return GenMS.msSpace.isLive(object);
    }
    return super.isLive(object);
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
    if (Space.isInSpace(GenMS.MS, object)) {
      return true;
    }
    return super.willNotMoveInCurrentCollection(object);
  }
}
