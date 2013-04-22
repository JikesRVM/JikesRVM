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

import org.mmtk.harness.Main;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.plan.CollectorContext;
import org.vmmagic.unboxed.harness.Clock;

class CollectorThread extends JavaThread {
  private static int collectorId = 0;

  private final CollectorContext context;

  private final JavaThreadModel model;

  protected CollectorThread(JavaThreadModel model, CollectorContext context, boolean daemon) {
    this.context = context;
    this.model = model;
    setName("Collector-"+(++collectorId));
    setDaemon(daemon);
    setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        Clock.stop();
        Trace.trace(Item.SCHEDULER, "Catching uncaught exception for thread %s%n%s",
            Thread.currentThread().getName(),
            e.getClass().getCanonicalName());
        e.printStackTrace();
        Main.exitWithFailure();
      }
    });
  }

  CollectorThread(JavaThreadModel model, CollectorContext context) {
    this(model, context,true);
  }

  @Override
  public void run() {
    Trace.trace(Item.SCHEDULER, "CollectorThread.run: in");
    model.setCurrentCollector(context);
    Clock.start();
    context.run();
    Clock.stop();
    model.removeCollector(this);
    Trace.trace(Item.SCHEDULER, "CollectorThread.run: out");
  }
}
