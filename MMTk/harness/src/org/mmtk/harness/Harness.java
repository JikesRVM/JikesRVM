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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.mmtk.harness.options.*;
import org.mmtk.harness.scheduler.AbstractPolicy;
import org.mmtk.harness.scheduler.MMTkThread;
import org.mmtk.harness.vm.*;

import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;
import org.mmtk.policy.Space;
import org.mmtk.policy.Space.SpaceVisitor;
import org.mmtk.utility.Log;
import org.mmtk.utility.heap.HeapGrowthManager;
import org.mmtk.utility.options.Options;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.harness.ArchitecturalWord;
import org.vmmagic.unboxed.harness.MemoryConstants;
import org.vmmagic.unboxed.harness.SimulatedMemory;
import org.vmutil.options.BooleanOption;
import org.vmutil.options.StringOption;

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

  /** Scalable heap size specification */
  public static final BaseHeap baseHeap = new BaseHeap();

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

  /** Timeout on unreasonably long lock wait */
  public static final LockTimeout lockTimeout = new LockTimeout();

  /** Whether the Harness sanity checker uses the read barrier */
  public static final BooleanOption sanityUsesReadBarrier = new SanityUsesReadBarrier();

  /** Set watch points on variables */
  public static final StringOption watchVar = new WatchVar();

  protected static final double MB = 1024*1024;

  private static boolean initialized = false;

  public static void initArchitecture(List<String> args){
    /* If the 'bits' arg is specified, parse and apply it first */
    for(String arg: args) {
      if (arg.startsWith("bits=")) {
        options.process(arg);
      }
    }
    ArchitecturalWord.init(Harness.bits.getValue());
  }

  public static void init(List<String> args) {
    init(args.toArray(new String[0]));
  }

  /**
   * Start up the harness, including creating the global plan and constraints,
   * and starting off the collector threads.
   *
   * After calling this it is possible to begin creating mutator threads.
   * @param args Command-line arguments
   */
  public static void init(String... args) {
    initialized = true;

    /* Always use the harness factory */
    System.setProperty("mmtk.hostjvm", Factory.class.getCanonicalName());

    /* Options used for configuring the plan to use */
    final ArrayList<String> newArgs = new ArrayList<String>();

    for(String arg: args) {
      if (!options.process(arg)) newArgs.add(arg);
    }

    /* If we're using the baseHeap mechanism, override initHeap and maxheap */
    if (baseHeap.getPages() != 0 && initHeap.getPages() == initHeap.getDefaultPages()) {
      applyHeapScaling();
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

        try {
          /* Get MMTk breathing */
          ActivePlan.init(PlanSpecificConfig.planClass(plan.getValue()));

          /* Override some defaults */
          Options.noFinalizer.setValue(true);
          Options.variableSizeHeap.setValue(false);

          /* Process command line options */
          for(String arg: newArgs) {
            if (!options.process(arg)) {
              throw new RuntimeException("Invalid option '" + arg + "'");
            }
          }

          ActivePlan.plan.enableAllocation();
          if (org.mmtk.utility.options.Options.verbose.getValue() > 0) {
            System.err.printf("[Harness] Configuring heap size [%4.2fMB..%4.2fMB]%n",
                initHeap.getBytes().toLong()/MB,
                maxHeap.getBytes().toLong()/MB);
          }
          HeapGrowthManager.boot(initHeap.getBytes(), maxHeap.getBytes());

          /* Check options */
          assert Options.noFinalizer.getValue(): "noFinalizer must be true";

          /* Finish starting up MMTk */
          ActivePlan.plan.processOptions();
          ActivePlan.plan.enableCollection();
          ActivePlan.plan.fullyBooted();
          checkSpaces();
          Log.flush();
        } catch (Throwable e) {
          e.printStackTrace();
          Main.exitWithFailure();
        }
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

  /**
   * Apply the plan-specific heap scaling used when the "heap" option
   * is used.
   */
  private static void applyHeapScaling() {
    double heapFactor = PlanSpecificConfig.heapFactor(plan.getValue());
    int scaledHeap = (int)Math.ceil(baseHeap.getPages() * heapFactor);
    System.out.printf("heapFactor=%4.2f, baseHeap=%dK, initHeap=%dK%n",
        heapFactor, baseHeap.getPages()*MemoryConstants.BYTES_IN_PAGE/1024,
        scaledHeap*MemoryConstants.BYTES_IN_PAGE/1024);
    initHeap.setPages(scaledHeap);
    maxHeap.setPages(scaledHeap);
  }

  public static void initOnce() {
    if (!initialized) {
      init("plan=org.mmtk.plan.marksweep.MS","bits=32");
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

  /**
   * @return A mutator context for the current Plan
   */
  public static MutatorContext createMutatorContext() {
    try {
      String prefix = PlanSpecificConfig.planClass(plan.getValue());
      return (MutatorContext)Class.forName(prefix + "Mutator").newInstance();
    } catch (Exception ex) {
      throw new RuntimeException("Could not create Mutator", ex);
    }
  }
  /**
   * @return A mutator context for the current Plan
   */
  public static CollectorContext createCollectorContext() {
    try {
      String prefix = PlanSpecificConfig.planClass(plan.getValue());
      return (CollectorContext)Class.forName(prefix + "Collector").newInstance();
    } catch (Exception ex) {
      throw new RuntimeException("Could not create Collector", ex);
    }
  }

  private static final Object DUMP_STATE_LOCK = new Object();

  public static void dumpStateAndExit(String message) {
    synchronized(DUMP_STATE_LOCK) {
      System.err.print(Thread.currentThread().getName()+": ");
      System.err.println(message);
      new Throwable().printStackTrace();
      throw new RuntimeException();
    }
  }

  private static void checkSpaces() {
    Set<String> expectedSpaces = PlanSpecificConfig.get(plan.getValue()).getExpectedSpaces();
    final Set<String> actualSpaces = new HashSet<String>();
    Space.visitSpaces(new SpaceVisitor() {
      @Override
      public void visit(Space s) {
        actualSpaces.add(s.getName());
      }
    });
    if (!expectedSpaces.equals(actualSpaces)) {
      for (String name : expectedSpaces) {
        if (!actualSpaces.contains(name)) {
          System.err.printf("Expected space %s was not found%n",name);
        }
      }
      for (String name : actualSpaces) {
        if (!expectedSpaces.contains(name)) {
          System.err.printf("Space %s was not expected%n",name);
        }
      }
      throw new AssertionError("Space map does not match expectations");
    }
  }
}
