package org.jikesrvm.energy;

import org.jikesrvm.VM;

/**
 * Almost the same as ProfileStack, This class must be used with thread safe way since 
 * its methods are not synchronzed.
 */
public class LogStack implements ProfilingTypes {
	/**
	 * For calculating profiling information
	 */
	public static double[][] hotMethodsProfLog;
	/**
	 * hotMethodsProfLog size for each profiling metric
	 */
	public static int [] logSize;
	
	//Since hashcode could be large, create a large entry size at first time
	public static int entrySize = 1000000;
	
	public static void InitStack(int socketNum) {
		// hotMethodsProfLog stores count, wallclocktime, index, cpuTime, userTime, DramEnergy, CpuEnergy, PkgEnergy
		if (hotMethodsProfLog == null){
//			hotMethodsProfLog = new double[entrySize * socketNum][50];
			hotMethodsProfLog = new double[1000][50];
			logSize = new int[entrySize * socketNum];
			VM.sysWriteln("logSize: " + logSize.length + " hotMethodProfLog: " + hotMethodsProfLog.length);
		}
	}
	
	/**
	 * @param sid method ID
	 * Grow hotMethodProfLog size by size of ProfileStack.stack since both of them should have the same size. 
	 * So hotMethodsProlog would invoke this method only once ever.
	 */
	public static void growStackSize(int sid) {
		VM.sysWriteln("Kenan: ProfileStack length: " + ProfileStack.stack.length);
		//Expand method list column size first
		double[][] newStack = new double[Math.max(sid + 1, (int)ProfileStack.stack.length)][50];
		//Initialize varied stack size for the index only
		for (int i = 0; i < ProfileStack.methodIndex.length; i++) {
			//Reinitialize newstack for those method entries that don't have the same size as ProfileStack.stack
			if(hotMethodsProfLog[ProfileStack.methodIndex[i]].length != ProfileStack.stack[ProfileStack.methodIndex[i]].length) {
				newStack[i] = new double[ProfileStack.stack[ProfileStack.methodIndex[i]].length];
			}
			//Copy the methods that have been pushed on stack only
			System.arraycopy(ProfileStack.stack[ProfileStack.methodIndex[i]], 0, 
					newStack[ProfileStack.methodIndex[i]], 0, ProfileStack.stack[ProfileStack.methodIndex[i]].length);
			hotMethodsProfLog[i] = newStack[i];
		}
		hotMethodsProfLog = newStack;
		
//		int[] newStackSize = new int[Math.max(sid + 1, (int)(sid * 1.25))];
//
//		// Note stackSize array start records from 0
//		System.arraycopy(stackSize, 0, newStackSize, 0, stackSize.length);
//		stackSize = newStackSize;
//		VM.sysWriteln("New size initialization for stack: " + stack.length 
//				+ " New size for stackSize: " + stackSize.length  
//				+ " Size for methodIndex: " + methodIndex.length + " SID is: " + sid);
//
//		// VM.sysWriteln("hotMethodsProfLog size: " + logSize[sid] + " hotMethodsProfLog lenth " +
//		// hotMethodsProfLog[sid].length);
//
//		double[] newStack = new double[logSize[sid] + logSize[sid] >> 1];
//		// Note logSize array start records from 0
//		if (VM.VerifyAssertions)
//			VM._assert(hotMethodsProfLog[sid].length == logSize[sid] + 1,
//					"ProfileStack error");
//		System.arraycopy(hotMethodsProfLog[sid], 0, newStack, 0, hotMethodsProfLog[sid].length);
//		hotMethodsProfLog[sid] = newStack;

	}
	
	public static boolean isEmpty() {
		return logSize[COUNT] == 0 ? true : false;
	}
	
	public static long getStackSize(int sid){
//		InitStack(EnergyCheckUtils.socketNum);
		return logSize[sid];
	}
	
	public static void push(int sid, double value) {
		//too bad, can only intialize multi dimensional array here
//		InitStack(EnergyCheckUtils.socketNum);
		// Check boundary of hotMethodsProfLog
		if(sid >= hotMethodsProfLog.length) {
			VM.sysWriteln("hotMethod length;" + hotMethodsProfLog.length);
			growStackSize(sid);
		}
		VM.sysWriteln("hotMethod length;" + hotMethodsProfLog.length + " log size: " + logSize[sid]);

		hotMethodsProfLog[sid][logSize[sid]++] = value;

	}

	public static double pop(int sid){
		//Epilogue may be sampled before prologue
//		InitStack(EnergyCheckUtils.socketNum);
		if(sid < 0 || sid >= hotMethodsProfLog.length)
			VM.sysWriteln("hotMethodsProfLog overflow sid: " + sid);
		if (logSize[sid] <= 0){
			VM.sysWriteln("pop fail: no element to pop");
			return -1;
		} else 
			VM.sysWriteln("pop value: " + hotMethodsProfLog[sid][logSize[sid] - 1]);
		return hotMethodsProfLog[sid][--logSize[sid]];
	}
	
	public static double peak(int sid) {
		return hotMethodsProfLog[sid][logSize[sid] - 1];
	}
	
	public static void dump(int sid) {
		for(int i = 0; i < logSize[sid]; i++) {
//			VM.sysWriteln(hotMethodsProfLog[sid][i]);
		}
	}
}
