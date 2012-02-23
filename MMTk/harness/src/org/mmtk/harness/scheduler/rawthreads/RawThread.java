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
package org.mmtk.harness.scheduler.rawthreads;

import org.mmtk.harness.Main;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.scheduler.MMTkThread;

/**
 * An MMTk thread in the RawThreads model
 */
class RawThread extends MMTkThread {
  /**
   * Link back to the thread model, so we can talk to the scheduler etc.
   */
  protected final RawThreadModel model;

  /** True if this thread is exiting */
  private boolean exiting = false;

  /** The ordinal (for a rendezvous) */
  private int ordinal = 0;

  /** Is this thread current ? Used to filter spurious wake-ups */
  private boolean isCurrent = false;

  /** The queue this thread is blocked on (or null if it's running) */
  private ThreadQueue queue;

  public RawThread(RawThreadModel model) {
    this.model = model;
    this.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
      @Override public void uncaughtException(Thread t, Throwable e) {
        e.printStackTrace();
        Main.exitWithFailure();
      }
    });
  }

  protected void end() {
    this.exiting = true;
  }

  /**
   * Make this thread active
   */
  synchronized void resumeThread() {
    assert !exiting;
    Trace.trace(Item.SCHED_DETAIL, "%d: resumeThread", getId());
    isCurrent = true;
    setQueue(null);
    model.setCurrent(this);
    notify();
  }

  /**
   * Put this thread to sleep, wake the scheduler, and then wait until the scheduler
   * selects us to run again.
   */
  synchronized void yieldThread(ThreadQueue queue) {
    isCurrent = false;
    setQueue(queue);
    queue.add(this);
    Trace.trace(Item.SCHED_DETAIL, "%d: yieldThread", getId());
    model.wakeScheduler();
    waitTillCurrent();
    Trace.trace(Item.SCHED_DETAIL, "%d: resuming", getId());
  }

  /**
   * Set the arrival order at a barrier
   * @param ordinal
   */
  void setOrdinal(int ordinal) {
    this.ordinal = ordinal;
  }

  /**
   * @return The order of arrival at the most recent barrier
   */
  int getOrdinal() {
    return ordinal;
  }

  /**
   * Wait until this thread becomes the current thread
   */
  protected synchronized void waitTillCurrent() {
    Trace.trace(Item.SCHED_DETAIL, "%d: waiting for thread wakeup", getId());
    while (!isCurrent) {
      try {
        this.wait();
      } catch (InterruptedException e) {
      }
    }
  }

  private void setQueue(ThreadQueue queue) {
    this.queue = queue;
  }


  @Override
  public String toString() {
    return (isCurrent ? "current " : "queue " + queue.getName()) + (exiting ? "exiting " : "");
  }
}
