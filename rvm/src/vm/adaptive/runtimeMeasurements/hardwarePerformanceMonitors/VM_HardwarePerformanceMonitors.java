/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.adaptive.VM_AOSOptions;
/*
 * This class provides access to hardware performance monitors
 * on PowerPC architectures.
 *
 * @author Peter F. Sweeney
 */

public class VM_HardwarePerformanceMonitors {

  static final int HPM_max_filter = 3;		// number of filters
  static final int HPM_filter_verified   = 1;	// events that have been verified
  static final int HPM_filter_unverified = 2;	// events that have not been verified
  static final int HPM_filter_caveat     = 4;	// events that work with caveats
  /**
   * Initialize the hardware performance monitors via VM_Controller options.
   */
  public static void init(VM_AOSOptions options) {
    
    //-#if RVM_WITH_HPM
    if (options.HARDWARE_PERFORMANCE_MONITORS) {
      int event1=0, event2=0, event3=0, event4=0;
      int event5=0, event6=0, event7=0, event8=0, mode=0;
      VM.sysWrite("VM_HardwarePerformanceMonitor.init()\n");
      VM.sysWrite("VM_HardwarePerformanceMonitor.init.boot(): Events 1: "+
		  options.HPM_EVENT_1+", 2: "+
		  options.HPM_EVENT_2+", 3: "+
		  options.HPM_EVENT_3+", 4: "+
		  options.HPM_EVENT_4+", 5: "+
		  options.HPM_EVENT_5+", 6: "+
		  options.HPM_EVENT_6+", 7: "+
		  options.HPM_EVENT_7+", 8: "+
		  options.HPM_EVENT_8+", mode: "+options.HPM_MODE+"\n");
      try {
	event1 = Integer.parseInt(options.HPM_EVENT_1);
	event2 = Integer.parseInt(options.HPM_EVENT_2);
	event3 = Integer.parseInt(options.HPM_EVENT_3);
	event4 = Integer.parseInt(options.HPM_EVENT_4);
	event5 = Integer.parseInt(options.HPM_EVENT_5);
	event6 = Integer.parseInt(options.HPM_EVENT_6);
	event7 = Integer.parseInt(options.HPM_EVENT_7);
	event8 = Integer.parseInt(options.HPM_EVENT_8);
	mode   = Integer.parseInt(options.HPM_MODE);
      } catch (NumberFormatException e) {
	VM.sysWrite("***VM_HardwarePerformanceMonitor.init(): Number Format Exception");
	VM.sysWrite("   can't translate value of events for Hardware Performance Monitor");
	VM.sysWrite("   Event 1: "+options.HPM_EVENT_1+
		    ", 2: "+options.HPM_EVENT_2+
		    ", 3: "+options.HPM_EVENT_3+
		    ", 4: "+options.HPM_EVENT_4+
		    ", 5: "+options.HPM_EVENT_5+
		    ", 6: "+options.HPM_EVENT_6+
		    ", 7: "+options.HPM_EVENT_7+
		    ", 8: "+options.HPM_EVENT_8+
		    ", mode: "+options.HPM_MODE+"\n");
	e.printStackTrace();
	VM.sysExit(-1);
      }
      VM.sysWrite("VM_HardwarePerformanceMonitor.init(): Events 1: "+
		  event1+", 2: "+event2+", 3: "+
		  event3+", 4: "+event4+", 5: "+
		  event5+", 6: "+event6+", 7: "+
		  event7+", 8: "+event8+", mode: "+mode+"\n");
      VM.sysCall1(VM_BootRecord.the_boot_record.sysHPMinitIP,
		  HPM_filter_unverified|HPM_filter_verified|HPM_filter_caveat);
      VM.sysCall4(VM_BootRecord.the_boot_record.sysHPMsetEventIP, 
		  event1,event2,event3,event4);
      VM.sysCall4(VM_BootRecord.the_boot_record.sysHPMsetEventXIP, 
		  event5,event6,event7,event8);
      VM.sysCall1(VM_BootRecord.the_boot_record.sysHPMsetModeIP,mode);
      VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMsetSettingsIP);
      VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMstartCountingIP);
    }
    //-#else
    {
      VM.sysWrite("System not built for HPM\n"); 
      VM.sysExit(-1);
    }
    //-#endif
  }
  
  /**
   * Print a report.
   */
  public static void report(VM_AOSOptions options) {
    //-#if RVM_WITH_HPM
    if (options.HARDWARE_PERFORMANCE_MONITORS) {
      VM.sysWrite("VM_HardwarePerformanceMonitors.report()\n");
      if (VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMstopCountingIP) != 0) {
	VM.sysWrite("VM_HardwarePerformanceMonitors.report(): sysHPMstop failed!\n");
	VM.sysExit(-1);
      }
      if (VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMprintIP) != 0) {
	VM.sysWrite("VM_HardwarePerformanceMonitors.report(): sysHPMprint failed!\n");
	VM.sysExit(-1);
      }
    }
    //-#else
    {
      VM.sysWrite("System not built for HPM\n"); 
      VM.sysExit(-1);
    }
    //-#endif
  }
  /**
   * Provide interface from outside to reset hardware performance counters.
   */
  public static void reset() {
    //-#if RVM_WITH_HPM
    VM.sysWrite("VM_HardwarePerformanceMonitors.reset()\n");
    if (VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMresetCountersIP) != 0) {
      VM.sysWrite("VM_HardwarePerformanceMonitors.reset(): sysHPMresetCounters failed!\n");
      VM.sysExit(-1);
    }
    //-#endif
  }
  /**
   * Provide interface from outside to stop the counting of hardware events,
   * report on hardware performance monitors, reset the counters, and restart 
   * the counting.
   */
  public static void reportResetAndStart() {
    //-#if RVM_WITH_HPM
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
