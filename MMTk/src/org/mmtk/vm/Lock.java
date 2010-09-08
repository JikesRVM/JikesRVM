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
package org.mmtk.vm;

import org.vmmagic.pragma.Uninterruptible;

/**
 * Simple, fair locks with deadlock detection.
 */
@Uninterruptible
public abstract class Lock {

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
