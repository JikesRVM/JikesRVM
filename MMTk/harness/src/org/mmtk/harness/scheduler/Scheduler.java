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
package org.mmtk.harness.scheduler;

import org.mmtk.harness.Collector;
import org.mmtk.harness.Harness;
import org.mmtk.harness.MMTkThread;
import org.mmtk.harness.Mutator;
import org.mmtk.harness.scheduler.javathreads.JavaThreadModel;
import org.mmtk.harness.scheduler.rawthreads.RawThreadModel;
import org.mmtk.utility.Log;

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
  private static ThreadModel selectedThreadModel() {
    switch (Harness.scheduler.model()) {
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
    model = selectedThreadModel();
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
   * Create and start a new collector thread
   */
  public static void scheduleCollector() {
    model.scheduleCollector();
  }

  /**
   * Create and start a new collector thread running a particular code
   * sequence.  Used to schedule unit tests in collector context.
   * @param item The schedulable object
   * @return A java thread for the item
   */
  public static Thread scheduleCollector(Schedulable item) {
    return model.scheduleCollector(item);
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
  public static Collector currentCollector() {
    return model.currentCollector();
  }

  /* schedule GC */

  /**
   * Request a GC.  Once requested, mutator threads block at
   * 'waitForGC' until a collection is performed.
   * @param why Reason code
   */
  public static void triggerGC(int why) {
    model.triggerGC(why);
  }

  /**
   * A collector thread informs the scheduler that it has completed
   * its GC work by calling this.
   */
  public static void exitGC() {
    model.exitGC();
  }

  /**
   * Collector threads call this method to wait for a GC to be triggered.
   */
  public static void waitForGCStart() {
    model.waitForGCStart();
  }

  /**
   * @see #triggerGC(int)
   * @return Why was the current GC triggered ?
   */
  public static int getTriggerReason() {
    return model.getTriggerReason();
  }

  /**
   * @return Are there no threads currently in GC?
   */
  public static boolean noThreadsInGC() {
    return model.noThreadsInGC();
  }

  /**
   * @return Has a GC been triggered?
   */
  public static boolean gcTriggered() {
    return model.gcTriggered();
  }

  /**
   * Collector thread synchronization barrier
   * @param where Rendezvous ID
   * @return The order of arrival at the barrier
   */
  public static int rendezvous(int where) {
    return model.rendezvous(where);
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
    return model.newLock(name);
  }
}
