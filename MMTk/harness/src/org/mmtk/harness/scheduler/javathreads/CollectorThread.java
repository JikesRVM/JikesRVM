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

import org.mmtk.harness.Collector;
import org.mmtk.harness.Main;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;

class CollectorThread extends JavaThread {
  private static int collectorId = 0;

  protected final Collector collector;

  protected CollectorThread(boolean daemon) {
    this.collector = new Collector();
    setName("Collector-"+(++collectorId));
    setDaemon(daemon);
  }

  CollectorThread() {
    this(true);
    setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      public void uncaughtException(Thread t, Throwable e) {
        Trace.trace(Item.SCHEDULER, "Catching uncaught exception for thread %s%n%s",
            Thread.currentThread().getName(),
            e.getClass().getCanonicalName());
        e.printStackTrace();
        Main.exitWithFailure();
      }
    });
  }

  @Override
  public void run() {
    JavaThreadModel.setCurrentCollector(collector);
    collector.run();
  }

}
