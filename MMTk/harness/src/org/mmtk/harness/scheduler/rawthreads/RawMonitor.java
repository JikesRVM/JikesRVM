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

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.vmmagic.unboxed.harness.Clock;

/**
 * A Monitor object in the Raw Threads model
 */
public class RawMonitor extends org.mmtk.vm.Monitor {

  private final RawLock lock;

  private final String name;

  private final RawThreadModel model;

  private final ThreadQueue waitList;

  RawMonitor(RawThreadModel model, String name) {
    this.lock = new RawLock(model,name+"Lock");
    this.name = name;
    this.model = model;
    this.waitList = new ThreadQueue(name);
  }

  @Override
  public void lock() {
    Clock.stop();
    lock.acquire();
    Clock.start();
  }

  @Override
  public void unlock() {
    Clock.stop();
    lock.release();
    Clock.start();
  }

  @Override
  public void await() {
    Clock.stop();
    unlock();
    Trace.trace(Item.SCHEDULER,"%d: Yielded onto monitor queue %s",Thread.currentThread().getId(),name);
    model.yield(waitList);
    lock();
    Clock.start();
  }

  @Override
  public void broadcast() {
    Clock.stop();
    assert lock.isLocked();
    Trace.trace(Item.SCHEDULER,"%s: Making %d threads on monitor queue %s runnable",
        Thread.currentThread().getName(), waitList.size(), name);
    model.makeRunnable(waitList);
    Clock.tick();
    Clock.start();
  }

}
