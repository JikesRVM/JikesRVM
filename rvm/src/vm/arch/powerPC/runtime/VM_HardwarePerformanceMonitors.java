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

  /*  debugging   */
  public static final int debug = 0;
  /**
   * Is the HPM system enabled?
   */
  public static boolean enabled = false;

  private static final int MAX_FILTER        = 3;	// number of filters
  private static final int FILTER_VERIFIED   = 1;	// events that have been verified
  private static final int FILTER_UNVERIFIED = 2;	// events that have not been verified
  private static final int FILTER_CAVEAT     = 4;	// events that work with caveats

  private static final int[] events = {0,0,0,0,0,0,0,0,0};
  private static int mode = 4; // Default to user mode only

  /**
   * Describe command line arguments 
   */
  public static void printHelp() {
    if (VM.BuildForHPM) {
      VM.sysWriteln("vm: -X:hpm:eventN=<int>, where 1<=N<=8\n");
      VM.sysWriteln("    -X:hpm:mode=<int>");
    } else {
      VM.sysWriteln("vm: Hardware performance monitors not supported");
    }
  }

  /**
   * Process command line arguments
   */
  public static void processArg(String arg) {
    if (VM.BuildForHPM) {
      int split = arg.indexOf('=');
      if (split == -1) {
	VM.sysFail("  Illegal option specification!\n  \""+arg+
		   "\" must be specified as a name-value pair in the form of option=value\n");
      }
      String name = arg.substring(0,split-1);
      String name2 = arg.substring(0,split);
      if (name.equals("event")) {
	String num = arg.substring(split-1,split);
	String value = arg.substring(split+1);
	try {
	  int eventNum = Integer.parseInt(num);
	  int eventVal = Integer.parseInt(value);
	  events[eventNum] = eventVal;
	} catch (NumberFormatException e) {
	  VM.sysWriteln("HPM: can't translate value of events for Hardware Performance Monitor");
	  VM.sysWriteln("     arg was -X:hpm:"+arg);
	  VM.sysExit(-1);
	} catch (ArrayIndexOutOfBoundsException e) {
	  VM.sysWriteln("HPM: invalid event number "+num);
	  VM.sysWriteln("   arg was -X:hpm:"+arg);
	  VM.sysExit(-1);
	}
	if (!enabled) {
	  enabled = true;
	  VM_Callbacks.addExitMonitor(new VM_Callbacks.ExitMonitor() {
	      public void notifyExit(int value) { report(); }
	    });
	  VM_Callbacks.addAppStartMonitor(new VM_Callbacks.AppStartMonitor() {
	      public void notifyAppStart(String app) { reportResetAndStart(); }
	    });
	  VM_Callbacks.addAppCompleteMonitor(new VM_Callbacks.AppCompleteMonitor() {
	      public void notifyAppComplete(String app) { reportResetAndStart(); }
	    });
	}
      } else if (name2.equals("mode")) {
	String value = arg.substring(split+1);
	mode = Integer.parseInt(value);
      } else {
	VM.sysFail("Unrecognized argument \"-X:hpm:"+arg+"\"");
      }
    } else {
      VM.sysWriteln("vm: Hardware performance monitors not supported; ignoring argument");
    }
  }  
  
  /**
   * Initialize the hardware performance monitors via VM_Controller options.
   */
  public static void boot() {
    if(debug>=1) {
	VM.sysWrite("VM_HPM.boot() enabled ");VM.sysWrite(enabled==true?"true":"false");
	VM.sysWrite(", VM.BuildForHPM ");VM.sysWrite(VM.BuildForHPM==true?"true":"false");
	VM.sysWrite("\n");
    }
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      // TODO: get rid of this preprcessor glop and the duplicatation of code in sys.C 
      // by switching to a JNI interface instead of a syscall interface.
      if(debug>=1) {
        VM.sysWrite("VM_HardwarePerformanceMonitor.init(): Events 1: "); VM.sysWrite(events[1]);
        VM.sysWrite(", 2: ");VM.sysWrite(events[2]);VM.sysWrite(", 3: ");VM.sysWrite(events[3]);
        VM.sysWrite(", 4: ");VM.sysWrite(events[4]);VM.sysWrite(", 5: ");VM.sysWrite(events[5]);
        VM.sysWrite(", 6: ");VM.sysWrite(events[6]);VM.sysWrite(", 7: ");VM.sysWrite(events[7]);
        VM.sysWrite(", 8: ");VM.sysWrite(events[8]);VM.sysWrite(", mode: ");VM.sysWrite(mode);
        VM.sysWrite("\n");
      }
      VM.sysCall1(VM_BootRecord.the_boot_record.sysHPMinitIP,
		  FILTER_UNVERIFIED|FILTER_VERIFIED|FILTER_CAVEAT);
      VM.sysCall4(VM_BootRecord.the_boot_record.sysHPMsetEventIP, 
		  events[1],events[2],events[3],events[4]);
      VM.sysCall4(VM_BootRecord.the_boot_record.sysHPMsetEventXIP, 
		  events[5],events[6],events[7],events[8]);
      VM.sysCall1(VM_BootRecord.the_boot_record.sysHPMsetModeIP,mode);
      if(debug>=1) VM.sysWrite("VM_HPM.init() call set settings\n");
      VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMsetSettingsIP);
      if(debug>=1) VM.sysWrite("VM_HPM.init() call set counting\n");
      VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMstartCountingIP);
      //-#endif
    }
  }
  
  /**
   * Print a report.
   */
  public static void report() {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      // TODO: get rid of this preprcessor glop and the duplicatation of code in sys.C 
      // by switching to a JNI interface instead of a syscall interface.
      VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMstopCountingIP);
      VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMprintIP);
      //-#endif
    }
  }

  /**
   * Provide interface from outside to reset hardware performance counters.
   */
  public static void reset() {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      // TODO: get rid of this preprcessor glop and the duplicatation of code in sys.C 
      // by switching to a JNI interface instead of a syscall interface.
      VM.sysWrite("VM_HardwarePerformanceMonitors.reset()\n");
      if (VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMresetCountersIP) != 0) {
	VM.sysWrite("VM_HardwarePerformanceMonitors.reset(): sysHPMresetCounters failed!\n");
	VM.sysExit(-1);
      }
      //-#endif
    }
  }

  /**
   * Provide interface from outside to stop the counting of hardware events,
   * report on hardware performance monitors, reset the counters, and restart 
   * the counting.
   */
  public static void reportResetAndStart() {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      // TODO: get rid of this preprcessor glop and the duplicatation of code in sys.C 
      // by switching to a JNI interface instead of a syscall interface.
      VM.sysWrite("VM_HardwarePerformanceMonitors.reportResetAndStart()\n");
      if (VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMstopCountingIP) != 0) {
	VM.sysWrite("VM_HardwarePerformanceMonitors.report(): sysHPMstop failed!\n");
	VM.sysExit(-1);
      }
      if (VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMprintIP) != 0) {
	VM.sysWrite("VM_HardwarePerformanceMonitors.report(): sysHPMprint failed!\n");
	VM.sysExit(-1);
      }
      if (VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMresetCountersIP) != 0) {
	VM.sysWrite("VM_HardwarePerformanceMonitors.resetAndStart(): sysHPMresetCounters failed!\n");
	VM.sysExit(-1);
      }
      if (VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMstartCountingIP) != 0) {
	VM.sysWrite("VM_HardwarePerformanceMonitors.resetAndStart(): sysHPMstartCounting failed!\n");
	VM.sysExit(-1);
      }
      //-#endif
    }
  }

  /**
   * Assumes that events and mode are set.
   * May only call once without an intervening call to deleteSettings.
   */
  static void setSettings()     
  {
    VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMsetSettingsIP);
  }
  /**
   * Assumes that setSettings was called.
   */
  static void deleteSettings()     
  {
    VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMdeleteSettingsIP);
  }
  static void startCounting()     
  {
    VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMstartCountingIP);
  }

}

