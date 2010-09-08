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
package org.mmtk.harness.scheduler;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;

/**
 * A scheduler policy that
 * @author rgarner
 *
 */
public class YieldEvery extends AbstractPolicy implements Policy {

  private final int frequency;

  private int counter;

  private void resetCounter() {
    counter = frequency;
  }

  public YieldEvery(Thread thread, int frequency) {
    super(thread, "YieldEvery");
    this.frequency = frequency;
    Trace.trace(Item.SCHEDULER, "  yield interval %d", frequency);
  }

  @Override
  public boolean taken() {
    if (counter == 0) {
      resetCounter();
    }
    return --counter == 0;
  }

}
