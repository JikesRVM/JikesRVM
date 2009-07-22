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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.mmtk.harness.Collector;
import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.scheduler.MMTkThread;
import org.mmtk.harness.scheduler.Schedulable;
import org.mmtk.harness.scheduler.ThreadModel;
import static org.mmtk.harness.scheduler.ThreadModel.State.*;
import org.mmtk.utility.Log;

public final class JavaThreadModel extends ThreadModel {
  static {
    //Trace.enable(Item.SCHEDULER);
  }

  /**
   * Collector threads scheduled through #scheduleCollector(Schedulable)
   */
  Set<CollectorThread> collectorThreads =
    Collections.synchronizedSet(new HashSet<CollectorThread>());

  /**
   * Mutator threads scheduled through scheduleMutator(Schedulable)
   */
  Set<MutatorThread> mutatorThreads =
    Collections.synchronizedSet(new HashSet<MutatorThread>());

  /**
   * Create a new mutator thread
   */
  @Override
  public void scheduleMutator(Schedulable code) {
    Trace.trace(Item.SCHEDULER, "Scheduling new mutator");
    MutatorThread t = new MutatorThread(this,code);
    mutatorThreads.add(t);
    t.start();
  }

  /**
   * Create a new collector thread
   */
  @Override
  public void scheduleCollector() {
    Trace.trace(Item.SCHEDULER, "Scheduling new collector");
    CollectorThread t = new CollectorThread();
    collectorThreads.add(t);
    t.start();
  }

  /**
   * Create a new collector thread with a specific Schedulable
   *
   * Used for scheduling unit tests in collector context
   */
  @Override
  public Thread scheduleCollector(Schedulable code) {
    Trace.trace(Item.SCHEDULER, "Scheduling new collector");
    CollectorContextThread t = new CollectorContextThread(this,code);
    collectorThreads.add(t);
    t.start();
    return t;
  }

  private MMTkThread currentMMTkThread() {
    return ((MMTkThread)Thread.currentThread());
  }

  @Override
  public void yield() {
    if (isRunning()) {
      if (currentMMTkThread().yieldPolicy()) {
        Thread.yield();
      }
    }
  }

  @Override
  public Log currentLog() {
    return currentMMTkThread().getLog();
  }

  @Override
  public Mutator currentMutator() {
    assert Thread.currentThread() instanceof MutatorThread  : "Current thread is not a Mutator";
    return ((MutatorThread)Thread.currentThread()).env;
  }

  /**
   * Perform the delicate operation of joining the pool of active mutators.
   *
   * If there isn't currently a GC in progress (!allWaitingForGC()), increment
   * the active mutator count and return.  If a GC has been initiated, we will
   * join it at the next GC-safe point.
   *
   * Otherwise, we 'quietly' join the active GC, by incrementing the count of
   * waiting mutators, and calling waitForGC(false) (there has already been a 'last'
   * mutator).  Once the GC has completed, we remove ourselves from the GC,
   * and increment the activeMutators.
   */
  void joinMutatorPool() {
    synchronized (count) {
      if (!allWaitingForGC()) {
        incActiveMutators();
        return;
      }
      mutatorsWaitingForGC++;
      incActiveMutators();
    }
    waitForGC(false);
    synchronized (count) {
      mutatorsWaitingForGC--;
    }
  }

  private void incActiveMutators() {
    activeMutators++;
  }

  private void decActiveMutators() {
    activeMutators--;
    if (activeMutators == 0)
      count.notify();
  }

  /**
   * Perform the delicate operation of leaving the mutator pool.
   *
   * If there's a GC scheduled, and we are the last thread to join,
   * join the GC (because we are required to trigger it) and then exit.
   * Otherwise, just decrement the mutator count and leave.
   */
  void leaveMutatorPool() {
    synchronized (count) {
      boolean lastToGC = (mutatorsWaitingForGC == (activeMutators - 1));
      if (!lastToGC) {
        decActiveMutators();
        return;
      }
      mutatorsWaitingForGC++;
    }
    waitForGC(true);
    synchronized (count) {
        mutatorsWaitingForGC--;
        decActiveMutators();
    }

  }

  /** Synchronisation object used for GC triggering */
  private final Object trigger = new Object();

  /**
   * Wait for a GC to complete
   */
  private void waitForGC(boolean last) {
    Trace.trace(Item.SCHEDULER, "%d waitForGC in", Thread.currentThread().getId());
    synchronized (trigger) {
      if (last) {
        setState(GC);
        trigger.notifyAll();
      }
      while (inGC > 0) {
        try {
          trigger.wait();
        } catch (InterruptedException ie) {}
      }
    }
    Trace.trace(Item.SCHEDULER, "%d waitForGC out", Thread.currentThread().getId());
  }

  @Override
  public void waitForGC() {
    boolean allWaiting;
    synchronized (count) {
      mutatorsWaitingForGC++;
      allWaiting = allWaitingForGC();
    }
    waitForGC(allWaiting);
    synchronized (count) {
        mutatorsWaitingForGC--;
    }
  }

  /**
   * Check whether all mutators are in GC - MUST HOLD THE 'count' MONITOR
   * @return true if all mutators are waiting for GC
   */
  private boolean allWaitingForGC() {
    return (activeMutators > 0) && (mutatorsWaitingForGC == activeMutators);
  }

  /**
   * Trigger a collection for the given reason
   */
  @Override
  public void triggerGC(int why) {
    synchronized (trigger) {
      triggerReason = why;
      inGC = collectorThreads.size();
      setState(BEGIN_GC);
      trigger.notifyAll();
    }
  }

  /**
   * A GC thread has completed its GC work.
   */
  @Override
  public void exitGC() {
    synchronized (trigger) {
      inGC--;
      if (inGC == 0) {
        setState(MUTATOR);
        trigger.notifyAll();
      }
    }
  }

  @Override
  public void waitForGCStart() {
    synchronized(trigger) {
      while(inGC == 0 || !isState(GC)) {
        try {
          trigger.wait();
        } catch (InterruptedException ie) {}
      }
      Trace.trace(Item.SCHEDULER, "GC has started");
    }
  }

  /**
   * The number of mutators waiting for a collection to proceed.
   */
  protected int mutatorsWaitingForGC;

  /** The number of collectors executing GC */
  protected int inGC;

  /**
   * The number of mutators currently executing in the system.
   */
  protected int activeMutators;

  /**
   * Object used for synchronizing the number of mutators waiting for a gc.
   */
  public static final Object count = new Object();

  /** Thread access to current collector */
  public static final ThreadLocal<Collector> collectorThreadLocal = new ThreadLocal<Collector>();

  @Override
  public int rendezvous(int where) {
    return Rendezvous.rendezvous(where);
  }

  @Override
  public Collector currentCollector() {
    return collectorThreadLocal.get();
  }

  @Override
  public JavaLock newLock(String name) {
    return new org.mmtk.harness.scheduler.javathreads.JavaLock(name);
  }

  /**
   * Wait for the mutator threads to exit.
   * @see org.mmtk.harness.scheduler.ThreadModel#schedule()
   */
  @Override
  public void schedule() {
    startRunning();
    /* Wait for the mutators */
    while (activeMutators > 0) {
      synchronized (count) {
        try {
          count.wait();
        } catch (InterruptedException e) {
        }
        System.err.println("Active mutators = "+activeMutators);
      }
    }
  }

  @Override
  public void scheduleGcThreads() {
    synchronized (trigger) {
      startRunning();
      inGC = collectorThreads.size();
      setState(GC);
      trigger.notifyAll();
      while (!isState(MUTATOR)) {
        try {
          trigger.wait();
        } catch (InterruptedException e) {
        }
      }
      stopRunning();
    }
  }

  @Override
  public boolean noThreadsInGC() {
    return inGC == 0;
  }

  @Override
  public boolean gcTriggered() {
    return inGC > 0;
  }
}
