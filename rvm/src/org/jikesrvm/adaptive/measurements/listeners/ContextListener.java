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
package org.jikesrvm.adaptive.measurements.listeners;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;

/**
 * This object that is invoked when online measurement information must
 * be collected.
 */
@Uninterruptible
public abstract class ContextListener extends Listener {

  /**
   * This method is called when its time to record that a yield point has occurred.
   *
   * @param sfp  pointer to stack frame where call stack should start
   *             to be examined.
   * @param whereFrom Was this a yieldpoint in a PROLOGUE, BACKEDGE, or
   *            EPILOGUE?
   */
  public abstract void update(Address sfp, int whereFrom);
}
