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
package org.mmtk.utility.sanitychecker;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the parallel root-gathering part of a sanity check.
 */
@Uninterruptible
public final class SanityRootTraceLocal extends TraceLocal {

  /**
   * Constructor
   */
  public SanityRootTraceLocal(Trace trace) {
    super(trace);
  }

  /****************************************************************************
   *
   * Object processing and tracing
   */

  /**
   * Copy root values across to the 'real' single-threaded trace that will do
   * the sanity checking.
   */
  @Inline
  public void copyRootValuesTo(TraceLocal trace) {
    while (!rootLocations.isEmpty()) {
      ObjectReference object = rootLocations.pop().loadObjectReference();
      if (!object.isNull()) {
        trace.traceObject(object, true);
      }
    }
    while (!values.isEmpty()) {
      trace.traceObject(values.pop(), true);
    }
  }

  /**
   * Process delayed roots. This does not make sense for SanityRootTraceLocal.
   * are empty.
   */
  @Override
  @Inline
  public void processRoots() {
    VM.assertions.fail("SanityRootTraceLocal.processRoots called.");
  }

  /**
   * Finishing processing all GC work. This does not make sense for SanityRootTraceLocal.
   */
  @Override
  @Inline
  public void completeTrace() {
    VM.assertions.fail("SanityRootTraceLocal.completeTrace called.");
  }

  /**
   * This method is the core method during the trace of the object graph.
   * The role of this method is to:
   *
   * @param object The object to be traced.
   * @param root Is this object a root?
   * @return The new reference to the same object instance.
   */
  @Override
  @Inline
  public ObjectReference traceObject(ObjectReference object, boolean root) {
    if (!root) VM.assertions.fail("SanityRootTraceLocal.traceObject called for non-root object.");
    if (!object.isNull()) {
      values.push(object);
    }
    return object;
  }

  /**
   * Will this object move from this point on, during the current trace ?
   *
   * @param object The object to query.
   * @return True if the object will not move.
   */
  @Override
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    // We never move objects!
    return true;
  }
}
