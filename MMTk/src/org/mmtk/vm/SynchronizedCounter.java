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

  public static void boot() {
  }

  public int reset() {
    return 0;
  }

  // Returns the value before the add
  //
  public int increment() {
    return 0;
  }

  public int peek () {
    return 0;
  }

}
