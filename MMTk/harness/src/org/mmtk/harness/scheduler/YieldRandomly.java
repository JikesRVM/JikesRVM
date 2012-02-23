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

import java.util.Random;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;

public final class YieldRandomly extends AbstractPolicy implements Policy {

  private int index = 0;
  private int counter;

  private static Random rng = null;

  private final int[] schedule;

  private void resetCounter() {
    counter = schedule[index++];
    index = index % schedule.length;
  }

  public YieldRandomly(Thread thread, int seed, int length, int min, int max) {
    super(thread, "YieldRandomly");
    this.schedule = new int[length];

    synchronized(YieldRandomly.class) {
      if (rng == null) {
        rng = new Random(seed);
      }
    }
    for (int i=0; i < length; i++) {
      schedule[i] = rng.nextInt(max-min+1)+min;
    }
    Trace.trace(Item.SCHEDULER, "  yield pattern %s", formatPolicy());
  }

  @Override
  public boolean taken() {
    if (counter == 0) {
      resetCounter();
    }
    return --counter == 0;
  }

  @Override
  protected String formatPolicy() {
    StringBuilder result = new StringBuilder();
    for (int i=0; i < schedule.length; i++) {
      result.append(schedule[i]);
      if (i < schedule.length-1) {
        result.append(",");
      }
    }
    return result.toString();
  }
}
