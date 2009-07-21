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
import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
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
    MutatorThread t = new MutatorThread(code);
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
    CollectorContextThread t = new CollectorContextThread(code);
    collectorThreads.add(t);
    t.start();
    return t;
  }

  private JavaThread currentMMTkThread() {
    return ((JavaThread)Thread.currentThread());
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

  private static int mutatorId = 0;

  private final class MutatorThread extends JavaThread {
    private final Schedulable code;
    private final Env env = new Env();


    MutatorThread(Schedulable code) {
      this.code = code;
      setName("Mutator-"+(mutatorId++));
      Trace.trace(Item.SCHEDULER, "MutatorThread created");
    }

    @Override
    public void run() {
      Trace.trace(Item.SCHEDULER, "Env.begin()");
      env.begin();
      begin();
      Trace.trace(Item.SCHEDULER, "Running mutator code");
      code.execute(env);
      env.end();
      endMutator();
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
          env.uncaughtException(t, e);
        }
      });
      synchronized (count) {
        if (!allWaitingForGC()) {
          activeMutators++;
          return;
        }
        mutatorsWaitingForGC++;
      }
      waitForGC(false);
      synchronized (count) {
        mutatorsWaitingForGC--;
        activeMutators++;
      }
    }

    private void endMutator() {
      boolean lastToGC;
      synchronized (count) {
        lastToGC = (mutatorsWaitingForGC == (activeMutators - 1));
        if (!lastToGC) {
          activeMutators--;
          return;
        }
        mutatorsWaitingForGC++;
      }
      waitForGC(lastToGC);
      synchronized (count) {
          mutatorsWaitingForGC--;
          activeMutators--;
      }
    }

  }

  private static int collectorId = 0;

  private class CollectorThread extends JavaThread {
    protected final Collector collector;

    protected CollectorThread(boolean daemon) {
      this.collector = new Collector();
      setName("Collector-"+(collectorId++));
      setDaemon(daemon);
    }

    private CollectorThread() {
      this(true);
    }

    @Override
    public void run() {
      collectorThreadLocal.set(collector);
      collector.run();
    }

  }

  private final class CollectorContextThread extends CollectorThread {
    final Schedulable code;

    private CollectorContextThread(Schedulable code) {
      super(false);
      this.code = code;
    }

    @Override
    public void run() {
      collectorThreadLocal.set(collector);
      waitForGCStart();
      code.execute(new Env());
      collectorThreads.remove(this);
      exitGC();
    }

  }

  /*****************************************************************************
   *
   * Scheduling of GCs
   *
   */

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

  @Override
  public void schedule() {
    startRunning();
    /* Wait for the mutators */
    for (Thread mutator : mutatorThreads) {
      try {
        mutator.join();
      } catch (InterruptedException e) {
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
