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
package org.mmtk.harness.scheduler.javathreads;

import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.scheduler.Schedulable;

/**
 * A Mutator thread in the java threading model.
 */
final class MutatorThread extends JavaThread {
  static int mutatorId = 0;

  private final Schedulable code;
  final Env env = new Env();
  private final JavaThreadModel model;

  MutatorThread(JavaThreadModel model, Schedulable code) {
    this.model = model;
    this.code = code;
    setName("Mutator-"+(++mutatorId));
    Trace.trace(Item.SCHEDULER, "MutatorThread created");
  }

  @Override
  public void run() {
    Trace.trace(Item.SCHEDULER, "Env.begin()");
    env.begin();
    begin();
    Trace.trace(Item.SCHEDULER, "Running mutator code");
    code.execute(env);
    env.end();
    endMutator();
  }

  /**
   * Mark a mutator as currently active. If a GC is currently in process we must
   * wait for it to finish.
   */
  public void begin() {
    // Trap uncaught exceptions
    Trace.trace(Item.SCHEDULER, "Setting uncaught exception handler for thread %s",
        Thread.currentThread().getName());
    Thread.currentThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        Trace.trace(Item.SCHEDULER, "Catching uncaught exception for thread %s%n%s",
            Thread.currentThread().getName(),
            e.getClass().getCanonicalName());
        env.uncaughtException(t, e);
      }
    });
    model.joinMutatorPool();
  }

  private void endMutator() {
    model.leaveMutatorPool(this);
  }

}
