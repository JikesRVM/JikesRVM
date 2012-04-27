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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.scheduler.MMTkThread;
import org.mmtk.harness.scheduler.Schedulable;
import org.mmtk.harness.scheduler.ThreadModel;

import static org.mmtk.harness.scheduler.ThreadModel.State.*;

import org.mmtk.plan.CollectorContext;
import org.mmtk.utility.Log;
import org.mmtk.vm.Monitor;

/**
 * The deterministic thread scheduler.  Java threads are used for all
 * program threads of execution, but only one thread is permitted
 * to execute at a time, the scheduler selecting the next thread at
 * each context switch.
 */
public final class RawThreadModel extends ThreadModel {

  private static boolean unitTest = false;

  static {
    //Trace.enable(Item.SCHEDULER);
  }

  /** The global scheduler thread */
  private final Thread scheduler = Thread.currentThread();

  /**
   * The 'scheduler woken' flag.
   *
   * Unchecked invariant: at most one of schedulerIsAwake and (RawThread)isCurrent
   * are true at any given time.
   */
  private boolean schedulerIsAwake = true;

  private RawThread current = null;

  /**
   * The collection of collector threads
   */
  final ThreadQueue collectors = new ThreadQueue("collectors");

  /**
   * The collection of mutator threads
   */
  private final ThreadQueue mutators = new ThreadQueue("mutators");

  /** Unique mutator thread number - used to name the mutator threads */
  private volatile int mutatorId = 0;

  int nextMutatorId() {
    return ++mutatorId;
  }

  /**
   * Counter of mutators currently blocked.  Used to determine when a 'stopAllMutators'
   * event has taken effect.
   */
  private volatile AtomicInteger mutatorsBlocked = new AtomicInteger();

  /*
   * Scheduling queues.  A thread may be on exactly one of these queues,
   * or on a lock wait queue, or current.
   */

  /**
   * Queue containing runnable threads
   */
  private final ThreadQueue runQueue = new ThreadQueue("runQueue");

  /**
   * Queue containing mutator threads blocked for a stop-the-world GC phase
   */
  private final ThreadQueue gcWaitQueue = new ThreadQueue("gcWaitQueue",mutatorsBlocked);

  private void dumpThreads() {
    System.err.println("-- Mutator threads --");
    for (RawThread thread : mutators) {
      System.err.println(thread.getId()+" : "+thread.toString());
    }
    System.err.println("-- Collector threads --");
    for (RawThread thread : collectors) {
      System.err.println(thread.getId()+" : "+thread.toString());
    }
  }

  /**
   * Remove a mutator from the pool when a mutator thread exits
   * @param m The mutator thread
   */
  void removeMutator(MutatorThread m) {
    synchronized(scheduler) {
      mutators.remove(m);
      Trace.trace(Item.SCHEDULER, "%d: mutator removed, %d mutators remaining",m.getId(),mutators.size());
      wakeScheduler();
    }
  }

  /**
   * Remove a collector from the pool when a collector thread exits.
   * Only used for unit testing, since collectors are usually permanent
   * @param c The collector thread
   */
  void removeCollector(CollectorThread c) {
    synchronized(scheduler) {
      collectors.remove(c);
      wakeScheduler();
    }
  }

  /**
   * {@code current} becomes the current thread.
   * @param current The new current thread
   */
  void setCurrent(RawThread current) {
    this.current = current;
  }

  /**
   * @return the current thread as an MMTkThread
   */
  static MMTkThread getCurrent() {
    return (MMTkThread)Thread.currentThread();
  }

  /**
   * @see org.mmtk.harness.scheduler.ThreadModel#currentLog()
   */
  @Override
  public Log currentLog() {
    return current.getLog();
  }

  /**
   * @see org.mmtk.harness.scheduler.ThreadModel#currentMutator()
   */
  @Override
  public Mutator currentMutator() {
    return ((MutatorThread)current).env;
  }

  /**
   * @see org.mmtk.harness.scheduler.ThreadModel#currentCollector()
   */
  @Override
  public CollectorContext currentCollector() {
    return ((CollectorThread)current).context;
  }

  /**
   * Queues for mutator rendezvous events
   */
  private final Map<String,ThreadQueue> rendezvousQueues = new HashMap<String,ThreadQueue>();

  /**
   * @see org.mmtk.harness.scheduler.ThreadModel#mutatorRendezvous(java.lang.String, int)
   */
  @Override
  public int mutatorRendezvous(String where, int expected) {
    String barrierName = "Barrier-"+where;
    Trace.trace(Item.SCHEDULER, "%s: rendezvous(%s)", current.getId(), barrierName);
    ThreadQueue queue = rendezvousQueues.get(barrierName);
    if (queue == null) {
      queue = new ThreadQueue(where,mutatorsBlocked);
      rendezvousQueues.put(barrierName, queue);
    }
    current.setOrdinal(queue.size()+1);
    if (queue.size() == expected-1) {
      resume(queue);
      rendezvousQueues.put(barrierName, null);
    } else {
      suspend(queue);
    }
    Trace.trace(Item.SCHEDULER, "%d: rendezvous(%s) complete: ordinal = %d", current.getId(), barrierName,current.getOrdinal());
    return current.getOrdinal();
  }

  @Override
  public void scheduleCollector(CollectorContext context) {
    scheduleCollectorContext(context);
  }

  /**
   * Create a collector thread for specific code (eg unit test)
   */
  @Override
  public Thread scheduleCollectorContext(CollectorContext method) {
    RawThread c = new CollectorThread(this,method);
    collectors.add(c);
    method.initCollector(collectors.size());
    Trace.trace(Item.SCHEDULER, "%d: creating new collector, id=%d",
        Thread.currentThread().getId(), c.getId());
    c.start();
    return c;
  }

  @Override
  public void scheduleMutator(Schedulable method) {
    MutatorThread m = new MutatorThread(method, RawThreadModel.this);
    synchronized(scheduler) {
      Trace.trace(Item.SCHEDULER, "%d: creating new mutator, id=%d", Thread.currentThread().getId(), m.getId());
      m.setName("Mutator-"+mutators.size());
      mutators.add(m);
      if (!isState(MUTATOR)) {
        Trace.trace(Item.SCHEDULER, "%d: Adding to GC wait queue", Thread.currentThread().getId());
        m.yieldThread(gcWaitQueue);
      } else {
        Trace.trace(Item.SCHEDULER, "%d: Adding to run queue", Thread.currentThread().getId());
        runQueue.add(m);
        assert runQueue.size() <= Math.max(mutators.size(),collectors.size());
      }
      m.start();
      Trace.trace(Item.SCHEDULER, "%d: mutator started", Thread.currentThread().getId());
    }
  }

  @Override
  protected void initCollectors() {
    Trace.trace(Item.SCHEDULER, "%d: Initializing collectors", Thread.currentThread().getId());
    assert Thread.currentThread() == scheduler;
    makeRunnable(collectors,false);
    schedule();
    Trace.trace(Item.SCHEDULER, "%d: Collector threads initialized", Thread.currentThread().getId());
  }

  /**
   * Mutator waits for a GC
   */
  @Override
  public void waitForGC() {
    Trace.trace(Item.SCHEDULER, "%d: Yielding to GC wait queue", Thread.currentThread().getId());
    suspend(gcWaitQueue);
  }

  /**
   * Yield (to the runQueue) if the scheduler policy requires that we do so.
   * @see org.mmtk.harness.scheduler.ThreadModel#yield()
   */
  @Override
  public void yield() {
    if (isRunning()) {
      if (current.yieldPolicy()) {
        Trace.trace(Item.YIELD, "%d: Yieldpoint", Thread.currentThread().getId());
        yield(runQueue);
      } else {
        Trace.trace(Item.YIELD, "%d: Yieldpoint - not taken", Thread.currentThread().getId());
      }
    }
  }

  /**
   * Yield, placing the current thread on a specific queue
   * @param queue
   */
  void yield(ThreadQueue queue) {
    assert current != null;
    Trace.trace(Item.SCHED_DETAIL,"%d: Yielded onto queue %s with %d members",Thread.currentThread().getId(),queue.getName(),queue.size());
    assert queue.size() <= mutators.size() + collectors.size() :
      "yielded to queue size "+queue.size()+" where there are "+mutators.size()+" m and "+collectors.size()+"c";
    current.yieldThread(queue);
  }

  /**
   * Yield to a specified queue, in a manner that we consider being blocked.
   * Mutator thread only.
   * @param queue Queue to wait on
   */
  void suspend(ThreadQueue queue) {
    assert isMutator() : "Collector threads may not yield to a mutator wait queue";
    yield(queue);
    assert mutatorsBlocked.get() <= mutators.size() :
      mutatorsBlocked.get() + " mutators are blocked but only " + mutators.size() +" exist";
  }

  @Override
  public boolean gcTriggered() {
    return isState(BLOCKING) || isState(BLOCKED);
  }

  @Override
  protected void resumeAllMutators() {
    assert isState(BLOCKED) : "Mutators must be blocked before they can be resumed";
    assert mutatorsBlocked.get() <= mutators.size() :
      mutatorsBlocked.get() + " mutators are blocked but only " + mutators.size() +" exist";
    resume(gcWaitQueue);
    setState(MUTATOR);
  }

  @Override
  protected void stopAllMutators() {
    assert isCollector() : "Collector threads may not yield to a mutator wait queue";
    setState(BLOCKING);
    while (isState(BLOCKING)) {
      yield();
    }
  }

  @Override
  public boolean isMutator() {
    return Thread.currentThread() instanceof MutatorThread;
  }

  @Override
  public boolean isCollector() {
    return Thread.currentThread() instanceof CollectorThread;
  }

  /**
   * Thread-model specific lock factory
   */
  @Override
  public org.mmtk.harness.scheduler.Lock newLock(String name) {
    Trace.trace(Item.SCHEDULER, "Creating new lock %s",name);
    return new RawLock(this,name);
  }

  @Override
  protected Monitor newMonitor(String name) {
    Trace.trace(Item.SCHEDULER, "Creating new monitor %s",name);
    return new RawMonitor(this,name);
  }

  /**
   * Schedule gc-context unit tests
   */
  @Override
  public void scheduleGcThreads() {
    /* Advance the GC threads to the collector wait queue */
    initCollectors();

    /* Transition to GC state */
    setState(BLOCKED);

    /* And run to completion */
    schedule();
  }

  /**
   * The actual scheduler
   */
  @Override
  public void schedule() {
    int blockingCount = 0;
    startRunning();
    assert Thread.currentThread() == scheduler;
    Trace.trace(Item.SCHEDULER, "%d: scheduler begin", scheduler.getId());

    /**
     * The scheduler runs until there are no threads to schedule.
     */
    while (!runQueue.isEmpty()) {
      synchronized(scheduler) {
        assert runQueue.size() <= mutators.size() + collectors.size() :
          "Run queue is unreasonably long, queue="+runQueue.size()+
          ", m="+mutators.size()+", c="+collectors.size();
        if (runQueue.size() > 0) {
          runQueue.remove().resumeThread();
          Trace.trace(Item.SCHED_DETAIL, "%d: scheduler sleeping, runqueue=%d", scheduler.getId(), runQueue.size());
          schedWait();
          Trace.trace(Item.SCHED_DETAIL, "%d: scheduler resuming, state %s, runqueue=%d", scheduler.getId(),getState(), runQueue.size());
        }
        assert mutatorsBlocked.get() <= mutators.size() :
          mutatorsBlocked.get() + " mutators are blocked but only " + mutators.size() +" exist";
        /*
         * Apply state-transition rules and enforce invariants
         */
        switch (getState()) {
          case MUTATOR:
            /* If there are available mutators, at least one of them must be runnable */
            assert mutators.isEmpty() || !runQueue.isEmpty() :
              "mutators.isEmpty()="+mutators.isEmpty()+", runQueue.isEmpty()="+runQueue.isEmpty();
            break;
          case BLOCKING:
            blockingCount++;
            Trace.trace(Item.SCHEDULER, "mutators blocked %d/%d", mutatorsBlocked.get(), mutators.size());
            if (mutatorsBlocked.get() == mutators.size()) {
              setState(BLOCKED);
              blockingCount = 0;
            }
            if (blockingCount > 1000000) {
              System.err.println("Block pending for an unreasonable amount of time");
              dumpThreads();
              throw new AssertionError();
            }
            break;
          case BLOCKED:
            assert ! unitTest && (mutators.size() == 0 || !runQueue.isEmpty()) :
              "Runqueue cannot be empty while mutators are blocked";
            break;
        }
      }
    }
    Trace.trace(Item.SCHEDULER, "%d: scheduler end", scheduler.getId());
    stopRunning();
  }

  /**
   * Make a collection of threads runnable, optionally clearing the
   * source collection.
   *
   * @param threads
   * @param clear
   */
  void makeRunnable(ThreadQueue threads, boolean clear) {
    runQueue.addAll(threads);
    assert runQueue.size() <= mutators.size() + collectors.size();
    if (clear) {
      threads.clear();
    }
  }

  /**
   * Make a collection of threads runnable, clearing the source collection.
   *
   * @param threads
   */
  void makeRunnable(ThreadQueue threads) {
    makeRunnable(threads,true);
  }

  /**
   * Make a collection of threads runnable, clearing the source collection.
   * Specifically, transitioning mutator threads from a blocked state to
   * a running state, and updating the appropriate housekeeping.
   *
   * @param threads Queue to resume
   */
  void resume(ThreadQueue threads) {
    assert mutatorsBlocked.get() >= 0 : mutatorsBlocked.get() + " mutators are blocked";
    makeRunnable(threads);
  }

  void wakeScheduler() {
    Trace.trace(Item.SCHED_DETAIL, "%d: waking scheduler", Thread.currentThread().getId());
    synchronized(scheduler) {
      schedulerIsAwake = true;
      scheduler.notify();
    }
  }

  /**
   * Wait for a scheduler wake-up.  The caller *must* hold the scheduler monitor.
   */
  private void schedWait() {
    schedulerIsAwake = false;
    assert Thread.currentThread() == scheduler;
    while (!schedulerIsAwake) {
      try {
        scheduler.wait(1);
      } catch (InterruptedException e) {
      }
    }
  }
}

