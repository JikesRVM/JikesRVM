/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;

/**
 * Primitives from which to build interval and absolute timers.
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public class VM_Time implements Uninterruptible {

  /**
   * Conversion factor from cycles to time in milliseconds
   */
  private static double milliPerCycle = 0;

  /**
   * Conversion factor from cycles to time in seconds
   */
  private static double secPerCycle = 0;

  private static long bootNow;
  private static long bootCycles;

  static void bootStageOne() {
    bootNow = currentTimeMicros();
    bootCycles = cycles();
  }

  static void bootStageTwo() {
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
    milliPerCycle = (((double)dur) / ((double)cycles)) / 1000;
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
    return (long)(t / milliPerCycle);
  }

  /**
   * Convert a time value in seconds to cycles.
   * @param t a time in seconds
   * @return the corresponding number of cycles
   */
  public static long secsToCycles(double t) {
    if (VM.VerifyAssertions) VM._assert(secPerCycle != 0);
    return (long)(t / secPerCycle);
  }

  /**
   * Time in microseconds (epoch Jan 1 1970).
   */ 
  public static long currentTimeMicros() {
    return VM_SysCall.sysGetTimeOfDay();
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
