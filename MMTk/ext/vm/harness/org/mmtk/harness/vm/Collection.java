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
package org.mmtk.harness.vm;

import org.mmtk.harness.Harness;
import org.mmtk.harness.Mutator;
import org.mmtk.harness.Mutators;
import org.mmtk.harness.exception.OutOfMemory;
import org.mmtk.harness.scheduler.Scheduler;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;

import org.vmmagic.pragma.*;

@Uninterruptible
public class Collection extends org.mmtk.vm.Collection {

  @Override
  public void prepareMutator(MutatorContext m) {
    // Nothing to do
  }

  @Override
  public void requestMutatorFlush() {
    Assert.notImplemented();
  }

  /**
   * Possibly yield the current concurrent collector thread. Return
   * true if yielded.
   */
  public boolean yieldpoint() {
    return Mutator.current().gcSafePoint();
  }

  /**
   * Block for the garbage collector.
   *
   * An MMTk class calls this when it is in Mutator context, and the mutator
   * needs to block and wait for a GC to complete.
   */
  @Override
  public void blockForGC() {
    Scheduler.waitForGC();
  }

  /**
   * @return The default number of collector threads to use.
   *
   * In the Harness, this is a command-line option
   */
  @Override
  public int getDefaultThreads() {
    return Harness.collectors.getValue();
  }

  @Override
  public int getActiveThreads() {
    return Mutators.count();
  }

  @Override
  public void outOfMemory() {
    throw new OutOfMemory();
  }

  @Override
  public void spawnCollectorContext(CollectorContext context) {
    Scheduler.scheduleCollector(context);
  }

  @Override
  public void stopAllMutators() {
    Scheduler.stopAllMutators();
  }

  @Override
  public void resumeAllMutators() {
    Scheduler.resumeAllMutators();
  }
}
