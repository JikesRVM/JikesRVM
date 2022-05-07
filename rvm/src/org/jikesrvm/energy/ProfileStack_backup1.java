package org.jikesrvm.energy;

import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayDeque;

import org.jikesrvm.VM;

/**
 * Event object for stack
 */
class EventStack {
	public Long threadId;
	public int cmid;
	/**
	 * L3 cache miss information,
	 * #0: L3 cache misses
	 * #1: L3 cache references
	 * #2: L3 cache miss rates
	 * TLB miss information,
	 * #3: data TLB misses
	 * #4: instruction TLB misses
	 * #5: TLBMisses
	 * Energy consumption information,
	 * #6: DRAM/uncore GPU energy consumption
	 * #7: CPU energy consumption
	 * #8: package energy consumption
	 **/
	public int eventId;

	public EventStack(long threadId, int cmid, int eventId) {
		this.threadId = threadId;
		this.cmid = cmid;
		this.eventId = eventId;
	}
	
	/**
	 * This hashcode implementation is weak, improve it later.
	 */
	@Override
	public int hashCode() {
		return threadId.hashCode() ^ cmid ^ eventId;
	}
	@Override
	public boolean equals(Object o) {
		if(o == this) return true;
		if(!(o instanceof EventStack)) return false;
		if(((EventStack)o).threadId == this.threadId 
				&& ((EventStack)o).cmid == this.cmid
				&& ((EventStack)o).eventId == this.eventId) {
			return true;
		}
		return false;
	}
}

public class ProfileStack implements ProfilingTypes {
	
	public static int methodCount = 0;
	public static int entrySize = 1000000;
	public static int methodIndexSize = 1000;
	/**
	 * For calculting profiling information
	 */
	public static HashMap<EventStack, Deque<Double>> stack;

	public double[] perfEnerInfo = new double[9];
	/**
	 * Stack size for each profiling metric
	 */
	public static int[] stackSize;
	/**
	 * Records method index sequentially
	 * TODO: HashMap/HashSet or array? No clue which one is better. Since
	 * HashMap/HashSet has overhead for two object accesses for each map
	 * operations. Object key and Object Value. 
	 * But array has repetitive index values be stored, because each push 
	 * would create a new entry in this array no matter if the index has 
	 * existed or not. Maybe test it latter.
	 */
	public static int[] methodIndex;
	
	public static void InitStack(int socketNum) {
		// stack stores count, wallclocktime, index, cpuTime, userTime, DramEnergy, CpuEnergy, PkgEnergy
		methodIndex = new int[methodIndexSize];
		stack = new HashMap<EventStack, Deque<Double>>();
	}
	
	public static boolean isEmpty() {
		return stackSize[COUNT] == 0 ? true : false;
	}
	
	public static long getStackSize(EventStack entry){
//		InitStack(EnergyCheckUtils.socketNum);
		return stack.get(entry).size();
	}
	
	public static void push(EventStack entry, double eventValue) {
		if(stack.containsKey(entry)) {
			stack.get(entry).addFirst(eventValue);
		} else {
			Deque<Double> deque = new ArrayDeque<Double>();
			deque.add(eventValue);
			stack.put(entry, deque);
		}
	}

	public static double pop(EventStack entry){
		if(!stack.containsKey(entry)) {
			if(stack.isEmpty()) {
				VM.sysWriteln("stack is empty!!");
			}
			VM.sysWriteln("entry will be poped dones't exist, cmid: " + entry.cmid 
					+ " eventId: " + entry.eventId + " threadId: " + entry.threadId);
		}
		return stack.get(entry).removeFirst();
	}
	
	public static boolean exist(EventStack entry) {
		return stack.containsKey(entry);
	}
	
	public static double peak(EventStack entry) {
		if(stack.containsKey(entry)) {
			return stack.get(entry).peekFirst();
		}
		return -1.0d;
	}
	
	public static void dump() {
		for(Map.Entry<EventStack,  Deque<Double>> entry : stack.entrySet()) {
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
