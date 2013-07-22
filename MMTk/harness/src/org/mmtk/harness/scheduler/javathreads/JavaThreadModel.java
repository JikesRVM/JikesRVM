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

import static org.mmtk.harness.scheduler.ThreadModel.State.BLOCKED;
import static org.mmtk.harness.scheduler.ThreadModel.State.BLOCKING;
import static org.mmtk.harness.scheduler.ThreadModel.State.MUTATOR;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.scheduler.MMTkThread;
import org.mmtk.harness.scheduler.Schedulable;
import org.mmtk.harness.scheduler.ThreadModel;
import org.mmtk.plan.CollectorContext;
import org.mmtk.utility.Log;

public final class JavaThreadModel extends ThreadModel {
  static {
    //Trace.enable(Item.SCHEDULER);
  }

  /**
   * Collector threads scheduled through #scheduleCollector(Schedulable)
   */
  private final Set<CollectorThread> collectorThreads =
      Collections.newSetFromMap(new ConcurrentHashMap<CollectorThread, Boolean>());

  /**
   * Mutator threads scheduled through scheduleMutator(Schedulable)
   */
  private final Set<MutatorThread> mutatorThreads =
      Collections.newSetFromMap(new ConcurrentHashMap<MutatorThread, Boolean>());

  @Override
  public void scheduleMutator(Schedulable code) {
    Trace.trace(Item.SCHEDULER, "Scheduling new mutator");
    MutatorThread t = new MutatorThread(this,code);
    mutatorThreads.add(t);
    if (isRunning())
      t.start();
  }

  @Override
  protected void scheduleCollector(CollectorContext context) {
    Trace.trace(Item.SCHEDULER, "Scheduling new collector");
    CollectorThread t = new CollectorThread(this,context);
    collectorThreads.add(t);
    context.initCollector(collectorThreads.size());
    t.start();
  }

  /**
   * Create a new non-daemon collector thread
   *
   * Used for scheduling unit tests in collector context
   */
  @Override
  public Thread scheduleCollectorContext(CollectorContext context) {
    Trace.trace(Item.SCHEDULER, "Scheduling new collector");
    CollectorThread t = new CollectorThread(this,context,false);
    collectorThreads.add(t);
    context.initCollector(collectorThreads.size());
    return t;
  }

  void removeCollector(CollectorThread c) {
    collectorThreads.remove(c);
    Trace.trace(Item.SCHEDULER, "removeCollector: remaining collectors = %d",collectorThreads.size());
    if (collectorThreads.size() == 0) {
      /* This only happens when running unit tests */
      resumeAllMutators();
    }
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
   * <p>
   * If there isn't currently a GC in progress ({@code !allWaitingForGC()}), increment
   * the active mutator count and return.  If a GC has been initiated, we will
   * join it at the next GC-safe point.
   * <p>
   * Otherwise, we 'quietly' join the active GC, by incrementing the count of
   * waiting mutators, and calling {@code waitForGC(false)} (there has already been a 'last'
   * mutator).  Once the GC has completed, we remove ourselves from the GC,
   * and increment the activeMutators.
   */
  void joinMutatorPool() {
    synchronized (count) {
      if (!allWaitingForGC()) {
        incActiveMutators();
        return;
      }
      mutatorsBlocked++;
      incActiveMutators();
    }
    waitForGC(false,false);
    synchronized (count) {
      mutatorsBlocked--;
    }
  }

  private void incActiveMutators() {
    activeMutators++;
    count.notify();
  }

  private void decActiveMutators() {
    activeMutators--;
    if (activeMutators == 0)
      count.notify();
  }

  /**
   * Perform the delicate operation of leaving the mutator pool.
   * <p>
   * If there's a GC scheduled, and we are the last thread to join,
   * join the GC (because we are required to trigger it) and then exit.
   * Otherwise, just decrement the mutator count and leave.
   */
  void leaveMutatorPool(MutatorThread m) {
    Trace.trace(Item.SCHEDULER, "%d Leaving mutator pool", Thread.currentThread().getId());
    synchronized (count) {
      boolean lastToGC = (mutatorsBlocked == (activeMutators - 1));
      if (activeMutators == 1 || !lastToGC) {
        decActiveMutators();
        Trace.trace(Item.SCHEDULER, "%d Leaving mutator pool: Removing self from thread pool", Thread.currentThread().getId());
        mutatorThreads.remove(m);
        return;
      }
      mutatorsBlocked++;
    }
    Trace.trace(Item.SCHEDULER, "%d Leaving mutator pool: checking for GC", Thread.currentThread().getId());
    waitForGC(true,false);
    synchronized (count) {
        mutatorsBlocked--;
        decActiveMutators();
    }
    Trace.trace(Item.SCHEDULER, "%d Leaving mutator pool: Removing self from thread pool", Thread.currentThread().getId());
    mutatorThreads.remove(m);
    Trace.trace(Item.SCHEDULER, "%d Leaving mutator pool: done", Thread.currentThread().getId());
  }

  /** Synchronisation object used for GC triggering */
  private final Object trigger = new Object();

  /**
   * Wait for a GC to complete
   * @param last True if this thread is the last to join
   */
  private void waitForGC(boolean last, boolean blockAnyway) {
    Trace.trace(Item.SCHEDULER, "%d waitForGC in, state=%s, mutatorsBlocked=%d",
        Thread.currentThread().getId(),getState(),mutatorsBlocked);
    synchronized (trigger) {
      /* Catch mutators who are waiting for a GC that hasn't necessarily started yet */
      while (blockAnyway && isState(MUTATOR)) {
        Trace.trace(Item.SCHEDULER, "%d current state is MUTATOR", Thread.currentThread().getId());
        try {
          trigger.wait();
        } catch (InterruptedException ie) {}
      }

      /* Change state from BLOCKING to BLOCKED when the last thread arrives */
      if (last && isState(BLOCKING)) {
        setState(BLOCKED);
        trigger.notifyAll();
      }

      /* Wait to be resumed */
      while (isState(BLOCKED) || isState(BLOCKING)) {
        try {
          trigger.wait();
        } catch (InterruptedException ie) {}
      }
    }
    Trace.trace(Item.SCHEDULER, "%d waitForGC out", Thread.currentThread().getId());
  }

  /**
   * Mutator thread calls this if it wants to block until the next GC is complete
   */
  @Override
  public void waitForGC() {
    boolean allWaiting;
    synchronized (count) {
      mutatorsBlocked++;
      allWaiting = allWaitingForGC();
    }
    waitForGC(allWaiting,true);
    synchronized (count) {
      mutatorsBlocked--;
    }
  }

  /**
   * Check whether all mutators are in GC - MUST HOLD THE 'count' MONITOR
   * @return true if all mutators are waiting for GC
   */
  private boolean allWaitingForGC() {
    return (activeMutators > 0) && (mutatorsBlocked == activeMutators);
  }

  /**
   * Trigger a collection for the given reason
   */
  @Override
  public void stopAllMutators() {
    Trace.trace(Item.SCHEDULER, "stopAllMutators");
    synchronized (trigger) {
      setState(BLOCKING);
      trigger.notifyAll();
    }
    waitForGCStart();
    Trace.trace(Item.SCHEDULER, "stopAllMutators - done");
  }

  /**
   * Resume all the blocked mutator threads
   */
  @Override
  public void resumeAllMutators() {
    synchronized (trigger) {
      setState(MUTATOR);
      trigger.notifyAll();
    }
  }

  protected void waitForGCStart() {
    synchronized(trigger) {
      while(!isState(BLOCKED)) {
        try {
          trigger.wait();
        } catch (InterruptedException ie) {}
      }
      Trace.trace(Item.SCHEDULER, "GC has started - returning to caller");
    }
  }

  /** Object used for synchronizing mutatorsWaitingForGC and activeMutators */
  private static final Object count = new Object();

  /** The number of mutators waiting for a collection to proceed. */
  protected int mutatorsBlocked;

  /** The number of mutators currently executing in the system. */
  protected int activeMutators;

  /** Thread access to current collector */
  private static final ThreadLocal<CollectorContext> collectorThreadLocal = new ThreadLocal<CollectorContext>();

  @Override
  public int mutatorRendezvous(String where, int expected) {
    synchronized (count) {
      mutatorsBlocked++;
    }
    int ordinal = Rendezvous.rendezvous(where,expected);
    synchronized (count) {
      mutatorsBlocked--;
    }
    return ordinal;
  }

  @Override
  public CollectorContext currentCollector() {
    return collectorThreadLocal.get();
  }

  void setCurrentCollector(CollectorContext c) {
    collectorThreadLocal.set(c);
  }

  @Override
  public JavaLock newLock(String name) {
    return new org.mmtk.harness.scheduler.javathreads.JavaLock(name);
  }

  /**
   * Wait for the mutator threads to exit.
   */
  @Override
  public void schedule() {
    startRunning();
    /* Start the mutator threads */
    for (Thread t : mutatorThreads) {
      t.start();
    }
    /* Wait for the mutators to start */
    while (mutatorThreads.size() > activeMutators) {
      synchronized (count) {
        try {
          count.wait();
        } catch (InterruptedException e) {
        }
        Trace.trace(Item.SCHEDULER,"Active mutators = "+activeMutators+
            ", mutatorThreads = "+mutatorThreads.size());
      }
    }
    /* Wait for the mutators to exit */
    while (activeMutators > 0) {
      synchronized (count) {
        try {
          count.wait();
        } catch (InterruptedException e) {
        }
        Trace.trace(Item.SCHEDULER,"Active mutators = "+activeMutators);
      }
    }
  }

  @Override
  public void scheduleGcThreads() {
    /* Start the mutator threads */
    for (Thread t : collectorThreads) {
      t.start();
    }
    synchronized (trigger) {
      startRunning();
      setState(BLOCKED);
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
  public boolean gcTriggered() {
    return !isState(MUTATOR);
  }

  @Override
  public boolean isMutator() {
    return Thread.currentThread() instanceof MutatorThread;
  }

  @Override
  public boolean isCollector() {
    return Thread.currentThread() instanceof CollectorThread;
  }

  @Override
  protected JavaMonitor newMonitor(String name) {
    return new JavaMonitor(name);
  }
}
