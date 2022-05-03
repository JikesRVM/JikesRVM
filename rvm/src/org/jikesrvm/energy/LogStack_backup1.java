package org.jikesrvm.energy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.jikesrvm.VM;

/**
 * Almost the same as ProfileStack, This class must be used with thread safe way since 
 * its methods are not synchronzed.
 */
public class LogStack implements ProfilingTypes {
	/**
	 * For calculating profiling information
	 */
	public static HashMap<EventStack, Deque<Double>> hotMethodsProfLog;
	
	//Since hashcode could be large, create a large entry size at first time
	public static int entrySize = 1000000;
	
	public static void InitStack(int socketNum) {
		// hotMethodsProfLog stores count, wallclocktime, index, cpuTime, userTime, DramEnergy, CpuEnergy, PkgEnergy
		if (hotMethodsProfLog == null){
			hotMethodsProfLog = new HashMap<EventStack, Deque<Double>>();
		}
	}
	
	public static long getStackSize(EventStack entry){
//		InitStack(EnergyCheckUtils.socketNum);
		return hotMethodsProfLog.get(entry).size();
	}
	
	public static void push(EventStack entry, double eventValue) {
		if(hotMethodsProfLog.containsKey(entry)) {
			hotMethodsProfLog.get(entry).addFirst(eventValue);
		} else {
			Deque<Double> deque = new ArrayDeque<Double>();
			deque.add(eventValue);
			hotMethodsProfLog.put(entry, deque);
		}
	}

	public static double pop(EventStack entry){
		return hotMethodsProfLog.get(entry).removeFirst();
	}
	
	public static double peak(EventStack entry) {
		return hotMethodsProfLog.get(entry).peekFirst();
	}
	
	public static void dump() {
		for(Map.Entry<EventStack,  Deque<Double>> entry : hotMethodsProfLog.entrySet()) {
			EventStack methodInfo = entry.getKey();
			Deque<Double> counters = entry.getValue();
			VM.sysWrite("Information of the method, methodId: "
					+ methodInfo.cmid + " threadId: " + methodInfo.threadId
					+ " eventId: " + methodInfo.eventId);
			for(double value : counters) {
				VM.sysWrite(" Value in stack: " + value);
			}
			VM.sysWriteln();
		}
	}
}
