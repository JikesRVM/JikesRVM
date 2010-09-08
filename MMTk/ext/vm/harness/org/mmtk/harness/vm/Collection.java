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
import org.mmtk.harness.exception.OutOfMemory;
import org.mmtk.harness.scheduler.Scheduler;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;

import org.vmmagic.pragma.*;

@Uninterruptible
public class Collection extends org.mmtk.vm.Collection {

  /**
   * Prepare a mutator for collection.
   *
   * @param m the mutator to prepare
   */
  @Override
  public void prepareMutator(MutatorContext m) {
    // Nothing to do
  }

  /**
   * Request each mutator flush remembered sets. This method
   * will trigger the flush and then yield until all processors have
   * flushed.
   */
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

  /**
   * Fail with an out of memory error.
   */
  @Override
  public void outOfMemory() {
    throw new OutOfMemory();
  }

  /**
   * Spawn a thread the execute the supplied collector context.
   */
  @Override
  public void spawnCollectorContext(CollectorContext context) {
    Scheduler.scheduleCollector(context);
  }

  /**
   * Stop all mutator threads. This is current intended to be run by a single thread.
   *
   * Fixpoint until there are no threads that we haven't blocked. Fixpoint is needed to
   * catch the (unlikely) case that a thread spawns another thread while we are waiting.
   */
  @Override
  public void stopAllMutators() {
    Scheduler.stopAllMutators();
  }

  /**
   * Resume all mutators blocked for GC.
   */
  @Override
  public void resumeAllMutators() {
    Scheduler.resumeAllMutators();
  }
}
