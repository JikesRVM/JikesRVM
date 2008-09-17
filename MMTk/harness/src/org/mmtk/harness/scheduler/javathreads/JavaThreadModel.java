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
package org.mmtk.harness.scheduler.javathreads;

import org.mmtk.harness.Collector;
import org.mmtk.harness.Harness;
import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.scheduler.Schedulable;
import org.mmtk.harness.scheduler.ThreadModel;
import org.mmtk.utility.Log;

public final class JavaThreadModel extends ThreadModel {
  static {
    //Trace.enable(Item.SCHEDULER);
  }

  /**
   * Create a new mutator thread
   */
  @Override
  public void scheduleMutator(Schedulable code) {
    Trace.trace(Item.SCHEDULER, "Scheduling new mutator");
    Thread t = new MutatorThread(code);
    t.start();
  }

  /**
   * Create a new collector thread
   */
  @Override
  public void scheduleCollector() {
    Trace.trace(Item.SCHEDULER, "Scheduling new collector");
    Thread t = new CollectorThread();
    t.start();
  }

  private JavaThread currentMMTkThread() {
    return ((JavaThread)Thread.currentThread());
  }

  @Override
  public void yield() {
    if (currentMMTkThread().yieldPolicy()) {
      Thread.yield();
    }
  }

  public Log currentLog() {
    return currentMMTkThread().getLog();
  }

  public Mutator currentMutator() {
    assert Thread.currentThread() instanceof MutatorThread  : "Current thread is not a Mutator";
    return ((MutatorThread)Thread.currentThread()).env;
  }

  protected static int mutatorId = 0;

  private final class MutatorThread extends JavaThread {
    private final Schedulable code;
    private final Env env = new Env();


    MutatorThread(Schedulable code) {
      this.code = code;
      setName("Mutator-"+(mutatorId++));
      Trace.trace(Item.SCHEDULER, "MutatorThread created");
    }

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

  protected static int collectorId = 0;

  private final class CollectorThread extends JavaThread {
    final Collector collector;

    private CollectorThread(Collector collector) {
      super(collector);
      this.collector = collector;
      setName("Collector-"+(collectorId++));
      setDaemon(true);
    }

    private CollectorThread() {
      this(new Collector());
    }

    @Override
    public void run() {
      collectorThreadLocal.set(collector);
      super.run();
    }

  }

  /*****************************************************************************
   *
   * Scheduling of GCs
   *
   */

  /** Synchronisation object used for GC triggering */
  private Object trigger = new Object();

  /**
   * Wait for a GC to complete
   */
  private void waitForGC(boolean last) {
    Trace.trace(Item.SCHEDULER, "%d waitForGC in", Thread.currentThread().getId());
    synchronized (trigger) {
      if (last) {
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
    return mutatorsWaitingForGC == activeMutators;
  }

  /**
   * Trigger a collection for the given reason
   */
  @Override
  public void triggerGC(int why) {
    synchronized (trigger) {
      triggerReason = why;
      inGC = Harness.collectors.getValue();
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
      if (inGC == 0) trigger.notifyAll();
    }
  }

  @Override
  public void waitForGCStart() {
    synchronized(trigger) {
      while(inGC == 0 || !allWaitingForGC()) {
        try {
          trigger.wait();
        } catch (InterruptedException ie) {}
      }
      Trace.trace(Item.SCHEDULER, "GC has started");
    }
  }

  /** Used during a GC to synchronise GC threads */
  private final Rendezvous rendezvous = new Rendezvous();

  /**
   * Object used for synchronizing the number of mutators waiting for a gc.
   */
  public static Object count = new Object();
  /** Thread access to current collector */
  public static ThreadLocal<Collector> collectorThreadLocal = new ThreadLocal<Collector>();

  @Override
  public int rendezvous(int where) {
    return rendezvous.rendezvous(where);
  }

  @Override
  public Collector currentCollector() {
    return collectorThreadLocal.get();
  }

  @Override
  public Lock newLock(String name) {
    return new org.mmtk.harness.scheduler.javathreads.Lock(name);
  }

  @Override
  public void schedule() {
    // Do nothing - java does it :)
  }
}
