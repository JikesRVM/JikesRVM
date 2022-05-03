package org.jikesrvm.energy;

import org.jikesrvm.VM;

/**
 * Almost the same as ProfileStack, This class must be used with thread safe way since 
 * its methods are not synchronzed.
 */
public class LogQueue implements ProfilingTypes {
	/**
	 * For calculating profiling information
	 * 1st dimension: eventId
	 * 2nd dimension: threadId
	 * 3rd dimension: methodId
	 * 4th dimension: stackId
	 */
	public static double[][][][] hotMethodsProfLog;
	/**
	 * hotMethodsProfLog size for each profiling metric
	 */
	public static int [][][] logSize;
	public static final int THREADSIZE = 50;
	
	public static int methodCount = 0;
	
	//Since hashcode could be large, create a large entry size at first time
	public static int entrySize = 100;
	
	public static void InitStack(int socketNum) {
		// hotMethodsProfLog stores count, wallclocktime, index, cpuTime, userTime, DramEnergy, CpuEnergy, PkgEnergy
		if (hotMethodsProfLog == null){
//			hotMethodsProfLog = new double[entrySize * socketNum][50];
			hotMethodsProfLog = new double[Scaler.getPerfEnerCounterNum()][THREADSIZE][entrySize * socketNum][50];
			logSize = new int[Scaler.getPerfEnerCounterNum()][THREADSIZE][entrySize * socketNum];
//			VM.sysWriteln("logSize: " + logSize.length + " hotMethodProfLog: " + hotMethodsProfLog.length);
		}
	}
	
	/**
	 * Once stack size of one methodId exceeds the stack boundary, increase the stack size for 
	 * all threads and events of this method stack
	 * @param methodId
	 */
	
	public static void growLogSize(int eventId, int threadId, int methodId) {
//		VM.sysWriteln("Kenan: ProfileStack length: " + ProfileStack.stack.length);
		//Expand method list column size first
		//Initialize varied stack size for the index only
		double[] newLogQueue = new double[(int)(logSize[eventId][threadId][methodId] * 1.25)];
		for(int j = 0; j < hotMethodsProfLog.length; j++) {
			for(int k = 0; k < hotMethodsProfLog[0].length; k++) {
				System.arraycopy(hotMethodsProfLog[j][k][methodId], 0, newLogQueue, 0, hotMethodsProfLog[j][k][methodId].length);
				hotMethodsProfLog[j][k][methodId] = newLogQueue;
			}
		}
	}
	
	/**
	 * Grow size of threadId and methodId on queue.
	 * @param eventId
	 * @param threadId
	 * @param methodId
	 */
	public static void growLogQueue(int eventId, int threadId, int methodId) {
		double[][][][] newLogQueue;
		//Expand method list column size first
		if(methodId >= hotMethodsProfLog[eventId][threadId].length && threadId >= hotMethodsProfLog[eventId].length) {
			newLogQueue = new double[Scaler.getPerfEnerCounterNum()][(int)(threadId * 1.25)][(int)(methodId * 1.25)][50];
		} else if(methodId >= hotMethodsProfLog[eventId][threadId].length) {
			newLogQueue = new double[Scaler.getPerfEnerCounterNum()][hotMethodsProfLog[eventId].length][(int)(methodId * 1.25)][50];
		} else if( threadId >= hotMethodsProfLog[eventId].length) {
			newLogQueue = new double[Scaler.getPerfEnerCounterNum()][(int)(threadId * 1.25)][hotMethodsProfLog[eventId][threadId].length][50];
		} else {
			return;
		}
		
		for(int j = 0; j < newLogQueue.length; j++) {
			for(int k = 0; k < newLogQueue[eventId].length; k++) {
				for (int i = 0; i < ProfileStack.methodIndex.length; i++) {
					int stackSize = hotMethodsProfLog[j][k][ProfileStack.methodIndex[i]].length;
					int newStackSize = newLogQueue[i][k][ProfileStack.methodIndex[i]].length;
					//Reinitialize newLogQueue for the method that has been invoked more than 50 times
					if(stackSize > newStackSize) {
						double[] newSize = new double[stackSize];
						newLogQueue[j][k][ProfileStack.methodIndex[i]] = newSize;
					}
					//Copy the methods that have been pushed on hotMethodsProfLog only
					System.arraycopy(hotMethodsProfLog[j][k][ProfileStack.methodIndex[i]], 0, newLogQueue[j][k][ProfileStack.methodIndex[i]]
							, 0, hotMethodsProfLog[j][k][ProfileStack.methodIndex[i]].length);
					hotMethodsProfLog[j][k][ProfileStack.methodIndex[i]] = newLogQueue[j][k][ProfileStack.methodIndex[i]];
				}
			}
		}
		//FIXME: increase the size of stack later
//		hotMethodsProfLog = newLogQueue;
//		if(methodId >= hotMethodsProfLog[eventId][threadId].length) {
//			int[][][] newLogSize = new int[Math.max(methodId + 1, (int)(methodId * 1.25))];
//	
//			// Note stackSize array start records from 0
//			System.arraycopy(logSize, 0, newLogSize, 0, logSize.length);
//			logSize = newLogSize;
//			VM.sysWriteln("New size initialization for hotMethodsProfLog: " + hotMethodsProfLog.length 
//					+ " New size for stackSize: " + logSize.length  
//					+ " Size for ProfileStack.methodIndex: " + ProfileStack.methodIndex.length + " SID is: " + methodId);
//		}
	}
	
	public static boolean isEmpty(int eventId, int threadId, int methodId) {
		return logSize[eventId][threadId][methodId] == 0 ? true : false;
	}
	
	public static long getStackSize(int eventId, int threadId, int methodId){
		return logSize[eventId][threadId][methodId];
	}
	
	public static void add(int eventId, int threadId, int methodId, double value) {
		/*Check boundary of hotMethodsProfLog*/
		//Check the boundary of eventId, threadId and methodId
		growLogQueue(eventId, threadId, methodId);
//		VM.sysWriteln("hotMethod length;" + hotMethodsProfLog.length + " log size: " + logSize[methodId]);
		//Check the boundary of stackSize for specified method (once one methodId
		//is out of boundary, increase size for all threads and events of this method)
		if (logSize[eventId][threadId][methodId] == hotMethodsProfLog[eventId][threadId][methodId].length - 1) {
			growLogSize(eventId, threadId, methodId);
		}
		hotMethodsProfLog[eventId][threadId][methodId][logSize[eventId][threadId][methodId]++] = value;
	}

	public static double pop(int eventId, int threadId, int methodId){
		//Epilogue may be sampled before prologue
//		InitStack(EnergyCheckUtils.socketNum);
		if(methodId < 0 || methodId >= hotMethodsProfLog.length)
			VM.sysWriteln("hotMethodsProfLog overflow methodId: " + methodId);
		if (logSize[eventId][threadId][methodId] <= 0){
			VM.sysWriteln("pop fail: no element to pop");
			return -1;
		} else 
//			VM.sysWriteln("pop value: " + hotMethodsProfLog[eventId][threadId][methodId][logSize[methodId] - 1]);
		return hotMethodsProfLog[eventId][threadId][methodId][--logSize[eventId][threadId][methodId]];
	}
	
	public static double peak(int eventId, int threadId, int methodId) {
		return hotMethodsProfLog[eventId][threadId][methodId][logSize[eventId][threadId][methodId] - 1];
	}
	
	public static void dump(int sid) {
	}
}

