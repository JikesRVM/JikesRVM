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

import org.mmtk.harness.Collector;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;

/**
 * The super-class of collector threads
 */
class CollectorThread extends RawThread {
  protected final Collector c;

  /**
   * Create a collector thread, running the 'run'method of collector c
   *
   * @param model
   * @param c
   * @param daemon
   */
  private CollectorThread(RawThreadModel model,Collector c, boolean daemon) {
    super(model);
    this.c = c;
    setName("Collector-"+model.collectors.size());
    model.collectors.add(this);
    setDaemon(daemon);
    Trace.trace(Item.SCHEDULER, "%d: collector thread %d \"%s\" created (%d total)",
        Thread.currentThread().getId(), getId(), getName(),model.collectors.size());
  }

  /** Create a collector thread, with a new collector */
  protected CollectorThread(RawThreadModel model, boolean daemon) {
    this(model,new Collector(), daemon);
  }

  /** Create a collector thread, with a new collector */
  public CollectorThread(RawThreadModel model) {
    this(model,true);
  }

  @Override
  public void run() {
    // Initial 'yield'
    waitTillCurrent();
    c.run();
    assert false : "Collector threads should never exit";
  }
}
