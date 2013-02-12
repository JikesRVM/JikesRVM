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
package org.vmmagic.unboxed.harness;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A 'clock' that counts memory accesses
 */
public class Clock {

  /** Set to {@code true} to enable the clock - it's expensive */
  public static final boolean ENABLE_CLOCK = false;

  /** The clock */
  private static final AtomicLong clock = new AtomicLong(0);

  static void tick() {
    if (ENABLE_CLOCK) clock.incrementAndGet();
  }

  /**
   * @return The current value of the clock
   */
  public static long read() {
    return clock.get();
  }

}
