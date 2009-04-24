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
package org.mmtk.plan.nogc;

import org.mmtk.plan.*;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;


/**
 * This class implements the global state of a a simple allocator
 * without a collector.
 */
@Uninterruptible
public class NoGC extends Plan {

  /*****************************************************************************
   * Class variables
   */
  public static final ImmortalSpace noGCSpace = new ImmortalSpace("default", DEFAULT_POLL_FREQUENCY, VMRequest.create());
  public static final int NOGC = noGCSpace.getDescriptor();


  /*****************************************************************************
   * Instance variables
   */
  public final Trace trace = new Trace(metaDataSpace);


  /*****************************************************************************
   * Collection
   */

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase
   */
  @Inline
  @Override
  public final void collectionPhase(short phaseId) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    /*
    if (phaseId == PREPARE) {
    }
    if (phaseId == CLOSURE) {
    }
    if (phaseId == RELEASE) {
    }
    super.collectionPhase(phaseId);
    */
  }

  /*****************************************************************************
   * Accounting
   */

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  The superclass accounts for its spaces, we just
   * augment this with the default space's contribution.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  @Override
  public int getPagesUsed() {
    return (noGCSpace.reservedPages() + super.getPagesUsed());
  }


  /*****************************************************************************
   * Miscellaneous
   */

  /**
   * Register specialized methods.
   */
  @Interruptible
  @Override
  protected void registerSpecializedMethods() {
    super.registerSpecializedMethods();
  }
}
