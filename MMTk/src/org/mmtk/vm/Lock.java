/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package org.mmtk.vm;

import org.vmmagic.pragma.Uninterruptible;

/**
 * Simple, fair locks with deadlock detection.
 * 
 * @author Perry Cheng
 * @version $Revision$
 * @date $Date$
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
