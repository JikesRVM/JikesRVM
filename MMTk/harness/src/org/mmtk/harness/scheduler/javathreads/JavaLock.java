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
package org.mmtk.harness.scheduler.javathreads;

import org.vmmagic.pragma.Uninterruptible;

/**
 * Simple lock.
 */
@Uninterruptible
public class JavaLock extends org.mmtk.harness.scheduler.Lock {

  /** Create a new lock (with given name) */
  public JavaLock(String name) {
    super(name);
  }

  /**
   * Try to acquire a lock and wait until acquired.
   */
  @Override
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
  @Override
  public void check(int w) {
    System.err.println("[" + name + "] AT " + w + " held by " + holder);
  }

  /**
   * Release the lock.
   */
  @Override
  public void release() {
    synchronized(this) {
      holder = null;
      this.notifyAll();
    }
  }
}
