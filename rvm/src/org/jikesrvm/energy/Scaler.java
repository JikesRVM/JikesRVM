package org.jikesrvm.energy;


import static org.jikesrvm.runtime.SysCall.sysCall;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.Charset;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.runtime.SysCall;
import org.jikesrvm.util.StringUtilities;

public class Scaler implements ScalerOptions {
	
	public static final int ENERGY_METRIC_SIZE = 3;
	public static int[] freqs = new int[20];
	public static boolean isInitScaler = false;
    /**Number of counters to be profiled in perf*/
    public static int perfCounters = 0;
    /**Number of energy counters to be profiled in perf*/
    public static int energyCounters = 0;
	/**number of event needs to be profiled for dynamic scaling*/
	public static int eventNum = 0;
	/**Record governor type*/
	public static byte[][] governor;
	/**Event counter values*/
	public static long[] perfEventCounters = new long[Scaler.perfCounters];
	
	public static PerfEvent[] perfEvents;

	public static String[] perfEventNames;

	public static void initScaler() {
		if(!isInitScaler) {
			int core;
			if(Controller.options.ENABLE_COUNTER_PROFILING) {
				perfEventInit();
				//perfThreadInit();

			}
			//TODO: fix freqAvailable
			SysCall.sysCall.FreqAvailable(freqs);
			core = SysCall.sysCall.getCpuNum();
			governor = new byte[core][20];

//			SysCall.sysCall.sysStartCounters(cacheTLBEvents, cacheTLBEvents.length);
			isInitScaler = true;
		}
	}
	
	public static void openDVFSFiles() {
		SysCall.sysCall.openDVFSFiles();
	}
	
	public static void closeDVFSFiles() {
		SysCall.sysCall.closeDVFSFiles();
	}
	
	public static void setGovernor(byte[] gov) {
		int cpu = SysCall.sysCall.SetGovernor(gov);
		//Set governor
		governor[cpu] = gov;
	}
	
	public static String getGovernor() {
		int strLen;
		byte[] govBuf = new byte[60];
		strLen = SysCall.sysCall.GetGovernor(govBuf);
		return StringUtilities.asciiBytesToString(govBuf, 0, strLen);
//		return govBuf;
	}

	public static int checkCpuFrequency() {
		return SysCall.sysCall.checkFrequency();
	}

	public static void scale(int freq){
		//VM.sysWriteln("Kenan: Frequency: " + freqs[option] + " is going to be written");
		SysCall.sysCall.Scale(freq);
	}
	
	public static void pkgPowerLimit(int option, double power) {
		SysCall.sysCall.SetPackagePowerLimit(0, option, power);
	}

	private static ThreadLocal<Boolean> started = new ThreadLocal<Boolean>() {
		@Override protected Boolean initialValue() {
			return Boolean.FALSE;
		}
	};

//	public static void registerCounterThread() {
//		SysCall.sysCall.sysCounterForThread(cacheTLBEvents, cacheTLBEvents.length);
//	}
	
//	public static void accuCacheTLBCounters() {
//		SysCall.sysCall.sysStartCounters(cacheTLBEvents, cacheTLBEvents.length);
////		accumCounters(cacheTLBEvents, ret);
//		accumCounters(ret);
//	}
	
//	public static int accumCounters(long[] buf) {
////		if(!(started.get().booleanValue())) {
////			SysCall.sysCall.sysStartCounters(events, events.length);
////			
////			started.set(Boolean.TRUE);
////			return;
////		}
//		int eventSet = SysCall.sysCall.sysAccum(buf);
//
//		return eventSet;
//	}
	/**
	 * @return number of events plus one for elapse time
	 */
	public static int getTotalEventNum() {
	    /*
	     * If user enables counter printer, we need store energy consumption
	     * for each hot method. It includes energy consumptions of dram/uncore gpu (depends on CPU
	     * architecture), cpu and package.
	     */
		if(Controller.options.ENABLE_COUNTER_PRINTER) {
			return (eventNum + EnergyCheckUtils.ENERGY_ENTRY_SIZE) * EnergyCheckUtils.socketNum + 1;
		}
		return eventNum * EnergyCheckUtils.socketNum + 1;
	}
	
	/**
	 * @return number of perf counters 
	 */
	public static int getPerfEnerCounterNum() {

		if(Controller.options.ENABLE_COUNTER_PRINTER) {
			
			VM.sysWrite("scaler 137");
			perfCounters = Controller.options.ENABLE_COUNTER_PROFILING ? perfCounters : 0;
			energyCounters = Controller.options.ENABLE_ENERGY_PROFILING ? EnergyCheckUtils.ENERGY_ENTRY_SIZE : 0;
			
			return (perfCounters + energyCounters) * EnergyCheckUtils.socketNum;
		}
		return perfCounters * EnergyCheckUtils.socketNum;
	}
	
	  public static void perfEventInit() {
		   String events;

		    // initialize perf event
		    if(Controller.options.ENABLE_SCALING_BY_COUNTERS) {
		    	events = Controller.options.COUNTERS_FOR_SCALING;
		    } else {
		    	events = Controller.options.EVENTCOUNTER;
		    }
		    
		    if (events.length() == 0) {
			      // no initialization needed
			      return;
		    }
		    perfEventNames = events.split(",");
		    
		    /*
		     * For dynamic scaling based on event counters, we collect profiling information
		     * as follows:
		     * 1. cache-misses, 
			 * 2. cache-references(usually means cache accesses, but it depends on the CPU), 
			 * 3. cache-rates,
			 * 4. dTLB-load-misses,
			 * 5. iTLB-load-misses,
			 * 6. TLB-misses.
		     */
		    eventNum = Controller.options.ENABLE_SCALING_BY_COUNTERS ? 6: perfEventNames.length;
		    perfCounters = perfEventNames.length;
//		    PerfEvent.readBuffer = new long[Scaler.perfCounters];
//		    PerfEvent.previousValue = new long[Scaler.perfCounters];

//		    perfEvents = new PerfEvent[perfCounters];
//		    for (int i = 0; i < perfCounters; i++) {
//		    	perfEvents[i] = new PerfEvent(i, perfEventNames[i]);
//		    }

		  }

	  public static void perfThreadInit() {
		    //Initialize structures for perf_event_open
		    sysCall.sysPerfEventInit(perfCounters);

		    for (int i = 0; i < perfCounters; i++) {
		    	//Set up attributes and do perf_event_open for each event counter
		  	sysCall.sysPerfEventCreate(i, perfEventNames[i].concat("\0").getBytes());
		    }

		    //Enable perf event counters
		    sysCall.sysPerfEventEnable();
	
	  }

	  public static void perfThreadClose() {

		    sysCall.sysPerfEventDisable();

		    for (int i = 0; i < perfCounters; i++) {
		    	//Set up attributes and do perf_event_open for each event counter
		  	sysCall.sysCloseFd(i);
		    }

	  }

	/**Read event counters as a group*/
//	public static long[] perfCheck() {
//	    //Get counter values with one read by group reading
//	    PerfEvent.getCurrentValue();
//		return PerfEvent.readBuffer;
////		return perfEvents[eventId].getCurrentValue();
//	}
	
	  /**Read only one event counters*/
	public static long perfCheck(int eventId) {
		return PerfEvent.getCurrentValue(eventId);
	}
	
	
//	public static void getEventCounters(long[] buf) {
//		
////		PerfEvent[] perfEvents = org.mmtk.vm.VM.statistics.getPerfEvents();
//		for(int i = 0; i < perfEvents.length; i++) {
//			buf[i] = perfEvents[i].getEventCounter();
//		}
//	}
	
//	public static void stopCounters(long[] buf, int eventSet) {
//		SysCall.sysCall.sysStopCounters(buf, eventSet);
//	}
//	public static void main(String[] argv) {
//		String option = null;
//		option = argv[0];
//		int[] a = freqAvailable();
//		for(int i = 0; i < a.length; i++) {
//			System.out.println(a[i]);
//		}
//
//		if(Integer.parseInt(option) == 1) {
//			scale(a[1]);
//
//		} else if(Integer.parseInt(option) == 2) {
//			scale(a[15]);
//		} else if(Integer.parseInt(option) == 3) {
//			scale(a[11]);
//		}
//	}
}
