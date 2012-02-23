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

package org.mmtk.utility.heap;

import org.mmtk.utility.Log;
import org.mmtk.plan.CollectorContext;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.Monitor;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This context concurrently zeroes a space when triggered.
 */
@Uninterruptible
public class ConcurrentZeroingContext extends CollectorContext {

  private PageResource pr;
  private Monitor lock;
  private volatile int trigger;

  public ConcurrentZeroingContext(PageResource pr) {
    this.pr = pr;
    this.lock = VM.newHeavyCondLock("ConcurrentZeroingLock");
  }

  public void trigger() {
    lock.lock();
    trigger++;
    lock.broadcast();
    lock.unlock();
  }

  public void run(){
    if (Options.verbose.getValue() >= 2) {
      Log.writeln("ZeroingThread running");
    }
    while(true) {
      lock.lock();
      while (trigger == 0) {
        lock.await();
      }
      trigger--;
      lock.unlock();
      pr.concurrentZeroing();
    }
  }
}
