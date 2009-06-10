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
package org.mmtk.harness;

import java.util.ArrayList;

import org.mmtk.harness.sanity.Sanity;
import org.mmtk.harness.scheduler.Scheduler;
import org.mmtk.harness.vm.ActivePlan;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.Plan;
import org.mmtk.utility.heap.HeapGrowthManager;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.Collection;

/**
 * This class represents a collector thread.
 */
public final class Collector implements Runnable {

  /** Registered collectors */
  private static ArrayList<Collector> collectors = new ArrayList<Collector>();

  /**
   * Get a collector by id.
   * @param id The ID of the collector
   * @return The collector
   */
  public static Collector get(int id) {
    return collectors.get(id);
  }

  /**
   * @return the currently executing collector.
   */
  public static Collector current() {
    Collector c = Scheduler.currentCollector();
    assert c != null: "Collector.current() called from a thread without a collector context";
    return c;
  }

  /**
   * @return The number of collector threads that have been created.
   */
  public static int count() {
    return collectors.size();
  }

  /**
   * Register a collector thread
   * @return the allocated id.
   */
  public static synchronized int allocateCollectorId() {
    int id = collectors.size();
    collectors.add(null);
    return id;
  }

  /**
   * Initialise numCollector collector threads.
   * @param numCollectors # collectors
   */
  public static void init(int numCollectors) {
    for(int i = 0; i < numCollectors; i++) {
      Scheduler.scheduleCollector();
    }
  }

  /**
   * The MMTk CollectorContext for this collector thread.
   */
  private final CollectorContext context;

  /**
   * Create a new Collector
   */
  public Collector() {
    try {
      Class<?> collectorClass = Class.forName(Harness.plan.getValue() + "Collector");
      this.context = (CollectorContext)collectorClass.newInstance();
      this.context.initCollector(allocateCollectorId());
    } catch (Exception ex) {
      throw new RuntimeException("Could not create Collector", ex);
    }
    collectors.set(context.getId(), this);
    Thread.currentThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      public void uncaughtException(Thread t, Throwable e) {
        System.err.print("Collector " + context.getId() + " caused unexpected exception: ");
        e.printStackTrace();
        System.exit(1);
      }
    });
  }

  /** The number of collections that have occurred */
  private static int collectionCount;

  /**
   * @return The number of GCs commenced so far
   */
  public static int getCollectionCount() {
    return collectionCount;
  }

  /** The current base count of collection attempts */
  private static int collectionAttemptBase;

  /**
   * @return The current base count of collection attempts
   */
  public static int getCollectionAttemptBase() {
    return collectionAttemptBase;
  }

  /** Has a heap dump been requested? */
  private static boolean heapDumpRequested;

  /**
   * Request a heap dump at the next GC.
   */
  public static void requestHeapDump() {
    heapDumpRequested = true;
  }

  /**
   * Trigger a collection for the given reason
   * @param why Reason (as defined by MMTk VM interface)
   */
  public static void triggerGC(int why) {
    Scheduler.triggerGC(why);
  }

  /**
   * @return the MMTk CollectorContext for this collector.
   */
  public CollectorContext getContext() {
    return context;
  }

  /**
   * Rendezvous with all other processors, returning the rank
   * (that is, the order this processor arrived at the barrier).
   * @param where An identifier for the rendezvous point
   * @return The order of arrival
   */
  public static int rendezvous(int where) {
    return Scheduler.rendezvous(where);
  }

  /**
   * The main collector execution loop. Wait for a GC to be triggered,
   * do the GC work and then wait again.
   */
  public void run() {
    while(true) {
      Scheduler.waitForGCStart();

      /*
       * Make all GC errors fatal
       */
      try {
        collect();
      } catch (Exception e) {
        e.printStackTrace();
        System.exit(1);
      }

      Scheduler.exitGC();
    }
  }

  /**
   * A timeout thread.  Exit the harness if it isn't cancelled in time.
   */
  private static final class TimeoutThread implements Runnable {
    private static final boolean VERBOSE = false;
    private static final int MILLIS_PER_SECOND = 1000;
    private final long timeout;
    private boolean cancelled = false;
    private volatile boolean started = false;

    /**
     * Create a timeout object and start it running in its own
     * thread.
     * @param seconds Timeout in seconds
     */
    private TimeoutThread(int seconds) {
      this.timeout = seconds * MILLIS_PER_SECOND;
      new Thread(this).start();
      synchronized (this) {
        while (!started) {
          try {
            wait();
          } catch (InterruptedException e) {
          }
        }
      }
    }

    /**
     * @see java.lang.Thread#run()
     */
    @Override
    public void run() {
      long startTime = System.currentTimeMillis();
      synchronized (this) {
        while (!cancelled) {
          try {
            started = true;
            notify();
            long now = System.currentTimeMillis();
            if (now - startTime >= timeout) {
              System.err.printf("Collection exceeded timeout %dms%n",timeout);
              System.exit(1);
            }
            long sleepTime = Math.max(1,timeout - (now - startTime));
            if (VERBOSE) {
              System.err.printf("Collection timeout: sleeping for %dms%n",sleepTime);
            }
            wait(sleepTime);
          } catch (InterruptedException e) {
            // Ignore interruptions
          }
        }
      }
    }

    /** Cancel the timeout */
    public void cancel() {
      synchronized (this) {
        if (VERBOSE) {
          System.err.printf("Collection timeout: cancelled%n");
        }
        cancelled = true;
        notify();
      }
    }
  }

  /**
   * Perform a GC
   */
  private void collect() {
    boolean primary = context.getId() == 0;
    Sanity sanity = new Sanity();
    TimeoutThread timeout = null;
    if (primary) {
      Plan.setCollectionTrigger(Scheduler.getTriggerReason());
      sanity.snapshotBefore();
      timeout = new TimeoutThread(Harness.timeout.getValue());
    }

    long startTime = System.nanoTime();
    boolean internalPhaseTriggered = (Scheduler.getTriggerReason() == Collection.INTERNAL_PHASE_GC_TRIGGER);
    boolean userTriggered = (Scheduler.getTriggerReason() == Collection.EXTERNAL_GC_TRIGGER);
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
      sanity.snapshotAfter();
      sanity.assertSanity();
      collectionAttemptBase = 0;

      /* This is where we would schedule Finalization, if we supported it. */
      if (heapDumpRequested) {
        Mutator.dumpHeap();
        heapDumpRequested = false;
      }
    }
    rendezvous(5202);
    if (primary) {
      Plan.collectionComplete();
      timeout.cancel();
    }
  }

}
