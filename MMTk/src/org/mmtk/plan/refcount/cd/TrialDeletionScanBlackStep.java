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
package org.mmtk.plan.refcount.cd;

import org.mmtk.plan.TraceStep;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This trace step is used during trial deletion processing.
 */
@Uninterruptible public final class TrialDeletionScanBlackStep extends TraceStep {

  /**
   * Trace a reference during GC.
   *
   * @param objLoc The location containing the object reference to be
   * traced.
   */
  public void traceObjectLocation(Address objLoc) {
    ObjectReference object = objLoc.loadObjectReference();
    ((TrialDeletionCollector)CDCollector.current()).enumerateScanBlack(object);
  }
}
