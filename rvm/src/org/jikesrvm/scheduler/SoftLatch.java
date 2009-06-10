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
package org.jikesrvm.scheduler;

/**
 * An implementation of a latch using monitors.
 * This essentially gives you park/unpark functionality.  It can also
 * be used like the Win32-style AutoResetEvent or ManualResetEvent.
 * <p>
 * Park/unpark example: use open() to unpark and waitAndClose() to park.
 * <p>
 * AutoResetEvent example: use open() to set, close() to reset, and
 * waitAndClose() to wait.
 * <p>
 * ManualResetEvent example: use open() to set, close() to reset, and
 * wait() to wait.
 * <p>
 * Note: <b><i>never</i></b> synchronize on instances of this class.
 */
public class SoftLatch {

  private boolean open;

  /** Create a new latch, with the given open/closed state. */
  public SoftLatch(boolean open) {
    this.open = open;
  }

  /**
   * Open the latch and let all of the thread(s) waiting on it through.
   * But - if any of the threads is using waitAndClose(), then as soon
   * as that thread awakes further threads will be blocked.
   */
  public synchronized void open() {
    open=true;
    notifyAll();
  }

  /**
   * Close the latch, causing future calls to wait() or waitAndClose()
   * to block.
   */
  public synchronized void close() {
    open=false;
  }

  /**
   * Wait for the latch to become open.  If it is already open, don't
   * wait at all.
   */
  public synchronized void await() {
    while (!open) {
      try {
        wait();
      } catch (InterruptedException e) {
        throw new Error(e);
      }
    }
  }

  /**
   * Wait for the latch to become open, and then close it and return.
   * If the latch is already open, don't wait at all, just close it
   * immediately and return.
   */
  public synchronized void waitAndClose() {
    while (!open) {
      try {
        wait();
      } catch (InterruptedException e) {
        throw new Error(e);
      }
    }
    open=false;
  }
}
