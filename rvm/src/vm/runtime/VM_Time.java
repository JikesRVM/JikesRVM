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
 */
/// uninterrupible so it can be called safely from inside scheduler code
public class VM_Time implements VM_Uninterruptible {
  /**
   * Number of processor cycles executed since some undefined epoch.
   */ 
  static long cycles() {
    //-#if RVM_FOR_POWERPC
    //-#if RVM_FOR_AIX
    // 1 tick --> 4 cycles, see VM_Magic.getTimeBase()
    return VM_Magic.getTimeBase() << 2; 
    //-#else
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return 0;  // currently unimplemented for PPC-Linux
    //-#endif
    //-#endif
    //-#if RVM_FOR_IA32
    // 1 tick --> 1 cycle on IA32
    return VM_Magic.getTimeBase();
    //-#endif
  }

  /**
   * Time in seconds (epoch Jan 1 1970), to nanosecond resolution.
   */ 
  public static double now() {
    //-#if RVM_FOR_POWERPC
    //-#if RVM_FOR_LINUX
    long currentTime = VM_SysCall.call_L_0(VM_BootRecord.the_boot_record.sysGetTimeOfDayIP );
    double time = (double) currentTime / 1000000D;
    //-#else
    double time = VM_Magic.getTime(VM_Processor.getCurrentProcessor());
    //-#endif
    //-#endif
    //-#if RVM_FOR_IA32
    long currentTime = VM_SysCall.call_L_0(VM_BootRecord.the_boot_record.sysGetTimeOfDayIP );
    double time = (double) currentTime / 1000000D;
    //-#endif
    return time;
  }

  /**
   * Time in milliseconds (epoch Jan 1 1970).
   */ 
  public static long currentTimeMillis() {
    long currentTime;
    currentTime = VM_SysCall.call_L_0(VM_BootRecord.the_boot_record.sysGetTimeOfDayIP );
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

  public static int toMins(double time) {
    return (int)(time/60.0);
  }
}
