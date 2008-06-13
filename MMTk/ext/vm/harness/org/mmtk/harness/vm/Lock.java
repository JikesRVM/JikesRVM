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
package org.mmtk.harness.vm;

import org.vmmagic.pragma.Uninterruptible;

/**
 * Simple lock.
 */
@Uninterruptible
public class Lock extends org.mmtk.vm.Lock {

  /** The name of this lock */
  private String name;

  /** The current holder of the lock */
  private Thread holder;

  /** Create a new lock (with given name) */
  Lock(String name) {
    setName(name);
  }

  /**
   * Set the name of this lock instance
   *
   * @param str The name of the lock (for error output).
   */
  public void setName(String str) {
    this.name = str;
  }

  /**
   * Try to acquire a lock and wait until acquired.
   */
  public void acquire() {
    synchronized(this) {
      while(holder != null) {
        try {
          this.wait();
        } catch (InterruptedException ie) {}
      }
      holder = Thread.currentThread();
    }
  }

  /**
   * Perform sanity checks on the lock. For debugging.
   *
   * @param w Identifies the code location in the debugging output.
   */
  public void check(int w) {
    System.err.println("[" + name + "] AT " + w + " held by " + holder);
  }

  /**
   * Release the lock.
   */
  public void release() {
    synchronized(this) {
      holder = null;
      this.notifyAll();
    }
  }
}
