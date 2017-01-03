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
package org.jikesrvm.runtime;

import org.vmmagic.pragma.Uninterruptible;

/**
 * Primitives from which to build interval and absolute timers.
 */
@Uninterruptible
public class Time {

  /** boot time in milliseconds */
  private static long bootTime;

  public static void boot() {
    bootTime = currentTimeMillis();
  }

  /**
   * Convert a long representing a time in nanoseconds into
   * a double representing the same time in milliseconds.
   * @param c a time in nanoseconds
   * @return c converted to milliseconds
   */
  public static double nanosToMillis(long c) {
    return (c) / 1e6;
  }

  /**
   * This used to return a hardware cycle counter, but the counter on ARM is not accessible in user mode,
   * so it has been altered to use sysNanoTime(). This function is only used in one place,
   * and should not be added to any new places.
   *
   * @return the value of the hardware cycle counter
   */
  public static long cycles() {
    return SysCall.sysCall.sysNanoTime();
  }

  /**
   * Same semantics as java.lang.System.nanoTime();
   * This (or java.lang.System.nanoTime) is the
   * preferred API for VM internal timing functions.
   * @return a monotonic timer value in nanoseconds.
   */
  public static long nanoTime() {
    return SysCall.sysCall.sysNanoTime();
  }

  /**
   * @return current time in milliseconds (epoch Jan 1 1970).
   */
  public static long currentTimeMillis() {
    return SysCall.sysCall.sysCurrentTimeMillis();
  }

  public static double nanosToSecs(long nanos) {
    return (nanos) / 1E9;
  }

  public static long secsToNanos(double secs) {
    return (long)(secs * 1E9);
  }

  public static long bootTime() {
    return bootTime;
  }
}
