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

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.plan.CollectorContext;

/**
 * The super-class of collector threads
 */
class CollectorThread extends RawThread {
  protected CollectorContext context;

  /**
   * Create a collector thread, running the 'run'method of collector c
   *
   * @param model
   * @param context
   * @param daemon
   */
  protected CollectorThread(RawThreadModel model, CollectorContext context, boolean daemon) {
    super(model);
    this.context = context;
    setName("Collector-"+model.collectors.size());
    setDaemon(daemon);
    Trace.trace(Item.SCHEDULER, "%d: collector thread %d \"%s\" created (%d total)",
        Thread.currentThread().getId(), getId(), getName(),model.collectors.size());
  }

  /** Create a collector thread, with a new collector */
  public CollectorThread(RawThreadModel model, CollectorContext context) {
    this(model,context,true);
  }

  @Override
  public void run() {
    // Initial 'yield'
    waitTillCurrent();
    context.run();
    model.removeCollector(this);
  }
}
