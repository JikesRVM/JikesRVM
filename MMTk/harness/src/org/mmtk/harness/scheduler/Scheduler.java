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
package org.mmtk.harness.scheduler;

import org.mmtk.harness.Harness;
import org.mmtk.harness.Mutator;
import org.mmtk.harness.scheduler.javathreads.JavaThreadModel;
import org.mmtk.harness.scheduler.rawthreads.RawThreadModel;
import org.mmtk.plan.CollectorContext;
import org.mmtk.utility.Log;
import org.mmtk.vm.Monitor;
import org.vmmagic.unboxed.harness.Clock;

/**
 * Facade class for the command-line selectable threading models available
 * in the MMTk harness.
 */
public class Scheduler {

  /**
   * Possible threading models
   */
  public enum Model {
    /** Schedule using the Java thread scheduler */
    JAVA,
    /** Schedule in the harness using deterministic algorithms */
    DETERMINISTIC;

    /** @return The values of this enum, converted to strings */
    public static String[] valueNames() {
      String[] result = new String[Scheduler.Model.values().length];
      for (int i=0; i < Scheduler.Model.values().length; i++) {
        result[i] = Scheduler.Model.values()[i].toString();
      }
      return result;
    }
  }

  /**
   * Possible thread-scheduling policies
   */
  public enum SchedPolicy {
    /** Reschedule every nth yield point */
    FIXED,
    /** Reschedule on a pseudo-random sequence of intervals */
    RANDOM,
    /** Only reschedule when the thread is blocked */
    NEVER;

    /** @return The values of this enum, converted to strings */
    public static String[] valueNames() {
      String[] result = new String[Scheduler.SchedPolicy.values().length];
      for (int i=0; i < Scheduler.SchedPolicy.values().length; i++) {
        result[i] = Scheduler.SchedPolicy.values()[i].toString();
      }
      return result;
    }
  }

  /**
   * Thread Model factory - returns the thread model selected by user options.
   */
  public static ThreadModel newThreadModel(Model model) {
    switch (model) {
      case JAVA:
        return new JavaThreadModel();
      case DETERMINISTIC:
        return new RawThreadModel();
      default:
        throw new RuntimeException("Unknown thread model");
    }
  }

  private static ThreadModel model;

  /**
   * Yield policy factory - return an instance of the the command-line
   * selected yield policy
   * @param thread The Java thread
   * @return A new policy for the given thread
   */
  public static Policy yieldPolicy(Thread thread) {
    switch (Harness.policy.policy()) {
      case FIXED:
        int yieldInterval = Harness.yieldInterval.getValue();
        if (yieldInterval == 1) {
          return new YieldAlways(thread);
        }
        return new YieldEvery(thread,yieldInterval);
      case RANDOM:
        return new YieldRandomly(thread,
            Harness.randomPolicySeed.getValue(),
            Harness.randomPolicyLength.getValue(),
            Harness.randomPolicyMin.getValue(),
            Harness.randomPolicyMax.getValue());
      case NEVER:
        return new YieldNever(thread);
      default:
        throw new RuntimeException("Unknown scheduler policy");
    }
  }

  /**
   * Initialization
   */
  public static void init() {
    setThreadModel(Harness.scheduler.model());
  }

  /**
   * Set the thread model
   * @param modelType
   */
  public static void setThreadModel(Scheduler.Model modelType) {
    model = newThreadModel(modelType);
  }

  /**
   * Advance collector threads to their initial 'wait for collection' barrier
   */
  public static void initCollectors() {
    model.initCollectors();
  }

  /**
   * A yield-point.
   */
  public static void yield() {
    model.yield();
  }

  /**
   * Create and start a new Mutator thread
   * @param item The executable code to run in this thread
   */
  public static void scheduleMutator(Schedulable item) {
    model.scheduleMutator(item);
  }

  /**
   * Create and start a new collector thread with the given collector context
   */
  public static void scheduleCollector(CollectorContext context) {
    model.scheduleCollector(context);
  }

  /**
   * Create and start a new collector thread running a particular code
   * sequence.  Used to schedule unit tests in collector context.
   *
   * @param item The schedulable object
   * @return A java thread for the item
   */
  public static Thread scheduleCollectorContext(CollectorContext item) {
    return model.scheduleCollectorContext(item);
  }

  /**
   * @return The current Log object.
   */
  public static Log currentLog() {
    return ((MMTkThread)Thread.currentThread()).getLog();
  }

  /**
   * @return The current mutator object (if the current thread is a Mutator)
   */
  public static Mutator currentMutator() {
    return model.currentMutator();
  }

  /**
   * @return The current collector object (if the current thread is a Collector)
   */
  public static CollectorContext currentCollector() {
    return model.currentCollector();
  }

  /**
   * @return Has a GC been triggered?
   */
  public static boolean gcTriggered() {
    return model.gcTriggered();
  }

  /**
   * Wait at a barrier in user code
   * @param name
   * @param expected
   * @return
   */
  public static int mutatorRendezvous(String name, int expected) {
    try {
      Clock.stop();
      return model.mutatorRendezvous(name,expected);
    } finally {
      Clock.start();
    }
  }

  /**
   * Cause the current thread to wait for a triggered GC to proceed.
   */
  public static void waitForGC() {
    model.waitForGC();
  }

  /**
   * Schedule the threads
   */
  public static void schedule() {
    model.schedule();
  }

  /**
   * Schedule the GC threads as though a GC had been triggered
   * Used to run unit tests that must run in collector context.
   */
  public static void scheduleGcThreads() {
    model.scheduleGcThreads();
  }

  /**
   * An MMTk lock - a factory method.
   * @param name The name of the lock
   * @return The newly created lock
   */
  public static Lock newLock(String name) {
    try {
      Clock.stop();
      return model.newLock(name);
    } finally {
      Clock.start();
    }
  }

  /**
   * Model-specific MMTk Monitor factory
   * @return A new monitor of the appropriate class
   */
  public static Monitor newMonitor(String name) {
    try {
      Clock.stop();
      return model.newMonitor(name);
    } finally {
      Clock.start();
    }
  }

  /**
   * Stop all mutator threads. This is current intended to be run by a single thread.
   */
  public static void stopAllMutators() {
    try {
      Clock.stop();
      model.stopAllMutators();
    } finally {
      Clock.start();
    }
  }

  /**
   * Resume all mutators blocked for GC.
   */
  public static void resumeAllMutators() {
    try {
      Clock.stop();
      model.resumeAllMutators();
    } finally {
      Clock.start();
    }
  }

  /** @return {@code true} if the current thread is a mutator thread */
  public static boolean isMutator() {
    return model.isMutator();
  }

  /** @return {@code true} if the current thread is a mutator thread */
  public static boolean isCollector() {
    return model.isCollector();
  }
}
