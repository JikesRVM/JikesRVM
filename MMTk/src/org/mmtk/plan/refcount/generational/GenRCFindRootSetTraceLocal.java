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
package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.plan.refcount.RCHeader;
import org.mmtk.policy.Space;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the thread-local core functionality for a transitive
 * closure over the heap graph.
 */
@Uninterruptible
public final class GenRCFindRootSetTraceLocal extends TraceLocal {

  private final ObjectReferenceDeque rootBuffer;

  /**
   * Constructor
   */
  public GenRCFindRootSetTraceLocal(Trace trace, ObjectReferenceDeque rootBuffer) {
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
   * @return <code>true</code> if the object is reachable.
   */
  @Override
  public boolean isLive(ObjectReference object) {
    return GenRC.isRCObject(object) && RCHeader.isLiveRC(object) ||
          (!Space.isInSpace(GenRC.NURSERY, object) && super.isLive(object));
  }

  /**
   * When we trace a non-root object we do nothing.
   */
  @Override
  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    return traceObject(object, false);
  }

  /**
   * When we trace a root object we remember it.
   */
  @Override
  @Inline
  public ObjectReference traceObject(ObjectReference object, boolean root) {
    if (object.isNull()) return object;

    if (Space.isInSpace(GenRC.NURSERY, object)) {
      object = GenRC.nurserySpace.traceObject(this, object, GenRC.ALLOC_RC);
    } else if (!GenRC.isRCObject(object)) {
      return object;
    }

    if (root) {
      rootBuffer.push(object);
    } else {
      RCHeader.incRC(object);
    }

    return object;
  }

  @Override
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!object.isNull());
    return !(Space.isInSpace(GenRC.NURSERY, object));
  }
}
