/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Primitives from which to build interval and absolute timers.
 * 
 * @author Bowen Alpern
 * @author Derek Lieber
 * @modified Dave Grove
 */
public class VM_Time implements VM_Uninterruptible {

  /**
   * Conversion factor from cycles to time in milliseconds
   */
  private static double milliPerCycle = 0;

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
   * Convert a time value in milliSeconds to cycles.
   * @param t a time in milliseconds
   * @return the corresponding number of cycles
   */
  public static long millisToCycles(double t) {
    if (VM.VerifyAssertions) VM._assert(milliPerCycle != 0);
    return (long)(t / milliPerCycle);
  }

  /**
   * Time in seconds (epoch Jan 1 1970), to nanosecond resolution.
   */
  public static double now() {
    long currentTime = VM_SysCall.sysGetTimeOfDay();
    double time = ((double) currentTime) / 1000000D;
    return time;
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

  /**
   * Scale a double (presumably representing the deltas/sums of VM.now values)
   * by a 1000000 and convert to an int so it can be safely printed 
   * with VM.sysWrite
   * even when GC and/or dynamic class loading is disabled
   */
  public static int toMicroSecs(double time) {
    return (int)(time*1000000.0);
  }

  /**
   * Scale a double (presumably representing the deltas/sums of VM.now values)
   * by a 1000 and convert to an int so it can be safely printed with 
   * VM.sysWrite
   * even when GC and/or dynamic class loading is disabled
   */
  public static int toMilliSecs(double time) {
    return (int)(time*1000.0);
  }

  // A little silly, but fills out the interface....
  public static int toSecs(double time) {
    return (int)time;
  }

  /**
   * Scale a double (presumably representing the deltas/sums of VM.now values)
   * to a time in minutes.
   */
  public static int toMins(double time) {
    return (int)(time/60.0);
  }
}
