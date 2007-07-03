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
package org.jikesrvm.adaptive.measurements.listeners;

import org.vmmagic.pragma.Uninterruptible;

/**
 * A VM_NullListener is an object that is invoked when
 * online measurement information must be collected.
 *
 * Defines update's interface.
 */

@Uninterruptible
public abstract class VM_NullListener extends VM_Listener {
  /**
   * Entry point when listener is awoken.
   * @param whereFrom Was this a yieldpoint in a PROLOGUE, BACKEDGE, or
   *             EPILOGUE?
   */
  public abstract void update(int whereFrom);
}
