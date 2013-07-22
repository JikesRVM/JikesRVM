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

public class JavaMonitor extends org.mmtk.vm.Monitor {

  private static final long NANOS_IN_SECOND = 1000000000L;

  private static final int WAIT_TIME = 1000; // ms

  private final long timeout;

  private final String name;

  private static final boolean TRACE = false;

  private final Object monitor = new Object();

  private boolean isLocked = false;

  /**
   * This counter is used to distinguish wakeups due to broadcast() from
   * other wakeups.  When we broadcast to threads waiting on the condition,
   * we increment the counter.  Each await-ing thread saves a thread-local copy
   * of the value when it started waiting, and when they diverge it wakes up.
   */
  private int counter = Integer.MIN_VALUE;

  private Thread holder = null;

  public JavaMonitor(String name) {
    this.name = name;
    this.timeout = Harness.lockTimeout.getValue() * NANOS_IN_SECOND;
  }

  /**
   * Initialize the timeout timer.
   *
   * Value must be kept in a local variable, not a field.
   */
  private long startWait() {
    return System.nanoTime();
  }

  /**
   * Check for timeout
   * @param startWait Time in nanoseconds that the wait started.
   */
  private boolean timedOut(long startWait) {
    return (System.nanoTime() - startWait) > timeout;
  }

  @Override
  public void await() {
    trace("await", "in");
    synchronized(monitor) {
      int savedCount = counter;
      trace("await", "unlocking");
      unlock();
      trace("await", "waiting");
      long start = startWait();
      while (savedCount == counter && !timedOut(start)) {
        try {
          monitor.wait(WAIT_TIME);
        } catch (InterruptedException e) { }
      }
      if (timedOut(start)) {
        Harness.dumpStateAndExit("Timed out waiting for notification at "+name+", held by");
      }
      trace("await", "waking ...");
      lock();
    }
    trace("await", "out");
  }

  @Override
  public void broadcast() {
    trace("broadcast", "in");
    synchronized(monitor) {
      counter++;
      monitor.notifyAll();
    }
    trace("broadcast", "out");
  }

  @Override
  public void lock() {
    trace("lock", "in");
    synchronized(monitor) {
      long start = startWait();
      while (isLocked && !timedOut(start)) {
        try {
          trace("lock", "wait for "+holder.getName());
          monitor.wait();
        } catch (InterruptedException e) { }
      }
      if (timedOut(start)) {
        String holderName = holder == null ? "<no-one>" : holder.getName();
        Harness.dumpStateAndExit("Timed out waiting for "+name+", held by "+holderName);
      }
      isLocked = true;
      holder = Thread.currentThread();
    }
    trace("lock", "out");
  }

  @Override
  public void unlock() {
    trace("unlock", "in");
    synchronized(monitor) {
      assert isLocked;
      isLocked = false;
      holder = null;
      monitor.notifyAll();
    }
    trace("unlock", "out");
  }

  private void trace(String method, String place) {
    if (TRACE) {
      System.out.printf("%s: %s(%s) : %s%n", Thread.currentThread().getName(), method, name, place);
      System.out.flush();
    }
  }

}
