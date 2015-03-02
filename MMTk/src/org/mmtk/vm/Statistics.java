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
   * Read cycle counter. This has the same semantics
   * as {@link java.lang.System#nanoTime()}.
   *
   * @return current time in nanoseconds
   */
  public abstract long nanoTime();

  /**
   * Converts nanoseconds to milliseconds
   *
   * @param c time in nanoseconds
   * @return time in milliseconds
   */
  public abstract double nanosToMillis(long c);

  /**
   * Converts nanoseconds to seconds
   *
   * @param c time in nanoseconds
   * @return time in seconds
   */
  public abstract double nanosToSecs(long c);

  /**
   * Converts milliseconds to nanoseconds
   *
   * @param t time in milliseconds
   * @return time in nanoseconds
   */
  public abstract long millisToNanos(double t);

  /**
   * Convert seconds to nanoseconds
   *
   * @param t time in seconds
   * @return time in nanoseconds
   */
  public abstract long secsToNanos(double t);

  /**
   * Read the cycle counter
   * @return number of cycles
   */
  public abstract long cycles();

  /**
   * Initializes performance events.
   *
   * @param events the events to initialize. This is a comma-separated
   *  list of event names.
   */
  @Interruptible
  public abstract void perfEventInit(String events);

  /**
   * Reads a performance event value.
   *
   * @param counter the event's id
   * @param values a buffer that will hold the return values of the
   * read (3 64-bit values).
   */
  public abstract void perfEventRead(int counter, long[] values);
}
