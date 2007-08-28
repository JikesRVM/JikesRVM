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
package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.plan.refcount.RCHeader;
import org.mmtk.policy.Space;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class is the fundamental mechanism for performing a
 * transitive closure over an object graph.<p>
 *
 * @see org.mmtk.plan.TraceLocal
 */
@Uninterruptible
public final class GenRCModifiedProcessor extends TransitiveClosure {
  private final GenRCTraceLocal trace;


  public GenRCModifiedProcessor(GenRCTraceLocal t) {
    trace = t;
  }

  /**
   * Trace a reference during GC.
   *
   * @param objLoc The location containing the object reference to be
   * traced.
   */
  @Inline
  public void processEdge(Address objLoc) {
    ObjectReference object = objLoc.loadObjectReference();
    if (!object.isNull()) {
      if (Space.isInSpace(GenRC.NS, object)) {
        object = GenRC.nurserySpace.traceObject(trace, object, GenRC.ALLOC_RC);
        RCHeader.incRC(object);
        objLoc.store(object);
      } else if (GenRC.isRCObject(object)) {
        RCHeader.incRC(object);
      }
    }
  }
}
