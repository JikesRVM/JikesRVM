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
import org.mmtk.policy.ExplicitLargeObjectSpace;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the scanning of dead large objects during a backup trace.
 */
@Uninterruptible
public final class BTScanLargeObjectSweeper extends ExplicitLargeObjectSpace.Sweeper {

  private final BTDecMarked sdm = new BTDecMarked();

  public boolean sweepLargeObject(ObjectReference object) {
    if (!RCHeader.isMarked(object)) {
      VM.scanning.scanObject(sdm, object);
    }
    // Do not free any objects at this time
    return false;
  }
}
