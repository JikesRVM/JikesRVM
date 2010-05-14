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

import org.mmtk.harness.options.*;
import org.mmtk.harness.scheduler.AbstractPolicy;
import org.mmtk.harness.scheduler.MMTkThread;
import org.mmtk.harness.vm.*;

import org.mmtk.utility.Log;
import org.mmtk.utility.heap.HeapGrowthManager;
import org.mmtk.utility.options.Options;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.harness.ArchitecturalWord;
import org.vmmagic.unboxed.harness.SimulatedMemory;
import org.vmutil.options.BooleanOption;

/**
 * This is the central class for the MMTk test harness.
 */
public class Harness {

  /** Used for processing harness and MMTk options */
  public static final HarnessOptionSet options = new HarnessOptionSet();

  /** Option for the MMTk plan (prefix) to use */
  public static final Bits bits = new Bits();

  /** Option for the number of collector threads */
  public static final Collectors collectors = new Collectors();

  /** Option for the MMTk plan (prefix) to use */
  public static final Plan plan = new Plan();

  /** Option for the initial heap size */
  public static final InitHeap initHeap = new InitHeap();

  /** Option for the maximum heap size */
  public static final MaxHeap maxHeap = new MaxHeap();

  /** Option for the maximum heap size */
  public static final DumpPcode dumpPcode = new DumpPcode();

  /** Trace options */
  public static final Trace trace = new Trace();

  /** GC stress options */
  public static final GcEvery gcEvery = new GcEvery();

  /** Scheduler */
  public static final Scheduler scheduler = new Scheduler();

  /** Scheduler policy */
  public static final SchedulerPolicy policy = new SchedulerPolicy();

  /** Interval for the fixed scheduler policies */
  public static final YieldInterval yieldInterval = new YieldInterval();

  /* Parameters for the random scheduler policy */
  /** Length of the pseudo-random yield interval sequence */
  public static final RandomPolicyLength randomPolicyLength = new RandomPolicyLength();
  /** Seed for the pseudo-random yield interval sequence */
  public static final RandomPolicySeed randomPolicySeed = new RandomPolicySeed();
  /** Minimum value of each entry in the pseudo-random yield interval sequence */
  public static final RandomPolicyMin randomPolicyMin = new RandomPolicyMin();
  /** Maximum value of each entry in the pseudo-random yield interval sequence */
  public static final RandomPolicyMax randomPolicyMax = new RandomPolicyMax();

  /** Print yield policy statistics on exit */
  public static final PolicyStats policyStats = new PolicyStats();

  /** A set of objects to watch */
  public static final WatchObject watchObject = new WatchObject();

  /** A set of addresses to watch */
  public static final WatchAddress watchAddress = new WatchAddress();

  /** Timeout on unreasonably long GC */
  public static final Timeout timeout = new Timeout();

  /** Whether the Harness sanity checker uses the read barrier */
  public static final BooleanOption sanityUsesReadBarrier = new SanityUsesReadBarrier();

  /** Allocate during collection, to simulate JikesRVM thread iterator objects */
  public static final BooleanOption allocDuringCollection = new AllocDuringCollection();

  private static boolean isInitialized = false;

  /**
   * Start up the harness, including creating the global plan and constraints,
   * and starting off the collector threads.
   *
   * After calling this it is possible to begin creating mutator threads.
   * @param args Command-line arguments
   */
  public static void init(String... args) {
    if (isInitialized) {
      return;
    }
    isInitialized = true;

    /* Always use the harness factory */
    System.setProperty("mmtk.hostjvm", Factory.class.getCanonicalName());

    /* Options used for configuring the plan to use */
    final ArrayList<String> newArgs = new ArrayList<String>();

    /* If the 'bits' arg is specified, parse and apply it first */
    for(String arg: args) {
      if (arg.startsWith("bits=")) {
        options.process(arg);
      }
    }
    ArchitecturalWord.init(Harness.bits.getValue());
    for(String arg: args) {
      if (!options.process(arg)) newArgs.add(arg);
    }
    trace.apply();
    gcEvery.apply();
    org.mmtk.harness.scheduler.Scheduler.init();

    for (Address watchAddr : watchAddress.getAddresses()) {
      System.err.printf("Setting watch at %s%n",watchAddr);
      SimulatedMemory.addWatch(watchAddr);
    }

    /*
     * Perform MMTk initialization in a minimal environment, specifically to
     * give it a per-thread 'Log' object
     */
    MMTkThread m = new MMTkThread() {
      @Override
      public void run() {

        /* Get MMTk breathing */
        ActivePlan.init(plan.getValue());
        ActivePlan.plan.boot();
        HeapGrowthManager.boot(initHeap.getBytes(), maxHeap.getBytes());
        Collector.init(collectors.getValue());

        /* Override some defaults */
        Options.noFinalizer.setValue(true);
        Options.variableSizeHeap.setValue(false);

        /* Process command line options */
        for(String arg: newArgs) {
          if (!options.process(arg)) {
            throw new RuntimeException("Invalid option '" + arg + "'");
          }
        }

        /* Check options */
        assert Options.noFinalizer.getValue(): "noFinalizer must be true";

        /* Finish starting up MMTk */
        ActivePlan.plan.postBoot();
        ActivePlan.plan.fullyBooted();
        Log.flush();
      }
    };
    m.start();
    try {
      m.join();
    } catch (InterruptedException e) {
    }

    org.mmtk.harness.scheduler.Scheduler.initCollectors();

    for (int value : watchObject.getValue()) {
      System.out.printf("Setting watch for object %d%n", value);
      org.mmtk.harness.vm.ObjectModel.watchObject(value);
    }

    /* Add exit handler to print yield stats */
    if (policyStats.getValue()) {
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          AbstractPolicy.printStats();
        }
      });
    }
  }

  /** GC stress - GC on every allocation */
  private static boolean gcEveryAlloc = false;

  /**
   * GC stress - GC after every allocation
   */
  public static void setGcEveryAlloc() {
    gcEveryAlloc = true;
  }

  public static boolean gcEveryAlloc() {
    return gcEveryAlloc;
  }

  static void mmtkShutdown() {
    MMTkThread m = new MMTkThread() {
      @Override
      public void run() {
        ActivePlan.plan.notifyExit(0);
      }
    };
    m.start();
    try {
      m.join();
    } catch (InterruptedException e) {
    }
  }
}
