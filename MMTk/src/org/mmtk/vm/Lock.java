/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id: Lock.java,v 1.4 2006/06/21 07:38:13 steveb-oss Exp $

package org.mmtk.vm;

import org.vmmagic.pragma.Uninterruptible;

/**
 * Simple, fair locks with deadlock detection.
 * 
 * @author Perry Cheng
 * @version $Revision: 1.4 $
 * @date $Date: 2006/06/21 07:38:13 $
 */
public abstract class Lock implements Uninterruptible {

  /**
   * Set the name of this lock instance
   * 
   * @param str The name of the lock (for error output).
   */
  public abstract void setName(String str);


  /**
   * Try to acquire a lock and spin-wait until acquired.
   */
  public abstract void acquire();

  /**
   * Perform sanity checks on the lock. For debugging.
   * 
   * @param w Identifies the code location in the debugging output.
   */
  public abstract void check(int w);

  /**
   * Release the lock by incrementing serving counter.
   */
  public abstract void release();

}
