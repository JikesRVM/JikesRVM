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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.mmtk.harness.Collector;
import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.scheduler.MMTkThread;
import org.mmtk.harness.scheduler.Schedulable;
import org.mmtk.harness.scheduler.ThreadModel;
import static org.mmtk.harness.scheduler.ThreadModel.State.*;
import org.mmtk.utility.Log;

/**
 * The deterministic thread scheduler.  Java threads are used for all
 * program threads of execution, but only one thread is permitted
 * to execute at a time, the scheduler selecting the next thread at
 * each context switch.
 */
public final class RawThreadModel extends ThreadModel {

  private static boolean unitTest = false;

  /**
   * Enable unit-test mode
   */
  public static void setUnitTest() {
    unitTest = true;
  }

  static {
    //Trace.enable(Item.SCHEDULER);
  }

  /** The global scheduler thread */
  private final Thread scheduler = Thread.currentThread();

  private static void trace(String message) {
    Trace.trace(Item.SCHEDULER, "%d: "+message, Thread.currentThread().getId());
  }

  /**
   * The 'scheduler woken' flag.
   *
   * Unchecked invariant: at most one of schedulerIsAwake and (RawThread)isCurrent
   * are true at any given time.
   */
  private boolean schedulerIsAwake = true;

  private RawThread current = null;

  final List<RawThread> collectors = new ArrayList<RawThread>();
  private final List<RawThread> mutators = new ArrayList<RawThread>();

  /** Unique mutator thread number - used to name thne mutator threads */
  private volatile int mutatorId = 0;

  int nextMutatorId() {
    return ++mutatorId;
  }

  /**
   * Scheduling queues.  A thread may be on exactly one of these queues,
   * or on a lock wait queue, or current.
   */
  private final List<RawThread> runQueue = new LinkedList<RawThread>();
  private final List<RawThread> rendezvousQueue = new LinkedList<RawThread>();
  private final List<RawThread> gcWaitQueue = new LinkedList<RawThread>();
  private final List<RawThread> collectorWaitQueue = new LinkedList<RawThread>();

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

  void setCurrent(RawThread current) {
    this.current = current;
  }

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
   * @see org.mmtk.harness.scheduler.ThreadModel#exitGC()
   */
  @Override
  public void exitGC() {
    Trace.trace(Item.SCHEDULER, "%d: exiting GC", current.getId());
    setState(END_GC);
  }

  /**
   * @see org.mmtk.harness.scheduler.ThreadModel#currentCollector()
   */
  @Override
  public Collector currentCollector() {
    return ((CollectorThread)current).c;
  }

  private int currentRendezvous = -1;

  /**
   * The number of mutators waiting for a collection to proceed.
   */
  protected int mutatorsWaitingForGC;

  /**
   * The number of mutators currently executing in the system.
   */
  protected int activeMutators;

  /**
   * @see org.mmtk.harness.scheduler.ThreadModel#rendezvous(int)
   */
  @Override
  public int rendezvous(int where) {
    Trace.trace(Item.SCHEDULER, "%d: rendezvous(%d)", current.getId(), where);
    if (currentRendezvous == -1) {
      currentRendezvous = where;
    } else {
      assert currentRendezvous == where;
    }
    current.setOrdinal(rendezvousQueue.size()+1);
    if (rendezvousQueue.size() == collectors.size()-1) {
      makeRunnable(rendezvousQueue);
      currentRendezvous = -1;
    } else {
      yield(rendezvousQueue);
    }
    Trace.trace(Item.SCHEDULER, "%d: rendezvous(%d) complete: ordinal = %d", current.getId(), where,current.getOrdinal());
    return current.getOrdinal();
  }


  private final Map<String,List<RawThread>> rendezvousQueues = new HashMap<String,List<RawThread>>();

  /**
   * @see org.mmtk.harness.scheduler.ThreadModel#mutatorRendezvous(java.lang.String, int)
   */
  @Override
  public int mutatorRendezvous(String where, int expected) {
    String barrierName = "Barrier-"+where;
    Trace.trace(Item.SCHEDULER, "%s: rendezvous(%s)", current.getId(), barrierName);
    List<RawThread> queue = rendezvousQueues.get(barrierName);
    if (queue == null) {
      queue = new ArrayList<RawThread>(expected);
      rendezvousQueues.put(barrierName, queue);
    }
    current.setOrdinal(queue.size()+1);
    if (queue.size() == expected-1) {
      makeRunnable(queue,false);
      rendezvousQueues.put(barrierName, null);
    } else {
      yield(queue);
    }
    Trace.trace(Item.SCHEDULER, "%d: rendezvous(%s) complete: ordinal = %d", current.getId(), barrierName,current.getOrdinal());
    return current.getOrdinal();
  }

  /**
   * Create a collector thread
   */
  @Override
  public void scheduleCollector() {
    RawThread c = new CollectorThread(this);
    Trace.trace(Item.SCHEDULER, "%d: creating new collector, id=%d",
        Thread.currentThread().getId(), c.getId());
    c.start();
  }

  /**
   * Create a collector thread for specific code (eg unit test)
   */
  @Override
  public Thread scheduleCollector(Schedulable method) {
    RawThread c = new CollectorContextThread(this,method);
    Trace.trace(Item.SCHEDULER, "%d: creating new collector, id=%d",
        Thread.currentThread().getId(), c.getId());
    c.start();
    return c;
  }

  /**
   * Create a mutator thread
   */
  @Override
  public void scheduleMutator(Schedulable method) {
    MutatorThread m = new MutatorThread(method, RawThreadModel.this);
    synchronized(scheduler) {
      Trace.trace(Item.SCHEDULER, "%d: creating new mutator, id=%d", Thread.currentThread().getId(), m.getId());
      m.setName("Mutator-"+mutators.size());
      mutators.add(m);
      if (!isState(MUTATOR)) {
        Trace.trace(Item.SCHEDULER, "%d: Adding to GC wait queue", Thread.currentThread().getId());
        gcWaitQueue.add(m);
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
    setState(BEGIN_GC);
    assert Thread.currentThread() == scheduler;
    makeRunnable(collectors,false);
    schedule();
    assert collectorWaitQueue.size() == collectors.size();
    setState(MUTATOR);
    Trace.trace(Item.SCHEDULER, "%d: Collector threads initialized", Thread.currentThread().getId());
  }

  @Override
  public void triggerGC(int why) {
    Trace.trace(Item.SCHEDULER, "%d: Triggering GC", Thread.currentThread().getId());
    synchronized(scheduler) {
      triggerReason = why;
      setState(BEGIN_GC);
    }
  }

  /**
   * A GC has been requested.  A Mutator thread calls this when it sees the request,
   * and wishes to wait for the GC.  In the raw-threads case, we yield to the
   * collectorWaitQueue.
   *
   * @see org.mmtk.harness.scheduler.ThreadModel#waitForGCStart()
   */
  @Override
  public void waitForGCStart() {
    trace("Yielding to collector wait queue");
    yield(collectorWaitQueue);
  }

  /**
   * Mutator waits for a GC
   */
  @Override
  public void waitForGC() {
    Trace.trace(Item.SCHEDULER, "%d: Yielding to GC wait queue", Thread.currentThread().getId());
    yield(gcWaitQueue);
  }

  @Override
  public void yield() {
    if (isRunning()) {
      if (current.yieldPolicy()) {
        yield(runQueue);
      }
    }
  }

  /**
   * Yield, placing the current thread on a specific queue
   * @param queue
   */
  void yield(List<RawThread> queue) {
    assert current != null;
    queue.add(current);
    Trace.trace(Item.SCHEDULER,"%d: Yielded onto queue with %d members",Thread.currentThread().getId(),queue.size());
    assert queue.size() <= Math.max(mutators.size(),collectors.size()) :
      "yielded to queue size "+queue.size()+" where there are "+mutators.size()+" m and "+collectors.size()+"c";
    current.yieldThread();
  }

  /**
   * Thread-model specific lock factory
   */
  @Override
  public org.mmtk.harness.scheduler.Lock newLock(String name) {
    return new RawLock(this,name);
  }

  /**
   * Schedule gc-context unit tests
   */
  @Override
  public void scheduleGcThreads() {
    /* Advance the GC threads to the collector wait queue */
    initCollectors();

    /* Make them all runnable, as though we had entered a GC */
    makeRunnable(collectorWaitQueue);

    /* Transition to GC state */
    setState(GC);

    /* And run to completion */
    schedule();
  }

  /**
   * The actual scheduler
   */
  @Override
  public void schedule() {
    startRunning();
    assert Thread.currentThread() == scheduler;
    Trace.trace(Item.SCHEDULER, "%d: scheduler begin", scheduler.getId());

    /**
     * The scheduler runs until there are no threads to schedule.
     */
    while (!runQueue.isEmpty()) {
      synchronized(scheduler) {
        assert runQueue.size() <= Math.max(mutators.size(),collectors.size()) :
          "Run queue is unreasonably long, queue="+runQueue.size()+
          ", m="+mutators.size()+", c="+collectors.size();
        if (runQueue.size() > 0) {
          runQueue.remove(0).resumeThread();
          Trace.trace(Item.SCHEDULER, "%d: scheduler sleeping, runqueue=%d", scheduler.getId(), runQueue.size());
          schedWait();
          Trace.trace(Item.SCHEDULER, "%d: scheduler resuming, state %s, runqueue=%d", scheduler.getId(),getState(), runQueue.size());
        }
        switch (getState()) {
          case MUTATOR:
            /* If there are available mutators, at least one of them must be runnable */
            assert mutators.isEmpty() || !runQueue.isEmpty() :
              "mutators.isEmpty()="+mutators.isEmpty()+", runQueue.isEmpty()="+runQueue.isEmpty();
            break;
          case BEGIN_GC:
            if (runQueue.isEmpty()) {
              setState(GC);
              Trace.trace(Item.SCHEDULER, "%d: Changing to state GC - scheduling %d GC threads",
                  scheduler.getId(),collectorWaitQueue.size());
              makeRunnable(collectorWaitQueue);
            }
            break;
          case END_GC:
            if (runQueue.isEmpty()) {
              assert collectorWaitQueue.size() == collectors.size();
              setState(MUTATOR);
              Trace.trace(Item.SCHEDULER, "%d: Changing to state MUTATOR - scheduling %d mutator threads",
                  scheduler.getId(),gcWaitQueue.size());
              makeRunnable(gcWaitQueue);
            }
            break;
          case GC:
            assert ! unitTest && (mutators.size() == 0 || !runQueue.isEmpty()) :
              "Runqueue cannot be empty while GC is in progress";
            break;
        }
      }
    }
    Trace.trace(Item.SCHEDULER, "%d: scheduler end", scheduler.getId());
    stopRunning();
  }

  void makeRunnable(List<RawThread> threads, boolean clear) {
    assert runQueue.size() <= Math.max(mutators.size(),collectors.size());
    runQueue.addAll(threads);
    if (clear) {
      threads.clear();
    }
  }

  void makeRunnable(List<RawThread> threads) {
    makeRunnable(threads,true);
  }

  void wakeScheduler() {
    Trace.trace(Item.SCHEDULER, "%d: waking scheduler", Thread.currentThread().getId());
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

  @Override
  public boolean noThreadsInGC() {
    return isState(MUTATOR);
  }

  @Override
  public boolean gcTriggered() {
    return isState(BEGIN_GC);
  }

}

