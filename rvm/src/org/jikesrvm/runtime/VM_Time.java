/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.runtime;

import org.jikesrvm.VM;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Primitives from which to build interval and absolute timers.
 */
@Uninterruptible
public class VM_Time {
  /* millisecond: 10^-3 seconds
   * microsecond: 10^-6 seconds
   * nanosecond:  10^-9 seconds
   */
  /**
   * Conversion factor from cycles to time in milliseconds
   */
  private static double milliPerCycle = 0;
  /**
   * Conversion factor from cycles to time in milliseconds
   */
  private static double nanoPerCycle = 0;

  /**
   * Conversion factor from cycles to time in seconds
   */
  private static double secPerCycle = 0;

  private static long bootNow;
  private static long bootCycles;

  public static void bootStageOne() {
    bootNow = currentTimeMicros();
    bootCycles = cycles();
  }

  public static void bootStageTwo() {
    long endNow = currentTimeMicros();
    long endCycles = cycles();
    long dur = endNow - bootNow;

    // insist on getting at least 0.05 seconds (ie 50,000 microseconds)
    // between bootStageOne and bootStage2 to ensure reasonable accuracy
    while (dur < 50000) {
      dur = currentTimeMicros() - bootNow;
      endCycles = cycles();
    }
    long cycles = endCycles - bootCycles;
    if (cycles < 0) VM.sysFail("VM_Time.boot failed due to negative cycle count");
    milliPerCycle = (((double) dur) / ((double) cycles)) / 1000;
    nanoPerCycle = (((double) dur) / ((double) cycles)) * 1000;
    secPerCycle = milliPerCycle / 1000;
  }

  /**
   * Read value of cycle counter or time base register.
   * The semantics of this value are platform dependent.
   * @return the value read from cycle counter or time base register.
   */
  public static long cycles() {
    // On IA32 we are reading a cycle counter.
    // On PowerPC we are reading the time base register.
    // The relationship between ticks of the time base register and
    // cycle count is architecturally undefined.
    // See PPC architecture book for more details.
    return VM_Magic.getTimeBase();
  }

  /**
   * Convert a value in the units of used by {@link #cycles()}
   * to time in milliSeconds.
   * @param c a real time clock value
   * @return c converted to milli seconds
   */
  public static double cyclesToMillis(long c) {
    if (VM.VerifyAssertions) VM._assert(milliPerCycle != 0);
    return c * milliPerCycle;
  }

  /**
   * Convert a value in the units of used by {@link #cycles()}
   * to time in seconds.
   * @param c a real time clock value
   * @return c converted to milli seconds
   */
  public static double cyclesToSecs(long c) {
    if (VM.VerifyAssertions) VM._assert(secPerCycle != 0);
    return c * secPerCycle;
  }

  /**
   * Convert a time value in milliSeconds to cycles.
   * @param t a time in milliseconds
   * @return the corresponding number of cycles
   */
  public static long millisToCycles(double t) {
    if (VM.VerifyAssertions) VM._assert(milliPerCycle != 0);
    return (long) (t / milliPerCycle);
  }

  /**
   * Convert a time value in nanoSeconds to cycles.
   * @param t a time in milliseconds
   * @return the corresponding number of cycles
   */
  public static long nanosToCycles(double t) {
    if (VM.VerifyAssertions) VM._assert(nanoPerCycle != 0);
    return (long) (t / nanoPerCycle);
  }

  /**
   * Convert a time value in seconds to cycles.
   * @param t a time in seconds
   * @return the corresponding number of cycles
   */
  public static long secsToCycles(double t) {
    if (VM.VerifyAssertions) VM._assert(secPerCycle != 0);
    return (long) (t / secPerCycle);
  }

  /**
   * Time in microseconds (epoch Jan 1 1970).
   */
  public static long currentTimeMicros() {
    return VM_SysCall.sysCall.sysGetTimeOfDay();
  }

  /**
   * Time in milliseconds (epoch Jan 1 1970).
   */
  public static long currentTimeMillis() {
    return currentTimeMicros() / 1000;
  }

  /**
   * Time in seconds (epoch Jan 1 1970).
   */
  public static long currentTimeSecs() {
    return currentTimeMicros() / 1000000;
  }
}
