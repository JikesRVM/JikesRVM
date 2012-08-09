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
package org.mmtk.plan.refcount.backuptrace;

import org.mmtk.plan.refcount.RCHeader;
import org.mmtk.policy.ExplicitFreeListSpace;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the thread-local core functionality for a transitive
 * closure over the heap graph.
 */
@Uninterruptible
public final class BTSweeper extends ExplicitFreeListSpace.Sweeper {

  @Override
  public boolean sweepCell(ObjectReference object) {
    if (!RCHeader.isMarked(object)) {
      return true;
    } else {
      RCHeader.clearMarked(object);
    }
    return false;
  }
}
