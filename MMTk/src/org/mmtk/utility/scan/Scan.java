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
package org.mmtk.utility.scan;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.TraceStep;

import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Class that supports scanning of objects (scalar and array)
 */
@Uninterruptible public final class Scan {
  /**
   * Scan a object, processing each pointer field encountered.
   *
   * @param trace The trace to use when scanning.
   * @param object The object to be scanned.
   */
  @Inline
  public static void scanObject(TraceStep trace,
                                ObjectReference object) {
    MMType type = VM.objectModel.getObjectType(object);
    if (!type.isDelegated()) {
      int references = type.getReferences(object);
      for (int i = 0; i < references; i++) {
        Address slot = type.getSlot(object, i);
        trace.traceObjectLocation(slot);
      }
    } else
      VM.scanning.scanObject(trace, object);
  }

  /**
   * Scan a object, pre-copying each child object encountered.
   *
   * @param trace The trace to use when precopying.
   * @param object The object to be scanned.
   */
  @Inline
  public static void precopyChildren(TraceLocal trace, ObjectReference object) {
    MMType type = VM.objectModel.getObjectType(object);
    if (!type.isDelegated()) {
      int references = type.getReferences(object);
      for (int i = 0; i < references; i++) {
        Address slot = type.getSlot(object, i);
        trace.precopyObjectLocation(slot);
      }
    } else
      VM.scanning.precopyChildren(trace, object);
  }
}
