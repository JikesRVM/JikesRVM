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

import org.mmtk.harness.MMTkThread;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.scheduler.Policy;
import org.mmtk.harness.scheduler.Scheduler;

/**
 * An MMTk thread in the RawThreads model
 */
class RawThread extends MMTkThread {
  /**
   *
   */
  private final RawThreadModel model;

  /** The command-line configurable yield policy */
  private final Policy yieldPolicy = Scheduler.yieldPolicy(this);

  /** True if this thread is exiting */
  private boolean exiting = false;

  /** The ordinal (for a rendezvous) */
  private int ordinal = 0;

  /** Is this thread current ? Used to filter spurious wade-ups */
  private boolean isCurrent = false;

  public RawThread(RawThreadModel model) {
    this.model = model;
  }

  protected void end() {
    this.exiting = true;
  }

  synchronized void resumeThread() {
    assert !exiting;
    Trace.trace(Item.SCHEDULER, "%d: resumeThread", getId());
    isCurrent = true;
    model.setCurrent(this);
    notify();
  }

  synchronized void yieldThread() {
    isCurrent = false;
    Trace.trace(Item.SCHEDULER, "%d: yieldThread", getId());
    model.wakeScheduler();
    waitTillCurrent();
    Trace.trace(Item.SCHEDULER, "%d: resuming", getId());
  }

  boolean yieldPolicy() {
    return yieldPolicy.yieldNow();
  }

  void setOrdinal(int ordinal) {
    this.ordinal = ordinal;
  }

  int getOrdinal() {
    return ordinal;
  }

  protected synchronized void waitTillCurrent() {
    Trace.trace(Item.SCHEDULER, "%d: waiting for thread wakeup", getId());
    while (!isCurrent) {
      try {
        this.wait();
      } catch (InterruptedException e) {
      }
    }
  }
}
