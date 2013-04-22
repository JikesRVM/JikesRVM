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

import org.mmtk.harness.Harness;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Simple lock.
 */
@Uninterruptible
public class JavaLock extends org.mmtk.harness.scheduler.Lock {

  private static final long NANOS_IN_SECOND = 1000000000L;

  private static final int WAIT_TIME = 1000; // ms

  private final long timeout;

  /** Create a new lock (with given name) */
  public JavaLock(String name) {
    super(name);
    this.timeout = Harness.lockTimeout.getValue() * NANOS_IN_SECOND;
  }

  /** Initialize the timeout timer */
  private long startWait() {
    return System.nanoTime();
  }

  /** Check for timeout
   * @param startWait TODO*/
  private boolean timedOut(long startWait) {
    return (System.nanoTime() - startWait) > timeout;
  }

  /**
   * Try to acquire a lock and wait until acquired.
   */
  @Override
  public void acquire() {
    synchronized(this) {
      long start = startWait();
      while(holder != null && !timedOut(start)) {
        try {
          wait(WAIT_TIME);
        } catch (InterruptedException ie) {
        }
      }
      if (timedOut(start)) {
        String holderName = holder == null ? "<no-one>" : holder.getName();
        Harness.dumpStateAndExit("Timed out waiting for "+name+", held by "+holderName);
      }
      holder = Thread.currentThread();
    }
  }

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
