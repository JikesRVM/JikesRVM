/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * This class provides access to hardware performance monitors
 * on PowerPC and Power architectures.
 * It relies on the support provided in bos.pmapi on AIX5.
 * 
 * @author Peter F. Sweeney
 * @author Dave Grove
 */
public class VM_HardwarePerformanceMonitors {

  /**
   * Is the HPM system enabled?
   */
  public static boolean enabled = false;

  /**
   * Describe command line arguments 
   */
  public static void printHelp() {
    VM.sysWriteln("vm: Hardware performance monitors not supported");
  }

  /**
   * Process command line arguments
   */
  public static void processArg(String arg) {
    VM.sysWriteln("vm: Hardware performance monitors not supported on IA32; ignoring argument");
  }  
  
  /**
   * Initialize the hardware performance monitors via VM_Controller options.
   */
  public static void boot() {
    if (VM.BuildForHPM && enabled) {
    }
  }
  
  /**
   * Print a report.
   */
  public static void report() {
    if (VM.BuildForHPM && enabled) {
    }
  }

  /**
   * Provide interface from outside to reset hardware performance counters.
   */
  public static void reset() {
    if (VM.BuildForHPM && enabled) {
    }
  }

  /**
   * Provide interface from outside to stop the counting of hardware events,
   * report on hardware performance monitors, reset the counters, and restart 
   * the counting.
   */
  public static void reportResetAndStart() {
    if (VM.BuildForHPM && enabled) {
    }
  }
}
