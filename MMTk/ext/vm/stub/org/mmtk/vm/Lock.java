/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package org.mmtk.vm;

/**
 * Simple, fair locks with deadlock detection.
 *
 * @author Perry Cheng
 * @version $Revision$
 * @date $Date$
 */
public class Lock {
  /**
   * Constructor.
   * 
   * @param str The name of the lock (for error output).
   */
  public Lock(String str) {}

  /**
   * Try to acquire a lock and spin-wait until acquired.
   */
  public void acquire() {}

  /**
   * Perform sanity checks on the lock.  For debugging.
   * 
   * @param w Identifies the code location in the debugging output.
   */
  public void check (int w) {}

  /**
   * Release the lock by incrementing serving counter.
   */
  public void release() {}

}
