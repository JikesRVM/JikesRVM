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
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.harness.Clock;

/**
 * Simple lock.
 */
@Uninterruptible
public class RawLock extends org.mmtk.harness.scheduler.Lock {

  private boolean isHeld = false;
  private final RawThreadModel model;

  private final ThreadQueue waitList = new ThreadQueue(name);

  /** Create a new lock (with given name) */
  public RawLock(RawThreadModel model, String name) {
    super(name);
    this.model = model;
  }

  /**
   * Try to acquire a lock and wait until acquired.
   */
  @Override
  public void acquire() {
    while (isHeld) {
      Trace.trace(Item.SCHEDULER,"Yielded onto lock queue ");
      model.yield(waitList);
    }
    isHeld = true;
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
    isHeld = false;
    Clock.stop();
    model.makeRunnable(waitList);
    Clock.start();
  }

  int threadsWaiting() {
    return waitList.size();
  }

  boolean isLocked() {
    return isHeld;
  }
}
