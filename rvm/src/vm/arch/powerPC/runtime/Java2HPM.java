/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * This class provides a jni interface to access the PowerPC
 * hardware performance monitors.
 * Trampoline code to hpm.c methods.
 *
 * @author Peter Sweeney
 * creation date 6/27/2001
 */
public class Java2HPM 
{
  private static final int debug = 0;

  static final int OK_CODE = 0;

  // native methods
  public static native int    init();
  public static native int    setEvent( int e1, int e2, int e3, int e4);
  public static native int    setEventX(int e5, int e6, int e7, int e8);
  public static native int    setModeUser();
  public static native int    setModeKernel();
  public static native int    setModeBoth();
  public static native int    setProgramMyThread();
  public static native int    setProgramMyGroup();
  public static native int    deleteProgramMyThread();
  public static native int    deleteProgramMyGroup();
  public static native int    getProgramMyThread();
  public static native int    getProgramMyGroup();
  public static native int    startMyThread();
  public static native int    startMyGroup();
  public static native int    stopMyThread();
  public static native int    stopMyGroup();
  public static native int    resetMyThread();
  public static native int    resetMyGroup();
  public static native int    getMyThread();
  public static native int    getMyGroup();
  public static native int    getNumberOfCounters();
  public static native long   getCounterMyThread(int counter);
  public static native long   getCounterMyGroup( int counter);
  public static native void   listAllEvents();
  public static native void   listSelectedEvents();
  public static native int    getEventId(       int counter);
  public static native String getEventShortName(int counter);
  public static native int    printMyThread(int processorId);
  public static native int    printMyGroup( int processorId);
  public static native int    test();
  public static native String getProcessorName();
  public static native int    isPower4();
  public static native int    isPower3();
  public static native int    isPower3II();
  public static native int    isRS64III();
  public static native int    is604e();

  /**
   * Where to find native method implementations
   * Constraint: must call before any call to a native method.
   */
  static {
    if (VM.BuildForHPM) {
      if(debug>=1)VM.sysWrite("Java2HPM static initializer calling System.loadLibrary(\"Java2HPM\")\n");
      try {
	System.loadLibrary("Java2HPM");
      } catch (Exception e) {
	VM.sysWriteln("\nJava2HPM.<init>() Exception with System.loadLibrary(Java2HPM)\n");
	VM.shutdown(VM.exitStatusHPMTrouble);
      }
      if(debug>=1)VM.sysWrite("Java2HPM static initializer called System.loadLibrary(\"Java2HPM\")\n");
    }
  }
/**************************************************************************/    

/**************************************************************************/    
  /**
   * Compute costs to access hpm subsystem.
   */
  static private final boolean JNI = true;

  static void computeCostsToAccessHPM()
  {
    // Performance
    if (VM.BuildForHPM && VM_HardwarePerformanceMonitors.enabled()) {
      //-#if RVM_WITH_HPM
      int TIMES = 10000000;
      int get_COUNTER = 3;
      int cycle_COUNTER = 4;

      VM.sysWriteln("\n\nJava2HPM.computeCostsToAccessHPM()");
      /*
      String lib_path = System.getProperty("java.library.path");
      if (lib_path != null) {
	VM.sysWrite(lib_path);VM.sysWrite("!\n");
      } else {
	VM.sysWrite(" System.getProperty(\"java.library.path\") returned null!\n");
      }
      */
      // dump selected events
      Java2HPM.listSelectedEvents();

      VM.sysWrite(" Access HPM through JNI\n");
      accessTest(      TIMES, cycle_COUNTER,               JNI);
      accessTest(      TIMES, cycle_COUNTER,               JNI);
      accessGetCounter(TIMES, cycle_COUNTER, get_COUNTER,  JNI);
      accessGetCounter(TIMES, cycle_COUNTER, get_COUNTER,  JNI);
      
      accessTest(      TIMES, cycle_COUNTER,               JNI);
      accessGetCounter(TIMES, cycle_COUNTER, get_COUNTER,  JNI);

      VM.sysWrite(" Access HPM through sysCalls\n");
      accessTest(      TIMES, cycle_COUNTER,              !JNI);
      accessTest(      TIMES, cycle_COUNTER,              !JNI);
      accessGetCounter(TIMES, cycle_COUNTER, get_COUNTER, !JNI);
      accessGetCounter(TIMES, cycle_COUNTER, get_COUNTER, !JNI);
      
      accessTest(      TIMES, cycle_COUNTER,              !JNI);
      accessGetCounter(TIMES, cycle_COUNTER, get_COUNTER, !JNI);
      //-#endif
    }
  }
  /*
   * What is the cost to access HPM counters?
   * Access C layer.
   * ASSUMPTION: reset counters and get counter calls amortized away.
   *
   * @param iterations    number of iterations
   * @param cycle_counter HPM counter that counts cycles
   * @param jni           use JNI call if true.
   */
  private static void accessTest(int iterations, int cycle_counter, boolean jni) 
  {
    //-#if RVM_WITH_HPM

    long start = VM_Time.cycles();
    if (jni == JNI) {
      Java2HPM.resetMyThread();
      Java2HPM.startMyThread();
    } else {
      VM_SysCall.sysHPMresetMyThread();
      VM_SysCall.sysHPMstartMyThread();
    }

    for (int i = 0; i < iterations; i++) {	
      if (jni == JNI) {
	Java2HPM.test();
      } else {
	VM_SysCall.sysHPMtest();
      }
    }

    long cycles;
    if (jni == JNI) {
      cycles = Java2HPM.getCounterMyThread(cycle_counter);
    } else {
      cycles = VM_SysCall.sysHPMgetCounterMyThread(cycle_counter);
    }

    long end = VM_Time.cycles();

    // time in milliseconds
    double time          = VM_Time.cyclesToMillis(end - start);
    VM.sysWriteln("\ttime "+time);
    // time in nano seconds 
    double averageTime   = sixDigitDecimal((time / (double) iterations)*1000000);
    double averageCycles = ((cycles*100)/iterations)  / (double) 100;

    if (jni == JNI) {
      VM.sysWrite("   JNI     accessTest(      ");
    } else {
      VM.sysWrite("   sysCall accessTest(      ");
    }
    VM.sysWriteInt(iterations);VM.sysWrite(",",cycle_counter);
    VM.sysWrite(")    time: total ",time);VM.sysWrite("ms (",averageTime);
    VM.sysWrite("ns); cycles: ");VM.sysWriteLong(cycles);
    VM.sysWrite("(",averageCycles);VM.sysWriteln(")");
    //-#endif
  }
  /**
   * Access get counter in kernel extension through JNI or sysCalls.
   *
   * @param iterations    number of iterations
   * @param cycle_counter HPM counter that counts cycles
   * @param counter       HPM counter to read
   * @param jni           use JNI call if true.
   */
  private static void accessGetCounter(int iterations, int cycle_counter, int counter, 
				       boolean jni) 
  {
    //-#if RVM_WITH_HPM
    // time in seconds
    long start = VM_Time.cycles();
    if (jni == JNI) {
      Java2HPM.resetMyThread();
      Java2HPM.startMyThread();
    } else {
      VM_SysCall.sysHPMresetMyThread();
      VM_SysCall.sysHPMstartMyThread();
    }

    long count;
    for (int i = 0; i < iterations; i++) {	
      if (jni == JNI) {
	count = Java2HPM.getCounterMyThread(counter);
      } else {
	count = VM_SysCall.sysHPMgetCounterMyThread(counter);
      }
    }

    long cycles;
    if (jni == JNI) {
      cycles = Java2HPM.getCounterMyThread(cycle_counter);
    } else {
      cycles = VM_SysCall.sysHPMgetCounterMyThread(cycle_counter);
    }

    long end       = VM_Time.cycles();
    // time in milliseconds
    double time          = VM_Time.cyclesToMillis(end - start);
    // time in nano seconds
    double averageTime   = sixDigitDecimal((time / (double) iterations)*1000000);
    double averageCycles = ((cycles*100)/ iterations) / (double) 100;

    if (jni == JNI) {
      VM.sysWrite("   JNI     AccessGetCounter(");
    } else {
      VM.sysWrite("   sysCall AccessGetCounter(");
    }
    VM.sysWriteInt(iterations);
    VM.sysWrite(", ",cycle_counter,", ",counter);
    VM.sysWrite(") time: total ",time);VM.sysWrite("ms (",averageTime);
    VM.sysWrite("ns); cycles: ");VM.sysWriteLong(cycles);
    VM.sysWrite("(",averageCycles);VM.sysWrite(")\n");
    //-#endif
  }


  /**
   * Given a double format it with only 4 decimal places
   * @param value double to be formatted
   */
  private static double twoDigitDecimal(double value)
  {
    return ((int)((value*100)+0.5))/100.0;
  }
  /**
   * Given a double format it with only 4 decimal places
   * @param value double to be formatted
   */
  private static double sixDigitDecimal(double value)
  {
    return ((int)((value*1000000)+0.5))/1000000.0;
  }
}
