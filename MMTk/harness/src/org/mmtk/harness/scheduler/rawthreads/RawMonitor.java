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

/**
 * A Monitor object in the Raw Threads model
 */
public class RawMonitor extends org.mmtk.vm.Monitor {

  private final String name;

  private boolean isLocked = false;

  private final RawThreadModel model;

  private final ThreadQueue waitList;

  RawMonitor(RawThreadModel model, String name) {
    this.name = name;
    this.model = model;
    this.waitList = new ThreadQueue(name);
  }

  @Override
  public void lock() {
    while (isLocked) {
      Trace.trace(Item.SCHEDULER,"%d: Yielded onto monitor queue %s",Thread.currentThread().getId(),name);
      model.yield(waitList);
    }
    isLocked = true;
  }

  @Override
  public void unlock() {
    isLocked = false;
    model.makeRunnable(waitList);
  }

  @Override
  public void await() {
    unlock();
    Trace.trace(Item.SCHEDULER,"%d: Yielded onto monitor queue %s",Thread.currentThread().getId(),name);
    model.yield(waitList);
    lock();
  }

  @Override
  public void broadcast() {
    model.makeRunnable(waitList);
  }
}
