/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package org.mmtk.vm;


/**
 * A counter that supports atomic increment and reset.
 *
 * @author Perry Cheng
 */
public final class SynchronizedCounter {

  /**
   * Reset the counter to 0, returning its previous value.
   * @return The value of the counter, prior to reset.
   */
  public int reset() {
    return 0;
  }

  /**
   * Adds 1 to the counter.
   * 
   * @return the value before the add
   */
  public int increment() {
    return 0;
  }

  /**
   * Peek at the counter
   * 
   * @return The current value of the counter.
   */
  public int peek () {
    return 0;
  }

}
