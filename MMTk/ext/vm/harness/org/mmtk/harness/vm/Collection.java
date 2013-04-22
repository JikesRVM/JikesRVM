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
import org.vmmagic.unboxed.harness.Clock;

@Uninterruptible
public class Collection extends org.mmtk.vm.Collection {

  private static int gcCount = 0;

  public static int getGcCount() {
    return gcCount;
  }

  @Override
  public void prepareMutator(MutatorContext m) {
    // Nothing to do
  }

  @Override
  public void requestMutatorFlush() {
    Mutator.current().getContext().flush();
    Assert.notImplemented();
  }

  /**
   * Possibly yield the current concurrent collector thread. Return
   * {@code true} if yielded.
   */
  public boolean yieldpoint() {
    return Mutator.current().gcSafePoint();
  }

  /**
   * {@inheritDoc}
   * <p>
   * An MMTk class calls this when it is in Mutator context, and the mutator
   * needs to block and wait for a GC to complete.
   */
  @Override
  public void blockForGC() {
    gcCount++;
    Clock.stop();
    Scheduler.waitForGC();
    Clock.start();
  }

  /**
   * @return The default number of collector threads to use.
   *
   * In the Harness, this is a command-line option.
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
    Clock.stop();
    Scheduler.scheduleCollector(context);
    Clock.start();
  }

  @Override
  public void stopAllMutators() {
    Clock.stop();
    Scheduler.stopAllMutators();
    Clock.start();
  }

  @Override
  public void resumeAllMutators() {
    Clock.stop();
    Scheduler.resumeAllMutators();
    Clock.start();
  }
}
