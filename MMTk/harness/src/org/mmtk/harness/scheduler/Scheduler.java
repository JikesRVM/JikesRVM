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
import org.mmtk.harness.Mutator;
import org.mmtk.harness.scheduler.javathreads.JavaThreads;
import org.mmtk.harness.scheduler.rawthreads.RawThreads;
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
    JAVA,
    DETERMINISTIC;

    /** The values of this enum, converted to strings */
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
    FIXED,
    RANDOM,
    NEVER;

    /** The values of this enum, converted to strings */
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
        return new JavaThreads();
      case DETERMINISTIC:
        return new RawThreads();
      default:
        throw new RuntimeException("Unknown thread model");
    }
  }

  private static final ThreadModel model = selectedThreadModel();

  /**
   * Yield policy factory - return an instance of the the command-line
   * selected yield policy
   */
  public static Policy yieldPolicy(Thread thread) {
    switch (Harness.policy.policy()) {
      case FIXED:
        int yieldInterval = Harness.yieldInterval.getValue();
        if (yieldInterval == 1) {
          return new YieldAlways(thread);
        } else {
          return new YieldEvery(thread,yieldInterval);
        }
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
   * The current Log object.
   * @return
   */
  public static Log currentLog() {
    return model.currentLog();
  }

  /**
   * The current mutator object (if the current thread is a Mutator)
   * @return
   */
  public static Mutator currentMutator() {
    return model.currentMutator();
  }

  /* schedule GC */

  public static void triggerGC(int why) {
    model.triggerGC(why);
  }

  public static void exitGC() {
    model.exitGC();
  }

  public static void waitForGCStart() {
    model.waitForGCStart();
  }

  public static int getTriggerReason() {
    return model.getTriggerReason();
  }

  /**
   * Are there no threads currently in GC?
   */
  public static boolean noThreadsInGC() {
    return model.noThreadsInGC();
  }

  /**
   * Has a GC been triggered?
   */
  public static boolean gcTriggered() {
    return model.gcTriggered();
  }

  public static int rendezvous(int where) {
    return model.rendezvous(where);
  }

  public static Collector currentCollector() {
    return model.currentCollector();
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
   * An MMTk lock
   */
  public static Lock newLock(String name) {
    return model.newLock(name);
  }
}
