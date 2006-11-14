/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package org.mmtk.vm;

import org.vmmagic.pragma.Uninterruptible;


/**
 * A counter that supports atomic increment and reset.
 * 
 * @author Perry Cheng
 */
public abstract class SynchronizedCounter implements Uninterruptible {

  /**
   * Reset the counter to 0, returning its previous value.
   * 
   * @return The value of the counter, prior to reset.
   */
  public abstract int reset();

  /**
   * Adds 1 to the counter.
   * 
   * @return the value before the add
   */
  public abstract int increment();

  /**
   * Peek at the counter
   * 
   * @return The current value of the counter.
   */
  public abstract int peek();
}
