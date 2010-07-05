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
package org.mmtk.harness;

/**
 * A timeout thread.  Exits the harness if it isn't cancelled in time.
 *
 * Use like this:
 *
 *   TimeoutThread timeout = new TimeoutThread(10);
 *   .. do stuff ..
 *   timeout.cancel();
 */
final class TimeoutThread implements Runnable {
  private static final boolean VERBOSE = false;
  private static final int MILLIS_PER_SECOND = 1000;
  private final long timeout;
  private boolean cancelled = false;
  private volatile boolean started = false;

  /**
   * Create a timeout object and start it running in its own
   * thread.
   * @param seconds Timeout in seconds
   */
  TimeoutThread(int seconds) {
    this.timeout = seconds * MILLIS_PER_SECOND;
    new Thread(this).start();
    synchronized (this) {
      while (!started) {
        try {
          /* Wait for the timeout thread to start running */
          wait();
        } catch (InterruptedException e) {
        }
      }
    }
  }

  /**
   * @see java.lang.Thread#run()
   */
  @Override
  public void run() {
    long startTime = System.currentTimeMillis();
    synchronized (this) {
      /* Inform the caller that the timeout thread has started */
      started = true;
      notify();

      while (!cancelled) {
        try {
          /* Sleep until woken by a cancel or the timer has expired */
          long now = System.currentTimeMillis();
          if (now - startTime >= timeout) {
            System.err.printf("Collection exceeded timeout %dms%n",timeout);
            Main.exitWithFailure();
          }
          long sleepTime = Math.max(1,timeout - (now - startTime));
          if (VERBOSE) {
            System.err.printf("Collection timeout: sleeping for %dms%n",sleepTime);
          }
          wait(sleepTime);
        } catch (InterruptedException e) {
          // Ignore interruptions
        }
      }
    }
  }

  /** Cancel the timeout */
  public void cancel() {
    synchronized (this) {
      if (VERBOSE) {
        System.err.printf("Collection timeout: cancelled%n");
      }
      cancelled = true;
      notify();
    }
  }
}
