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
 * The super-class of collector-context threads.  These are unit tests
 * that need to run in collector context.
 */
class CollectorContextThread extends CollectorThread {
  private final Schedulable code;

  public CollectorContextThread(RawThreadModel model,Schedulable code) {
    super(model,false);
    this.code = code;
  }

  @Override
  public void run() {
    // Initial 'yield'
    waitTillCurrent();
    Trace.trace(Item.SCHEDULER, "%d: Collector context waiting for GC", getId());
    model.waitForGCStart();
    Trace.trace(Item.SCHEDULER, "%d: Starting collector context", getId());
    code.execute(new Env());
    model.removeCollector(this);
    Trace.trace(Item.SCHEDULER, "%d: Collector context complete", getId());
  }
}
