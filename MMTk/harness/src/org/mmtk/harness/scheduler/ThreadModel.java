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
import org.mmtk.harness.Mutator;
import org.mmtk.utility.Log;

public abstract class ThreadModel {

  /** The number of collectors executing GC */
  protected int inGC;
  /**
   * The number of mutators currently executing in the system.
   */
  protected int activeMutators;
  /**
   * The number of mutators waiting for a collection to proceed.
   */
  protected int mutatorsWaitingForGC;
  /** The trigger for this GC */
  protected int triggerReason;

  protected void initCollectors() { }

  protected abstract void yield();

  protected abstract void scheduleMutator(Schedulable method);

  protected abstract void scheduleCollector();

  protected abstract Log currentLog();

  protected abstract Mutator currentMutator();

  /* schedule GC */

  protected abstract void triggerGC(int why);

  protected abstract void exitGC();

  protected abstract void waitForGCStart();

  public boolean noThreadsInGC() {
    return inGC == 0;
  }

  public boolean gcTriggered() {
    return inGC > 0;
  }

  public abstract int rendezvous(int where);

  public abstract Collector currentCollector();

  public abstract void waitForGC();

  public int getTriggerReason() {
    return triggerReason;
  }

  public abstract void schedule();

  /**
   * An MMTk lock
   */
  public abstract Lock newLock(String name);

}
