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
import static org.mmtk.harness.scheduler.ThreadModel.State.*;
import org.mmtk.utility.Log;

public final class RawThreadModel extends ThreadModel {

  private static boolean unitTest = false;

  public static void setUnitTest() {
    unitTest = true;
  }

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

  /**
   * The super-class of collector threads
   */
  private static class CollectorThread extends RawThread {
    protected final Collector c;

    /**
     * Create a collector thread, running the 'run'method of collector c
     *
     * @param model
     * @param c
     * @param daemon
     */
    private CollectorThread(RawThreadModel model,Collector c, boolean daemon) {
      super(model);
      this.c = c;
      setName("Collector-"+model.collectors.size());
      model.collectors.add(this);
      setDaemon(daemon);
      Trace.trace(Item.SCHEDULER, "%d: collector thread %d \"%s\" created (%d total)",
          Thread.currentThread().getId(), getId(), getName(),model.collectors.size());
    }

    /** Create a collector thread, with a new collector */
    protected CollectorThread(RawThreadModel model, boolean daemon) {
      this(model,new Collector(), daemon);
    }

    /** Create a collector thread, with a new collector */
    public CollectorThread(RawThreadModel model) {
      this(model,true);
    }

    @Override
    public void run() {
      // Initial 'yield'
      waitTillCurrent();
      c.run();
      assert false : "Collector threads should never exit";
    }
  }

  /**
   * The super-class of collector-context threads.  These are unit tests
   * that need to run in collector context.
   */
  private class CollectorContextThread extends CollectorThread {
    private final Schedulable code;

    public CollectorContextThread(RawThreadModel model,Schedulable code) {
      super(model,false);
      this.code = code;
    }

    @Override
    public void run() {
      // Initial 'yield'
      waitTillCurrent();
      Trace.trace(Item.SCHEDULER, "%d: Collector context waiting for GC", current.getId());
      waitForGCStart();
      Trace.trace(Item.SCHEDULER, "%d: Starting collector context", current.getId());
      code.execute(new Env());
      synchronized(scheduler) {
        collectors.remove(this);
        wakeScheduler();
      }
      Trace.trace(Item.SCHEDULER, "%d: Collector context complete", current.getId());
    }
  }


  void setCurrent(RawThread current) {
    this.current = current;
  }

  static MMTkThread getCurrent() {
    return (MMTkThread)Thread.currentThread();
  }

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
    setState(END_GC);
  }

  @Override
  public Collector currentCollector() {
    return ((CollectorThread)current).c;
  }

  private int currentRendezvous = 0;

  /**
   * The number of mutators waiting for a collection to proceed.
   */
  protected int mutatorsWaitingForGC;

  /**
   * The number of mutators currently executing in the system.
   */
  protected int activeMutators;

  @Override
  public int rendezvous(int where) {
    Trace.trace(Item.SCHEDULER, "%d: rendezvous(%d)", current.getId(), where);
    if (!isState(RENDEZVOUS)) {
      currentRendezvous = where;
      setState(RENDEZVOUS);
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
    MutatorThread m = new MutatorThread(method);
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

  @Override
  public void waitForGCStart() {
    Trace.trace(Item.SCHEDULER, "%d: Yielding to collector wait queue", Thread.currentThread().getId());
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
            assert mutators.isEmpty() || !runQueue.isEmpty();
            break;
          case BEGIN_GC:
            if (runQueue.isEmpty()) {
              assert gcWaitQueue.size() == mutators.size();
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
          case RENDEZVOUS:
            if (runQueue.isEmpty()) {
              assert rendezvousQueue.size() == collectors.size();
              Trace.trace(Item.SCHEDULER, "%d: Rendezvous complete - changing to state GC", scheduler.getId());
              setState(GC);
              makeRunnable(rendezvousQueue);
              currentRendezvous = 0;
            }
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
//        Trace.trace(Item.SCHEDULER, "%d: waiting for scheduler wakeup",
//            Thread.currentThread().getId());
        scheduler.wait(1);
//        Trace.trace(Item.SCHEDULER, "%d: scheduler wakeup complete",
//            Thread.currentThread().getId());
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

