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

import org.mmtk.plan.TransitiveClosure;
import org.mmtk.plan.refcount.RCBase;
import org.mmtk.plan.refcount.RCHeader;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class is the fundamental mechanism for performing a
 * transitive closure over an object graph.<p>
 *
 * @see org.mmtk.plan.TraceLocal
 */
@Uninterruptible
public final class RCModifiedProcessor extends TransitiveClosure {

  private RCCollector collector;

  public RCModifiedProcessor(RCCollector ctor) {
    this.collector = ctor;
  }

  @Override
  @Inline
  public void processEdge(ObjectReference source, Address slot) {
    ObjectReference object = slot.loadObjectReference();
    if (RCBase.isRCObject(object)) {
      if (RCBase.CC_BACKUP_TRACE && RCBase.performCycleCollection) {
        if (RCHeader.remainRC(object) == RCHeader.INC_NEW) {
          collector.getModBuffer().push(object);
        }
      } else {
        if (RCHeader.incRC(object) == RCHeader.INC_NEW) {
          collector.getModBuffer().push(object);
        }
      }
    }
  }
}
