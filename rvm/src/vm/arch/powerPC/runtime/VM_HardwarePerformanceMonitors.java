/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Scheduler;
//-#if RVM_WITH_HPM
import com.ibm.JikesRVM.Java2HPM;
/**
 * This class provides support to hardware performance monitors
 * without making any assumption of what architecture we are running on.
 * <p>
 * @author Peter F. Sweeney
 * @author Dave Grove
 */
public class VM_HardwarePerformanceMonitors {

  static private int debug = 0;
  /**
   * Is the HPM system enabled?
   */
  private static boolean enabled = false;
  public  static boolean enabled() { return enabled; }

  /*
   * Print machine's processor name
   */
  private static boolean hpm_processor = false;
  /*
   * List events on machine
   */
  private static boolean hpm_list_all_events = false;
  /*
   * List events on machine
   */
  private static boolean hpm_list_selected_events = false;
  /*
   * test HPM access times for sysCalls and JNI
   */
  private static boolean hpm_test = false;

  /*
   * HPM information 
   */
  public static HPM_info hpm_info;

  /**
   * Describe command line arguments 
   */
  public static void printHelp() {
    if (VM.BuildForHPM) {
      VM.sysWriteln("Boolean Options (-X:hpm:<option>=true or -X:hpm:<option>=false) default is false");
      VM.sysWriteln(" Option       Description"); 
      VM.sysWriteln(" trace        dump HPM counter values at each thread switch.");
      VM.sysWriteln(" processor    print name of processor and number of counters.");
      VM.sysWriteln(" listAll      list all events associated with each counter.");
      VM.sysWriteln(" listSelected list selected events for each counter.");
      //VM.sysWriteln(" test       at end of execution, compute access time with sysCall and JNI.");
      VM.sysWriteln();
      VM.sysWriteln("Value Options (-X:hpm:<option>=<value>)");
      VM.sysWriteln(" Option     Type    Description"); 
      VM.sysWriteln(" eventN     int     specify event for counter N where 1<=N<=UB and UB is processor specific");
      VM.sysWriteln(" mode       int     specify mode: 1=GROUP, 2=PROCESS, 4=KERNEL, 8=USER, 16=COUNT, 32=PROCTREE");
      VM.sysWriteln(" trace_file String  prefix of trace file name.  Concatenate virtual processor number.");
      VM.sysWriteln();
    } else {
      VM.sysWriteln("\nrvm: Hardware performance monitors not supported");
    }
    VM.shutdown(-1);
  }

  /**
   * Process command line arguments
   */
  public static void processArg(String arg) {
    if (VM.BuildForHPM) {
      //-#if RVM_WITH_HPM
      if (hpm_info == null) {
	hpm_info = new HPM_info();
      }

      if (arg.compareTo("help") == 0) {
	printHelp();
      }
      int split = arg.indexOf('=');
      if (split == -1) {
	VM.sysWriteln("  Illegal option specification!\n  \""+arg+
		      "\" must be specified as a name-value pair in the form of option=value");
	VM.shutdown(-1);
      }
      String name = arg.substring(0,split-1);
      String name2 = arg.substring(0,split);
      if (name.equals("event")) {
	String num = arg.substring(split-1,split);
	String value = arg.substring(split+1);
	try {
	  int eventNum = Integer.parseInt(num);
	  int eventVal = Integer.parseInt(value);
	  hpm_info.ids[eventNum-1] = eventVal;
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
	}
      } else if (name2.equals("mode")) {
	String value = arg.substring(split+1);
	int mode = Integer.parseInt(value);
	hpm_info.mode = mode;
      } else if (name2.equals("trace_file")) {
	hpm_info.traceFilePrefix = arg.substring(split+1);
	if(debug>=1)VM.sysWriteln("VM_HPM.processArgs() trace file prefix found \""+
				  hpm_info.traceFilePrefix+"\"");
      } else if (name2.equals("trace")) {
	// trace HPM counter values at thread switch time
	String value = arg.substring(split+1);
	if (value.compareTo("true")==0) {
	  VM_Processor.hpm_trace = true;
	} else if (value.compareTo("false")==0) {
	  VM_Processor.hpm_trace = false;
	} else {
	  VM.sysWriteln("\nrvm: unrecognized boolean value "+value+"\n -X:hpm:trace={true|false} is the correct syntax");
	  VM.shutdown(-1);
	}
      } else if (name2.equals("processor")) {
	// trace HPM counter values at thread switch time
	hpm_processor = true;
      } else if (name2.equals("listAll")) {
	// trace HPM counter values at thread switch time
	String value = arg.substring(split+1);
	if (value.compareTo("true")==0) {
	  hpm_list_all_events = true;
	} else if (value.compareTo("false")==0) {
	  hpm_list_all_events = false;
	} else {
	  VM.sysWriteln("\nrvm: unrecognized boolean value "+value+"\n -X:hpm:list={true|false} is the correct syntax");
	  VM.shutdown(-1);
	}
      } else if (name2.equals("listSelected")) {
	// trace HPM counter values at thread switch time
	String value = arg.substring(split+1);
	if (value.compareTo("true")==0) {
	  hpm_list_selected_events = true;
	} else if (value.compareTo("false")==0) {
	  hpm_list_selected_events = false;
	} else {
	  VM.sysWriteln("\nrvm: unrecognized boolean value "+value+"\n -X:hpm:events={true|false} is the correct syntax");
	  VM.shutdown(-1);
	}
      } else if (name2.equals("test")) {
	// trace HPM counter values at thread switch time
	String value = arg.substring(split+1);
	if (value.compareTo("true")==0) {
	  hpm_test = true;
	} else if (value.compareTo("false")!=0) {
	  VM.sysWriteln("\nrvm: unrecognized boolean value "+value+"\n -X:hpm:test={true|false} is the correct syntax");
	  VM.shutdown(-1);
	}
      } else {
	VM.sysWriteln("rvm: Unrecognized argument \"-X:hpm:"+arg+"\"");
	VM.shutdown(-1);
      }
      //-#endif
    } else {
      VM.sysWriteln("\nrvm: Hardware performance monitors not supported.  Illegal command line options \""+arg+"\"\n");
      VM.shutdown(-1);
    }
  }  
  
  /**
   * Initialize the hardware performance monitors via command line options.
   * This routine must be called before any other interaction with HPM occurs.
   * Use sysCall interface to initialize HPM because JNI environment is not yet initialized.
   * SysCall interface accesses static C variables in hpm.c.
   * Called from VM.boot() before VM_Scheduler.boot() is called.
   */
  public static void boot() 
  {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      // When only the main thread Java thread is created, allocate HPM_counters.
      VM_Callbacks.addExitMonitor(new VM_Callbacks.ExitMonitor() {
	public void notifyExit(int value) { 
	  if(debug>=1)VM.sysWriteln("VM_HPM.notifyExit(",value,")");
	  stopUpdateResetAndReport(); 
	  if(debug>=1)VM.sysWriteln("VM_HPM.notifyExit(",value,") finished");
	}
      });
      VM_Callbacks.addAppRunStartMonitor(new VM_Callbacks.AppRunStartMonitor() {
	public void notifyAppRunStart(String app, int run) { 
	  if(debug>=1)VM.sysWriteln("VM_HPM.notifyAppRunStart(",app,",", run,")");
	  stopResetAndStart();
	  System.gc();	// call GC
	  stopUpdateResetReportAndStart(); 
	  if(debug>=1)VM.sysWriteln("VM_HPM.notifyAppRunStart(",app,",", run,") finished");
	}
      });
      VM_Callbacks.addAppRunCompleteMonitor(new VM_Callbacks.AppRunCompleteMonitor() {
	public void notifyAppRunComplete(String app, int run) { 
	  if(debug>=1)VM.sysWriteln("VM_HPM.notifyAppRunComplete(",app,",", run,")");
	  stopUpdateResetReportAndStart(); 
	  if(debug>=1)VM.sysWriteln("VM_HPM.notifyAppRunComplete(",app,",", run,") finished");
	}
      });

      VM_Thread thread = VM_Thread.getCurrentThread();
      if (thread.hpm_counters == null) {
	if(debug>=1)VM.sysWriteln("VM.boot() call new HPM_counters for primordial thread");
	thread.hpm_counters = new HPM_counters();
      }

      int events[] = hpm_info.ids;
      if (debug>=2) {
        VM.sysWrite("VM_HPM.boot(): Events 1: "); VM.sysWrite(events[0]);
	VM.sysWrite(", 2: ");VM.sysWrite(events[1]);VM.sysWrite(", 3: ");VM.sysWrite(events[2]);
	VM.sysWrite(", 4: ");VM.sysWrite(events[3]);VM.sysWrite(", 5: ");VM.sysWrite(events[4]);
	VM.sysWrite(", 6: ");VM.sysWrite(events[5]);VM.sysWrite(", 7: ");VM.sysWrite(events[6]);
	VM.sysWrite(", 8: ");VM.sysWrite(events[7]);VM.sysWrite(", mode: ");VM.sysWrite(hpm_info.mode);
	VM.sysWrite("\n");
      }
      if(debug>=1)VM.sysWrite("VM_HPM.boot() call hpmInit()\n");
      VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMinitIP);
      if(debug>=1) {
	VM.sysWrite("VM_HPM.boot() call hpmSetEvent(");
	VM.sysWrite(events[0]);VM.sysWrite(",");VM.sysWrite(events[1]);VM.sysWrite(",");
	VM.sysWrite(events[2]);VM.sysWrite(",");VM.sysWrite(events[3]);VM.sysWrite(")\n");
      }
      VM.sysCall4(VM_BootRecord.the_boot_record.sysHPMsetEventIP,
	       events[0],events[1],events[2],events[3]);
      if(debug>=1){
	VM.sysWrite("VM_HPM.boot() call hpmSetEventX(");
	VM.sysWrite(events[4]);VM.sysWrite(",");VM.sysWrite(events[5]);VM.sysWrite(",");
	VM.sysWrite(events[6]);VM.sysWrite(",");VM.sysWrite(events[7]);VM.sysWrite(")\n");
      }
      VM.sysCall4(VM_BootRecord.the_boot_record.sysHPMsetEventXIP,
	       events[4],events[5],events[6],events[7]);
      if(debug>=1){
	VM.sysWrite("VM_HPM.boot() call hpmSetMode(",hpm_info.mode,")\n");
      }
      VM.sysCall1(VM_BootRecord.the_boot_record.sysHPMsetModeIP, hpm_info.mode);
      //-#endif
    }
  }
  
  /**
   * Assume that HPM's init routine has been sysCalled.
   * Use of JNI to get string information from HPM.
   * Called from VM.boot() after VM_Scheduler.boot() is called.
   *
   * Don't use VM.sysExit because rvm is in an inconsistent state during call to 
   * VM_Callbacks.notifyExit.  Instead use VM.shutdown.
   */
  public static void setUpHPMinfo() {
    if (VM.BuildForHPM && enabled) {
      if(debug>=1) VM.sysWrite("VM_HPM.setUpHPMinfo()\n");
      /* 
       * Assume that hpm_init, hpm_set_event, hpm_set_event_X, hpm_set_mode, and
       * set_settings have been called.  
       * Initialize hpm_info.
       */
      if (hpm_test) {
	VM_Callbacks.addExitMonitor(new VM_Callbacks.ExitMonitor() {
          public void notifyExit(int value) { 
	    Java2HPM.computeCostsToAccessHPM();
	  }
	});
      }
      if (hpm_list_selected_events) {
	Java2HPM.listSelectedEvents();
      }
      hpm_info.numberOfCounters = Java2HPM.getNumberOfCounters();
      hpm_info.processorName    = Java2HPM.getProcessorName();
      VM.sysWrite(  "\nProcessor name: \"",hpm_info.processorName,"\" has ",
		    hpm_info.numberOfCounters);
      VM.sysWriteln(" counters");
      if (hpm_processor) {
	VM.shutdown(-1);
      }
      if (hpm_list_all_events) {
	Java2HPM.listAllEvents();
	VM.shutdown(-1);
      }
      String []short_names = new String[hpm_info.numberOfCounters];
      int max_length = 10;
      for (int i=0; i<hpm_info.numberOfCounters; i++) {
	short_names[i] = Java2HPM.getEventShortName(i);
	if (max_length < short_names[i].length()) max_length=short_names[i].length();
	if (debug>=3){
	  VM.sysWrite("short_name[");VM.sysWrite(i+1);	
	  VM.sysWrite("] \"");VM.sysWrite(short_names[i]);VM.sysWrite("\"\n");
	}
      }
      // format short names to same length
      // translate 0-origin to 1-origin short_names arrays
      for (int i=1;  i<=hpm_info.numberOfCounters; i++) {
	hpm_info.short_names[i] = short_names[i-1];
	for (int j=0; j<max_length - short_names[i-1].length(); j++) {
	  hpm_info.short_names[i] += " ";
	}
	if (debug>=1){
	  VM.sysWrite("hpm_info.short_name[");VM.sysWrite(i+1);	
	  VM.sysWrite("] \"");VM.sysWrite(hpm_info.short_names[i]);VM.sysWrite("\"\n");
	}
      }
      hpm_info.short_names[0] = "REAL_TIME";
      int length = hpm_info.short_names[0].length();
      for (int j=0; j<max_length - length; j++ ) {
	hpm_info.short_names[0] += " ";
      }
    }
  }

  /**
   * Report on HPM.
   * Assume VM_Processor and VM_Thread HPM_counter data structures are up-to-date.
   * After HPM_counters are reported, they are zeroed (reset).
   */
  public static void report() {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      if(debug>=2)VM.sysWrite("VM_HardwarePerformanceMonitors.report()\n");
      HPM_counters sum = new HPM_counters();
      VM.sysWriteln("\nDump HPM counter values for virtual processors");
      for (int i = 1; i<= VM_Scheduler.numProcessors; i++) {
	VM_Processor processor = VM_Scheduler.processors[i];
	VM.sysWriteln(" Virtual Processor: ",i);
	processor.hpm_counters.dump_counters(hpm_info);
	processor.hpm_counters.accumulate(sum, hpm_info);
	processor.hpm_counters.reset_counters();
	
      }
      if (VM_Scheduler.numProcessors>1) {
	VM.sysWriteln("Dump aggregate HPM counter values for VirtualProcessors");
	sum.dump_counters(hpm_info);
      }

      VM.sysWriteln("\nDump HPM counter values for threads");
      sum.reset_counters();
      HPM_counters aos = new HPM_counters();
      aos.reset_counters();
      int n_aosThreads = 0;
      int n_nonZeroThreads = 0; 
      for (int i = 1, n = VM_Scheduler.threads.length; i < n; i++) {
	VM_Thread t = VM_Scheduler.threads[i];
	if (t != null) {
	  String thread_name = t.getClass().getName();
	  // dump HPM counter values
	  synchronized (hpm_info) {
	    VM.sysWrite(" ThreadIndex: ",t.getIndex()," ");
	    VM.sysWriteln(thread_name," ");
	    if (t.hpm_counters != null) {
	      if (t.hpm_counters.dump_counters(hpm_info)) n_nonZeroThreads++;
	      t.hpm_counters.accumulate(sum, hpm_info);
	      if (thread_name.lastIndexOf("VM_ControllerThread") != -1 ||
		  thread_name.lastIndexOf("VM_MethodSampleOrganizer") != -1){
		t.hpm_counters.accumulate(aos, hpm_info);
		n_aosThreads++;
	      }
	      t.hpm_counters.reset_counters();
	    } else {
	      VM.sysWriteln(" hpm_counters == null!***");
	    }
	  }
	}
      }
      if (n_aosThreads > 1) {
	VM.sysWriteln("\nDump aggregate HPM counter values for AOS threads");
	aos.dump_counters(hpm_info);
      }
      if (n_nonZeroThreads > 1) {
	VM.sysWriteln("\nDump aggregate HPM counter values for threads");
	sum.dump_counters(hpm_info);
      }
      //-#endif
    }
  }

  /**
   * Public interface to stop, reset, report and start counting of hardware events.
   * <p> 
   * Using sysCall interface (could use JNI interface).
   */
  public static void stopUpdateResetReportAndStart() {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      if(debug>=2)VM.sysWrite("VM_HardwarePerformanceMonitors.stopUpdateResetReportAndStart()\n");
      stop_update_reset_report();

      start();
      //-#endif
    }
  }

  /**
   * Public interface to stop, reset and report counting of hardware events.
   */
  public static void stopUpdateResetAndReport() {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      if(debug>=2)VM.sysWrite("VM_HardwarePerformanceMonitors.stopUpdateResetReport()\n");
      stop_update_reset_report();
      //-#endif
    }
  }

  /**
   * Public interface to reset VM_Processor and VM_Thread HPM_counter data structures,
   * and reset HPM counters.
   */
  public static void stopResetAndStart() {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      if(debug>=2)VM.sysWrite("VM_HardwarePerformanceMonitors.stopAndReset()\n");
      stop(); 
      Reset(); 
      start();
      //-#endif
    }
  }
  /**
   * Public interface to reset VM_Processor and VM_Thread HPM_counter data structures,
   * and reset HPM counters.
   */
  private static void Reset() {
    if (VM.BuildForHPM && enabled) {
      //-#if RVM_WITH_HPM
      if(debug>=2)VM.sysWrite("VM_HardwarePerformanceMonitors.reset()\n");
      for (int i = 1; i<= VM_Scheduler.numProcessors; i++) {
	VM_Processor processor = VM_Scheduler.processors[i];
	if (processor.hpm_counters != null) {
	  processor.hpm_counters.reset_counters();
	} else {
	  VM.sysWrite("***VM_HPM.reset() Virtual Processor ",i," hpm_counters == null!***");
	}
      }
      for (int i = 1, n = VM_Scheduler.threads.length; i < n; i++) {
	VM_Thread t = VM_Scheduler.threads[i];
	if (t != null) {
	  if (t.hpm_counters != null) {
	    t.hpm_counters.reset_counters();
	  } else {
	    t.hpm_counters = new HPM_counters();
	    if (debug>=1) 
	      VM.sysWrite("***VM_HPM.reset() thread ",i," hpm_counters == null!***");
	  }
	}
      }
      reset();
      //-#endif
    }
  }

  /*
   * Do the work.
   * Private interface to stop, reset and report counting of hardware events.
   * Constraint: VM.BuildForHPM and enabled are set and built with RVM_WITH_HPM.
   */
  private static void stop_update_reset_report() 
  {
    if(debug>=2)VM.sysWrite("VM_HardwarePerformanceMonitors.stop_update_reset_report()\n");

    stop();

    // update hpm counters of current processor and thread.
    VM_Processor.getCurrentProcessor().updateHPMcounters(VM_Thread.getCurrentThread(), null, false);

    reset();
    report();
  }

  /*
   * Do the work.
   * Private interface to start counting of hardware events.
   * Constraint: VM.BuildForHPM and enabled are set and built with RVM_WITH_HPM.
   */
  private static void start() 
  {
    //-#if RVM_WITH_HPM
    if(debug>=2)VM.sysWrite("VM_HardwarePerformanceMonitors.start()\n");
    VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMstartCountingIP);
    //-#endif
  }
  /*
   * Do the work.
   * Private interface to stop counting of hardware events.
   * Constraint: VM.BuildForHPM and enabled are set and built with RVM_WITH_HPM.
   */
  private static void stop() 
  {
    //-#if RVM_WITH_HPM
    if(debug>=2)VM.sysWrite("VM_HardwarePerformanceMonitors.stop()\n");
    VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMstopCountingIP);
    //-#endif
  }
  /*
   * Do the work.
   * Private interface to reset counting of hardware events.
   * Constraint: VM.BuildForHPM and enabled are set and built with RVM_WITH_HPM.
   */
  private static void reset() 
  {
    //-#if RVM_WITH_HPM
    if(debug>=2)VM.sysWrite("VM_HardwarePerformanceMonitors.reset()\n");
    VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMresetCountersIP);
    //-#endif
  }
}
//-#endif
