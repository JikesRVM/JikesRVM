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

import org.mmtk.plan.refcount.RCBaseCollector;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements <i>per-collector thread</i> behavior
 * and state for a cycle detector.
 */
@Uninterruptible public abstract class CDCollector {
  /****************************************************************************
   * Instance fields
   */

  /****************************************************************************
   *
   * Initialization
   */

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a collection phase.
   *
   * @param phaseId Collection phase to execute.
   * @param primary Use this thread to execute any single-threaded collector
   * context actions.
   */
  @Inline
  public boolean collectionPhase(int phaseId, boolean primary) {
    return false;
  }


  /**
   * Buffer an object after a successful update when shouldBufferOnDecRC
   * returned true.
   *
   * @param object The object to buffer.
   */
  public abstract void bufferOnDecRC(ObjectReference object);

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active cycle detector global instance */
  @Inline
  public static CDCollector current() {
    return ((RCBaseCollector)VM.activePlan.collector()).cycleDetector();
  }
}
