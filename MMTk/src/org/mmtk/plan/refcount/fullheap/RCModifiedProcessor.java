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
package org.mmtk.plan.refcount.fullheap;

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.plan.refcount.RCHeader;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class is the fundamental mechanism for performing a
 * transitive closure over an object graph.<p>
 *
 * @see org.mmtk.plan.TraceLocal
 */
@Uninterruptible
public final class RCModifiedProcessor extends TransitiveClosure {

  /**
   * Trace a reference during GC.
   *
   * @param objLoc The location containing the object reference to be
   * traced.
   */
  public void processEdge(Address objLoc) {
    ObjectReference object = objLoc.loadObjectReference();
    if (RC.isRCObject(object)) {
      RCHeader.incRC(object);
    }
  }
}
