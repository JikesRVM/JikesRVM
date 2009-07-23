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
import org.mmtk.harness.scheduler.Schedulable;

final class CollectorContextThread extends CollectorThread {
  final Schedulable code;
  private final JavaThreadModel model;

  CollectorContextThread(JavaThreadModel model, Schedulable code) {
    super(false);
    this.model = model;
    this.code = code;
  }

  @Override
  public void run() {
    JavaThreadModel.setCurrentCollector(collector);
    model.waitForGCStart();
    code.execute(new Env());
    model.removeCollector(this);
    model.exitGC();
  }

}
