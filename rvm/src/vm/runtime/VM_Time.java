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
   * Conversion factor from realTimeClock to time in milliseconds
   */
  private static double milliPerCycle = 0.0;

  /**
   * Sets milliPerCycle, the conversion factor between a tick of the 
   * realTimeClock and time in milliseconds. 
   */
  static void boot() {
    double start = now();
    long clockTickStart = realTimeClock();
    double dur = 0.0; // in milliseconds
    // 0.1 second should be enough to obtain accurate factor but not enough to overflow cycle counter
    while (dur < 0.1) {  
      for (int i=0; i<1000; i++)  // busy-spin
	milliPerCycle += 1.0; 
      dur = 1000.0 * (VM_Time.now() - start);
    }
    long clockTicks = realTimeClock() - clockTickStart;
    if (clockTicks < 0) VM.sysFail("VM_Time.boot failed due to negative cycle count");
    milliPerCycle = dur / (double)clockTicks;
  }

  /**
   * Read value of cycle counter or real time clock.
   * The semantics of this value are platform dependent.
   * @return the value read from the real time clock.
   */ 
  public static long realTimeClock() {
    // On IA32 we are reading a cycle counter.
    // On PowerPC we are reading the time base register.
    // The relationship between ticks of the time base register and
    // cycle count is architecturally undefined.  
    // See PPC architecture book for more details.
    return VM_Magic.getTimeBase();
  }

  /**
   * Convert a value in the units of used by realTimeClock
   * to time in milliSeconds.
   * @param c a real time clock value
   * @return c converted to milli seconds
   */
  public static double realTimeClockToMilliseconds(long c) {
    return c * milliPerCycle;
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
   * Time in milliseconds (epoch Jan 1 1970).
   */ 
  public static long currentTimeMillis() {
    long currentTime;
    currentTime = VM_SysCall.sysGetTimeOfDay();
    currentTime /= 1000;
    return currentTime;
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
