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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.mmtk.harness.Collector;
import org.mmtk.harness.MMTkThread;
import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.scheduler.Schedulable;
import org.mmtk.harness.scheduler.ThreadModel;
import org.mmtk.utility.Log;

public final class RawThreadModel extends ThreadModel {

  static {
    //Trace.enable(Item.SCHEDULER);
  }

  /** The global state of the scheduler */
  private enum State { MUTATOR, BEGIN_GC, GC, END_GC, RENDEZVOUS };

  private static State state = State.MUTATOR;

  /** The global scheduler thread */
  private final Thread scheduler = Thread.currentThread();

  /**
   * The 'scheduler woken' flag.
   *
   * Unchecked invariant: at most one of schedulerIsAwake and (RawThread)isCurrent
   * are true at any given time.
   */
  private boolean schedulerIsAwake = true;

  /**
   * The superclass of mutator threads in the raw threads model
   */
  private class MutatorThread extends RawThread {
    final Env env = new Env();
    final Schedulable code;

    public MutatorThread(Schedulable code) {
      super(RawThreadModel.this);
      this.code = code;
    }

    /*
     * Thread.run()
     */
    @Override
    public void run() {
      Trace.trace(Item.SCHEDULER, "%d: initial yield",this.getId());
      // Initial 'yield'
      waitTillCurrent();
      Trace.trace(Item.SCHEDULER, "%d: Env.begin()",this.getId());
      env.begin();
      begin();
      Trace.trace(Item.SCHEDULER, "%d: Running mutator code",this.getId());
      code.execute(env);
      env.end();
      end();
    }

    private void uncaughtException(Thread t, Throwable e) {
      env.uncaughtException(t, e);
      end();
    }

    /**
     * Mark a mutator as currently active. If a GC is currently in process we must
     * wait for it to finish.
     */
    public void begin() {
      // Trap uncaught exceptions
      Trace.trace(Item.SCHEDULER, "Setting uncaught exception handler for thread %s",
          Thread.currentThread().getName());
      Thread.currentThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
        public void uncaughtException(Thread t, Throwable e) {
          MutatorThread.this.uncaughtException(t, e);
        }
      });
    }

    protected void end() {
      super.end();
      synchronized(scheduler) {
        mutators.remove(this);
        wakeScheduler();
      }
      Trace.trace(Item.SCHEDULER, "%d: mutator thread exiting",this.getId());
    }
  }

  private class CollectorThread extends RawThread {
    Collector c;

    private CollectorThread(Collector c) {
      super(RawThreadModel.this, c);
      this.c = c;
      setDaemon(true);
    }

    public CollectorThread() {
      this(new Collector());
    }

    public void run() {
      // Initial 'yield'
      waitTillCurrent();
      super.run();
    }
  }

  RawThread current = null;

  void setCurrent(RawThread current) {
    this.current = current;
  }

  static MMTkThread getCurrent() {
    return (MMTkThread)Thread.currentThread();
  }

  private List<RawThread> collectors = new ArrayList<RawThread>();
  private List<RawThread> mutators = new ArrayList<RawThread>();

  /**
   * Scheduling queues.  A thread may be on exactly one of these queues,
   * or on a lock wait queue, or current.
   */
  private List<RawThread> runQueue = new LinkedList<RawThread>();
  private List<RawThread> rendezvousQueue = new LinkedList<RawThread>();
  private List<RawThread> gcWaitQueue = new LinkedList<RawThread>();
  private List<RawThread> collectorWaitQueue = new LinkedList<RawThread>();

  @Override
  public Log currentLog() {
    return current.getLog();
  }

  @Override
  public Mutator currentMutator() {
    return ((MutatorThread)current).env;
  }

  @Override
  public void exitGC() {
    Trace.trace(Item.SCHEDULER, "%d: exiting GC", current.getId());
    state = State.END_GC;
  }

  @Override
  public Collector currentCollector() {
    return ((CollectorThread)current).c;
  }

  private int currentRendezvous = 0;

  @Override
  public int rendezvous(int where) {
    Trace.trace(Item.SCHEDULER, "%d: rendezvous(%d)", current.getId(), where);
    if (state != State.RENDEZVOUS) {
      currentRendezvous = where;
      state = State.RENDEZVOUS;
    } else {
      assert currentRendezvous == where;
    }
    current.setOrdinal(rendezvousQueue.size()+1);
    yield(rendezvousQueue);
    Trace.trace(Item.SCHEDULER, "%d: rendezvous(%d) complete: ordinal = %d", current.getId(), where,current.getOrdinal());
    return current.getOrdinal();
  }

  /**
   * Create a collector thread
   */
  @Override
  public void scheduleCollector() {
    RawThread c = new CollectorThread();
    synchronized(scheduler) {
      Trace.trace(Item.SCHEDULER, "%d: creating new collector, id=%d", Thread.currentThread().getId(), c.getId());
      c.setName("Collector-"+collectors.size());
      collectors.add(c);
      c.start();
    }

    Trace.trace(Item.SCHEDULER, "%d: collector created and scheduled", Thread.currentThread().getId());
  }

  /**
   * Create a mutator thread
   */
  @Override
  public void scheduleMutator(Schedulable method) {
    MutatorThread m = new MutatorThread(method);
    synchronized(scheduler) {
      Trace.trace(Item.SCHEDULER, "%d: creating new mutator, id=%d", Thread.currentThread().getId(), m.getId());
      m.setName("Mutator-"+mutators.size());
      mutators.add(m);
      if (state != RawThreadModel.State.MUTATOR) {
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
    state = State.GC;
    assert Thread.currentThread() == scheduler;
    runQueue.addAll(collectors);
    schedule();
    assert collectorWaitQueue.size() == collectors.size();
    state = State.MUTATOR;
    Trace.trace(Item.SCHEDULER, "%d: Collector threads initialized", Thread.currentThread().getId());
  }

  @Override
  public void triggerGC(int why) {
    Trace.trace(Item.SCHEDULER, "%d: Triggering GC", Thread.currentThread().getId());
    synchronized(scheduler) {
      triggerReason = why;
      inGC++;
      state = State.BEGIN_GC;
    }
  }

  @Override
  public void waitForGCStart() {
    yield(collectorWaitQueue);
  }

  /**
   * Mutator waits for a GC
   */
  @Override
  public void waitForGC() {
    inGC++;
    yield(gcWaitQueue);
  }

  @Override
  public void yield() {
    if (current.yieldPolicy()) {
      yield(runQueue);
    }
  }

  /**
   * Yield, placing the current thread on a specific queue
   * @param queue
   */
  void yield(List<RawThread> queue) {
    assert current != null;
    queue.add(current);
    Trace.trace(Item.SCHEDULER,"Yielded onto queue with %d members",queue.size());
    assert queue.size() <= Math.max(mutators.size(),collectors.size()) :
      "yielded to queue size "+queue.size()+" where there are "+mutators.size()+" m and "+collectors.size()+"c";
    current.yieldThread();
  }

  /**
   * Thread-model specific lock factory
   */
  @Override
  public org.mmtk.harness.scheduler.Lock newLock(String name) {
    return new Lock(this,name);
  }

  /**
   * The actual scheduler
   */
  @Override
  public void schedule() {
    while (!runQueue.isEmpty()) {
      synchronized(scheduler) {
        assert runQueue.size() <= Math.max(mutators.size(),collectors.size()) :
          "Run queue is unreasonably long, queue="+runQueue.size()+", m="+mutators.size()+", c="+collectors.size();
        runQueue.remove(0).resumeThread();
        Trace.trace(Item.SCHEDULER, "%d: scheduler sleeping, runqueue=%d", scheduler.getId(), runQueue.size());
        schedWait();
        Trace.trace(Item.SCHEDULER, "%d: scheduler resuming, state %s, runqueue=%d", scheduler.getId(),state, runQueue.size());
        switch (state) {
          case MUTATOR:
            assert mutators.isEmpty() || !runQueue.isEmpty();
            break;
          case BEGIN_GC:
            if (runQueue.isEmpty()) {
              assert gcWaitQueue.size() == mutators.size();
              state = State.GC;
              Trace.trace(Item.SCHEDULER, "%d: Changing to state GC", scheduler.getId());
              makeRunnable(collectorWaitQueue);
            }
            break;
          case END_GC:
            if (runQueue.isEmpty()) {
              assert collectorWaitQueue.size() == collectors.size();
              state = State.MUTATOR;
              Trace.trace(Item.SCHEDULER, "%d: Changing to state MUTATOR", scheduler.getId());
              makeRunnable(gcWaitQueue);
              inGC = 0;
            }
            break;
          case GC:
            assert mutators.size() == 0 || !runQueue.isEmpty() :
              "Runqueue cannot be empty while GC is in progress";
            break;
          case RENDEZVOUS:
            if (runQueue.isEmpty()) {
              assert rendezvousQueue.size() == collectors.size();
              Trace.trace(Item.SCHEDULER, "%d: Rendezvous complete - changing to state GC", scheduler.getId());
              state = State.GC;
              makeRunnable(rendezvousQueue);
              currentRendezvous = 0;
            }
            break;
        }
      }
    }
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
    while (!schedulerIsAwake) {
      try {
        Trace.trace(Item.SCHEDULER, "%d: waiting for scheduler wakeup", Thread
            .currentThread().getId());
        assert Thread.currentThread() == scheduler;
        scheduler.wait(1);
        Trace.trace(Item.SCHEDULER, "%d: scheduler wakeup complete", Thread
            .currentThread().getId());
      } catch (InterruptedException e) {
      }
    }
  }

}

