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
package org.mmtk.harness.vm;

import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class Statistics extends org.mmtk.vm.Statistics {
  @Override
  public long nanoTime() {
    return System.nanoTime();
  }

  @Override
  public double nanosToMillis(long c) {
    return (c) / 1e6;
  }

  @Override
  public double nanosToSecs(long c) {
    return (c) / 1e9;
  }

  @Override
  public long millisToNanos(double t) {
    return (long)(t * 1e6);
  }

  @Override
  public long secsToNanos(double t) {
    return (long)(t * 1e9);
  }

  @Override
  public long cycles() {
    return System.nanoTime();
  }

  /**
   * Read (a set of) performance counters
   */
  @Override
  public void perfEventRead(int x,long[] y) {
    throw new UnsupportedOperationException("Statistics#perfEventRead(): Not Implemented");
  }

  /**
   * Initialize performance counters
   *
   * @param metric An string identifying the metrics being read
   */
  @Override
  public void perfEventInit(String events) {
    if (events.equals(""))
      return;
    throw new UnsupportedOperationException("Statistics#perfEventInit("+events+"): Not Implemented");
  }
}
