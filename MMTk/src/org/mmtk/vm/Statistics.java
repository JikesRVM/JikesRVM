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
package org.mmtk.vm;

import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;


@Uninterruptible
public abstract class Statistics {
  /**
   * Read cycle counter
   */
  public abstract long nanoTime();

  /**
   * Convert nanoseconds to milliseconds
   */
  public abstract double nanosToMillis(long c);

  /**
   * Convert nanoseconds to seconds
   */
  public abstract double nanosToSecs(long c);

  /**
   * Convert milliseconds to nanoseconds
   */
  public abstract long millisToNanos(double t);

  /**
   * Convert seconds to nanoseconds
   */
  public abstract long secsToNanos(double t);

  /**
   * Read the cycle counter
   */
  public abstract long cycles();

  /**
   * Initialize performance events
   */
  @Interruptible
  public abstract void perfEventInit(String events);

  /**
   * Read a performance event value
   */
  public abstract void perfEventRead(int counter, long[] values);
}
