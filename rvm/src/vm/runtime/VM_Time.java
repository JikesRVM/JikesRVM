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

  private static double milliPerCycle = 0.0;

  static public void boot() {
    double start = now();
    long cycleStart = cycles();
    double dur = 0.0; // in milliseconds
    // 0.1 second should be enough to obtain accurate factor but not enough to overflow cycle counter
    while (dur < 0.1) {  
      for (int i=0; i<1000; i++)  // busy-spin
	milliPerCycle += 1.0; 
      dur = 1000.0 * (VM_Time.now() - start);
    }
    long cycles = cycles() - cycleStart;
    if (cycles < 0) VM.sysFail("VM_Time.boot failed due to negative cycle count");
    milliPerCycle = dur / cycles;
  }

  /**
   * Number of processor cycles executed since some undefined epoch.
   */ 
  static public long cycles() {
    // On IA32 we are reading a cycle counter
    // On PPC we are reading the time base register and 
    // the relationship between ticks of the time base register and
    // cycle count is undefined.  
    // Empirically, on some machines, it appears to be 4x.  
    int shift = VM.BuildForPowerPC ? 2 : 0;
    return VM_Magic.getTimeBase() << shift;
  }

  /**
   * Convert a value obtained from VM_Time.cycles to
   * milliSeconds
   * @param c a cycle value obtained via cycles
   * @return c converted to milli seconds
   */
  static public double cyclesToMilli (long c) {
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
