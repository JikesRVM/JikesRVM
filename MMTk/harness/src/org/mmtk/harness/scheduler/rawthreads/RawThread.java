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
package org.mmtk.harness.scheduler.rawthreads;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.scheduler.Policy;
import org.mmtk.harness.scheduler.Scheduler;
import org.mmtk.utility.Log;

/**
 * An MMTk thread in the RawThreads model
 */
class RawThread extends Thread {
  /**
   *
   */
  private final RawThreads model;

  /** The command-line configurable yield policy */
  private final Policy yieldPolicy = Scheduler.yieldPolicy(this);

  /** The per-thread Log instance */
  protected final Log log = new Log();

  /** True if this thread is exiting */
  private boolean exiting = false;

  /** The ordinal (for a rendezvous) */
  private int ordinal = 0;

  /** Is this thread current ? Used to filter spurious wade-ups */
  private boolean isCurrent = false;

  public RawThread(RawThreads model) {
    this.model = model;
  }

  public RawThread(RawThreads myThreads, Runnable target) {
    super(target);
    model = myThreads;
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
    while (!isCurrent) {
      try {
        this.wait();
      } catch (InterruptedException e) {
      }
    }
  }
}
