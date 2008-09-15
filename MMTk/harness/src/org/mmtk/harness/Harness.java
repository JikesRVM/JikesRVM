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

import org.mmtk.harness.options.*;
import org.mmtk.harness.scheduler.AbstractPolicy;
import org.mmtk.harness.vm.*;

import org.mmtk.utility.heap.HeapGrowthManager;
import org.mmtk.utility.options.Options;

/**
 * This is the central class for the MMTk test harness.
 */
public class Harness {

  /** Used for processing harness and MMTk options */
  public static HarnessOptionSet options = new HarnessOptionSet();

  /** Option for the number of collector threads */
  public static Collectors collectors = new Collectors();

  /** Option for the MMTk plan (prefix) to use */
  public static Plan plan = new Plan();

  /** Option for the initial heap size */
  public static InitHeap initHeap = new InitHeap();

  /** Option for the maximum heap size */
  public static MaxHeap maxHeap = new MaxHeap();

  /** Option for the maximum heap size */
  public static DumpPcode dumpPcode = new DumpPcode();

  /** Trace options */
  public static Trace trace = new Trace();

  /** GC stress options */
  public static GcEvery gcEvery = new GcEvery();

  /** Scheduler */
  public static Scheduler scheduler = new Scheduler();

  /** Scheduler policy */
  public static SchedulerPolicy policy = new SchedulerPolicy();

  /** Interval for the fixed scheduler policies */
  public static YieldInterval yieldInterval = new YieldInterval();

  /** Parameters for the random scheduler policy */
  public static RandomPolicyLength randomPolicyLength = new RandomPolicyLength();
  public static RandomPolicySeed randomPolicySeed = new RandomPolicySeed();
  public static RandomPolicyMin randomPolicyMin = new RandomPolicyMin();
  public static RandomPolicyMax randomPolicyMax = new RandomPolicyMax();

  /** Scheduler policy */
  public static PolicyStats policyStats = new PolicyStats();

  private static boolean isInitialized = false;

  /**
   * Start up the harness, including creating the global plan and constraints,
   * and starting off the collector threads.
   *
   * After calling this it is possible to begin creating mutator threads.
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
    for(String arg: args) {
      if (!options.process(arg)) newArgs.add(arg);
    }
    trace.apply();
    gcEvery.apply();
    /* Get MMTk breathing */
    ActivePlan.init(plan.getValue());
    ActivePlan.plan.boot();
    HeapGrowthManager.boot(initHeap.getBytes(), maxHeap.getBytes());
    Collector.init(collectors.getValue());

    /* Override some defaults */
    Options.noFinalizer.setValue(true);
    Options.noReferenceTypes.setValue(true);
    Options.variableSizeHeap.setValue(false);

    /* Process command line options */
    for(String arg: newArgs) {
      if (!options.process(arg)) {
        throw new RuntimeException("Invalid option '" + arg + "'");
      }
    }

    /* Check options */
    assert Options.noFinalizer.getValue(): "noFinalizer must be true";
    assert Options.noReferenceTypes.getValue(): "noReferenceTypes must be true";

    /* Finish starting up MMTk */
    ActivePlan.plan.postBoot();
    ActivePlan.plan.fullyBooted();

    /* Add exit handler to print yield stats */
    if (policyStats.getValue()) {
      Runtime.getRuntime().addShutdownHook(new Thread() {
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
}
