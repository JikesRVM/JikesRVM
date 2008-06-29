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
package org.mmtk.harness;

import java.util.ArrayList;

import org.mmtk.harness.vm.ActivePlan;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.Plan;
import org.mmtk.utility.heap.HeapGrowthManager;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.Collection;

/**
 * This class represents a collector thread.
 */
public final class Collector extends MMTkThread {

  /** Registered collectors */
  private static ArrayList<Collector> collectors = new ArrayList<Collector>();

  /** Thread access to current collector */
  private static ThreadLocal<Collector> collectorThreadLocal = new ThreadLocal<Collector>();

  /**
   * Get a collector by id.
   */
  public static Collector get(int id) {
    return collectors.get(id);
  }

  /**
   * Get the currently executing collector.
   */
  public static Collector current() {
    Collector c = collectorThreadLocal.get();
    assert c != null: "Collector.current() called from a thread without a collector context";
    assert c == Thread.currentThread() : "Collector.current() does not match Thread.currentThread()";
    return c;
  }

  /**
   * The number of collector threads that have been created.
   */
  public static int count() {
    return collectors.size();
  }

  /**
   * Register a collector thread, returning the allocated id.
   */
  public static synchronized int register(CollectorContext context) {
    int id = collectors.size();
    collectors.add(null);
    return id;
  }

  /**
   * Initialise numCollector collector threads.
   */
  public static void init(int numCollectors) {
    try {
      Class<?> collectorClass = Class.forName(Harness.plan.getValue() + "Collector");
      for(int i = 0; i < numCollectors; i++) {
        Collector c = new Collector((CollectorContext)collectorClass.newInstance());
        c.start();
      }
    } catch (Exception ex) {
      throw new RuntimeException("Could not create Collector", ex);
    }
  }

  /**
   * The MMTk CollectorContext for this collector thread.
   */
  private final CollectorContext context;

  /**
   * Create a new Collector
   */
  private Collector(final CollectorContext context) {
    collectors.set(context.getId(), this);
    this.context = context;
    setDaemon(true);
    setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
      public void uncaughtException(Thread t, Throwable e) {
        System.err.print("Collector " + context.getId() + " caused unexpected exception: ");
        e.printStackTrace();
        System.exit(1);
      }
    });
  }

  /** The number of collectors executing GC */
  private static int inGC;

  /** The number of collections that have occurred */
  public static int collectionCount;

  /** The current base count of collection attempts */
  public static int collectionAttemptBase;

  /**
   * Are there no threads currently in GC?
   */
  public static boolean noThreadsInGC() {
    return inGC == 0;
  }

  /**
   * Has a GC been triggered?
   */
  public static boolean gcTriggered() {
    return inGC > 0;
  }

  /** Synchronisation object used for GC triggering */
  private static Object trigger = new Object();

  /** The trigger for this GC */
  private static int triggerReason;

  /**
   * Wait for a GC to complete
   */
  public static void waitForGC(boolean last) {
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
  }

  /**
   * Trigger a collection for the given reason
   */
  public static void triggerGC(int why) {
    synchronized (trigger) {
      triggerReason = why;
      inGC = Harness.collectors.getValue();
      trigger.notifyAll();
    }
  }

  /**
   * A GC thread has completed its GC work.
   */
  private static void exitGC() {
    synchronized (trigger) {
      inGC--;
      if (inGC == 0) trigger.notifyAll();
    }
  }

  /**
   * Return the MMTk CollectorContext for this collector.
   */
  public CollectorContext getContext() {
    return context;
  }

  /** Used during a GC to synchronise GC threads */
  private static Object rendezvousObject = new Object();

  /** The rank that was given to the last thread to arrive at the rendezvous */
  private static int currentRank = 0;

  /**
   * Rendezvous with all other processors, returning the rank
   * (that is, the order this processor arrived at the barrier).
   */
  public static int rendezvous(int where) {
    synchronized(rendezvousObject) {
      int rank = ++currentRank;
      if (currentRank == org.mmtk.vm.VM.activePlan.collectorCount()) {
        currentRank = 0;
        rendezvousObject.notifyAll();
      } else {
        try {
          rendezvousObject.wait();
        } catch (InterruptedException ie) {
          assert false : "Interrupted in rendezvous";
        }
      }
      return rank;
    }
  }

  /**
   * The main collector execution loop. Wait for a GC to be triggered, do the GC work and then wait again.
   */
  public void run() {
    collectorThreadLocal.set(this);
    boolean primary = context.getId() == 0;
    while(true) {
      synchronized(trigger) {
        while(inGC == 0 || !Mutator.allWaitingForGC()) {
          try {
            trigger.wait();
          } catch (InterruptedException ie) {}
        }
      }

      if (primary) {
        Plan.setCollectionTrigger(triggerReason);
      }

      long startTime = System.nanoTime();
      boolean internalPhaseTriggered = (triggerReason == Collection.INTERNAL_PHASE_GC_TRIGGER);
      boolean userTriggered = (triggerReason == Collection.EXTERNAL_GC_TRIGGER);
      rendezvous(5000);

      do {
        context.collect();
        rendezvous(5200);

        if (primary) {
          long elapsedTime = System.nanoTime() - startTime;
          HeapGrowthManager.recordGCTime(elapsedTime / 1e6);
          if (ActivePlan.plan.lastCollectionFullHeap() && !internalPhaseTriggered) {
            if (Options.variableSizeHeap.getValue() && !userTriggered) {
              // Don't consider changing the heap size if gc was forced by System.gc()
              HeapGrowthManager.considerHeapSize();
            }
            HeapGrowthManager.reset();
          }

          if (internalPhaseTriggered) {
            if (ActivePlan.plan.lastCollectionFailed()) {
              internalPhaseTriggered = false;
              Plan.setCollectionTrigger(Collection.INTERNAL_GC_TRIGGER);
            }
          }

          collectionAttemptBase++;
          collectionCount += 1;
        }

        startTime = System.nanoTime();
        rendezvous(5201);
      } while (ActivePlan.plan.lastCollectionFailed() && !Plan.isEmergencyCollection());


      if (primary && !internalPhaseTriggered) {
        /* If the collection failed, we may need to throw OutOfMemory errors.
         * As we have not cleared the GC flag, allocation is not budgeted.
         *
         * This is not flawless in the case we physically can not allocate
         * anything right after a GC, but that case is unlikely (we can
         * not make it happen) and is a lot of work to get around. */
        if (Plan.isEmergencyCollection()) {
          boolean gcFailed = ActivePlan.plan.lastCollectionFailed();
          // Allocate OOMEs (some of which *may* not get used)
          for(int m=0; m < Mutator.count(); m++) {
            Mutator mutator = Mutator.get(m);
            if (mutator.getCollectionAttempts() > 0) {
              /* this thread was allocating */
              if (gcFailed || mutator.isPhysicalAllocationFailure()) {
                mutator.setOutOfMemory(true);
              }
            }
          }
        }
      }

      if (primary) {
        collectionAttemptBase = 0;

        /* This is where we would schedule Finalization, if we supported it. */
      }
      rendezvous(5202);
      if (primary) {
        Plan.collectionComplete();
      }
      exitGC();
    }
  }
}
