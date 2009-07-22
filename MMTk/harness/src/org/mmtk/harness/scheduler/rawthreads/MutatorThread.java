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
package org.mmtk.harness.scheduler.rawthreads;

import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.scheduler.Schedulable;

/**
 * The superclass of mutator threads in the raw threads model
 */
class MutatorThread extends RawThread {
  final Env env = new Env();
  final Schedulable code;

  public MutatorThread(Schedulable code, RawThreadModel model) {
    super(model);
    this.code = code;
    setName("Mutator-"+model.nextMutatorId());
  }

  /*
   * Thread.run()
   */
  @Override
  public void run() {
    Trace.trace(Item.SCHEDULER, "%d: initial yield",this.getId());
    // Initial 'yield'
    waitTillCurrent();
    Trace.trace(Item.SCHEDULER, "%d: Env.begin()",this.getId());
    env.begin();
    begin();
    Trace.trace(Item.SCHEDULER, "%d: Running mutator code",this.getId());
    code.execute(env);
    env.end();
    end();
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
      public void uncaughtException(Thread t, Throwable e) {
        env.uncaughtException(t, e);
        end();
      }
    });
  }

  @Override
  protected void end() {
    super.end();
    model.removeMutator(this);
    Trace.trace(Item.SCHEDULER, "%d: mutator thread exiting",this.getId());
  }
}
