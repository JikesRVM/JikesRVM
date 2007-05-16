/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.adaptive.measurements.listeners;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;

/**
 * This object that is invoked when online measurement information must
 * be collected.
 */
@Uninterruptible
public abstract class VM_ContextListener extends VM_Listener {

  /**
   * Entry point when listener is awoken.
   *
   * @param sfp  pointer to stack frame where call stack should start
   *             to be examined.
   * @param whereFrom Was this a yieldpoint in a PROLOGUE, BACKEDGE, or
   *            EPILOGUE?
   */
  public abstract void update(Address sfp, int whereFrom);
}
