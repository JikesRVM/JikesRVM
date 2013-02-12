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
package org.mmtk.plan.generational.copying;

import org.mmtk.plan.generational.Gen;
import org.mmtk.plan.generational.GenCollector;
import org.mmtk.plan.generational.GenMatureTraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.Space;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the core functionality for a transitive
 * closure over the heap graph, specifically in a Generational copying
 * collector.
 */
@Uninterruptible
public final class GenCopyMatureTraceLocal extends GenMatureTraceLocal {

  /**
   * Constructor
   */
  public GenCopyMatureTraceLocal(Trace global, GenCollector plan) {
    super(global, plan);
  }

  private static GenCopy global() {
    return (GenCopy) VM.activePlan.global();
  }

  /**
   * Trace a reference into the mature space during GC. This involves
   * determining whether the instance is in from space, and if so,
   * calling the <code>traceObject</code> method of the Copy
   * collector.
   *
   * @param object The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  @Override
  public ObjectReference traceObject(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(global().traceFullHeap());
    if (object.isNull()) return object;

    if (Space.isInSpace(GenCopy.MS0, object))
      return GenCopy.matureSpace0.traceObject(this, object, Gen.ALLOC_MATURE_MAJORGC);
    if (Space.isInSpace(GenCopy.MS1, object))
      return GenCopy.matureSpace1.traceObject(this, object, Gen.ALLOC_MATURE_MAJORGC);
    return super.traceObject(object);
  }

  @Override
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (Space.isInSpace(GenCopy.MS0, object))
      return GenCopy.hi ? GenCopy.matureSpace0.isLive(object) : true;
    if (Space.isInSpace(GenCopy.MS1, object))
      return GenCopy.hi ? true : GenCopy.matureSpace1.isLive(object);
    return super.isLive(object);
  }

  /****************************************************************************
   *
   * Object processing and tracing
   */


  /**
   * {@inheritDoc}
   */
  @Override
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    if (Space.isInSpace(GenCopy.toSpaceDesc(), object)) {
      return true;
    }
    if (Space.isInSpace(GenCopy.fromSpaceDesc(), object)) {
      return false;
    }
    return super.willNotMoveInCurrentCollection(object);
  }
}
