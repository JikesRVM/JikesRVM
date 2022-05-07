package org.jikesrvm.energy;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.Controller;

/**
 * @author kenan
 *
 */

public class ProfileQueue {
	//Post-calculating profiling information 
	public static long[][] methodProfQueue;
	public static double[][] matrix;
	private final static int MATRIX_COL_SIZE = 2;
	/**Times of the method that is out of time range of method hotness*/
	private final static int SHORT_METHOD_THRESHOLD = 10;

	public static int numNonskippableMethods = 0;
	//Method profiling array size for each profiling metric
	public static int [] methodProfSize;
	public static int entrySize = 256;
	public static int profEntrySize = 8;
	
	
	//Must define array size here, weird limitation.
	/**Hot methods chose by the estimated future execution time*/
	public static Boolean[] longMethods = new Boolean[0];
	/**
	 * Methods that are considered as hot, but very short. they are chose 
	 * to be hot methods because of invocation frequency. eg. get() and set()
	 * It has three status: 
	 * 1. true means it's short method
	 * 2. false means it's long method
	 * 3. null means it hasn't been calculated
	 */
	public static Boolean[] shortMethod = new Boolean[0];
	/**
	 * Record the times of methods are out of time rage that considers hot method 
	 */
	public static int[] shortMethodJudge = new int[0];
	/**For debug*/
//	public static double[] hotMethodExeTime = new double[0]; 
	
	//Initializing two dimensional arrays is different with 
	//initialization of one dimensional array. Weird again...
	/**Record L3CacheMissRates and TLBMissesRates for scaling recognition*/
	public static double[][] longMethodsByEventCounter;
	/**Record event counters: L3CacheMisses, TLBMisses and WallClockTime*/
	public static double[][] eventCounterQueue;
	
	/**
	 * Energy measurement utility for AOS 7/22/15
	 * If the current method has not been considered as hot 
	 * method by future execution time in previous stage of AOS
	 */
	public static boolean eventCounterIsEmpty(int event, int cmid) {
		return longMethodsByEventCounter[event][cmid] == 0;
	}
	/**
	 * If the current method is not considered as hot method 
	 * (could be the method hasn't been sampled in the previous 
	 * stage of AOS) or the longMethodsByEventCounter has not been 
	 * initialized.
	 */
	public static boolean isOutOfBoundary(int event, int cmid) {
		return longMethodsByEventCounter == null ? true : ProfileQueue.longMethodsByEventCounter[event].length <= cmid;
//		return longMethodsByEventCounter == null || ProfileQueue.longMethodsByEventCounter[0].length <= cmid;
	}
	
	public static void initHotMethodQueue() {
		if(longMethods.length == 0) {
			longMethods = new Boolean[entrySize + (entrySize >>> 2)];
			shortMethod = new Boolean[entrySize + (entrySize >>> 2)];
//			hotMethodExeTime = new double[1000000];
		}
	}
	
	public static void initSkippableMethod() {
//		shortMethodJudge = new int[entrySize + (entrySize >>> 2)];
		shortMethod = new Boolean[entrySize + (entrySize >>> 2)];
	}
	
	/**
	 * Initialize matrix for correlated calculation
	 */
	public static void initCorrelationMatrix() {
		if (matrix == null) {
			matrix = new double[MATRIX_COL_SIZE][entrySize + (entrySize >>> 2)];
		}
	}
	
	public static void initEventCounterQueue() {
    	/*
    	 * Difference between longMethodsByEventCounter and eventCounterQueue is when
    	 * ENABLE_SCALING_BY_COUNTERS is set to be true by user, longMethodsByEventCounter
    	 * would have cache-rates and TLB-misses entries.
    	 */
		if (longMethodsByEventCounter == null) {
			longMethodsByEventCounter = new double[Scaler.getTotalEventNum()][entrySize + (entrySize >>> 2)];
		}
		if (eventCounterQueue == null) {
			eventCounterQueue = new double[Scaler.getPerfEnerCounterNum()][entrySize + (entrySize >>> 2)];
		}
//		VM.sysWriteln("test if the initialization is successfule: " + eventCounterQueue[0].length);
//		if(longMethodsByEventCounter[0].length == 0) {
//			longMethodsByEventCounter = new double[Scaler.cacheTLBEvents.length * EnergyCheckUtils.socketNum][entrySize + (entrySize >>> 2)];
//		}
//		if(eventCounterQueue[0].length == 0) {
//			eventCounterQueue = new double[Scaler.cacheTLBEvents.length * EnergyCheckUtils.socketNum][entrySize + (entrySize >>> 2)];
//		}
	}
	
	/**
	 * @param column columns of matrix represents values of variables to be correlated
	 * @param element
	 * @param value
	 */
	public static synchronized void insertToMatrix(int column, int cmid, double value) {
		if(value >= matrix[column].length) {
			growMatrixSize(value);
		}
		matrix[column][cmid] = value;
	}
	
	public static synchronized void insertToEventCounterQueue(int cmid, int event, double value) {
		if (cmid >= eventCounterQueue[event].length) {
			growEventCounterQueueSize(cmid);
		}
		eventCounterQueue[event][cmid] = value;
	}
	
	public static void growMatrixSize(double value) {
		double[][] newMatrix = new double[MATRIX_COL_SIZE][Math.max(matrix[0].length + 1, (int) (matrix[0].length * 1.25))];
		for (int i = 0; i < MATRIX_COL_SIZE; i++) {
			System.arraycopy(matrix[i], 0, newMatrix[i], 0, matrix[i].length);
			matrix[i] = newMatrix[i];
		}
	}
	
	public static double getHotMethodCounter(int cmid, int event) {
		return longMethodsByEventCounter[event][cmid];
	}
	
	public static double getEventCounter(int cmid, int event) {
//			VM.sysWriteln("in getEventCounter: ", eventCounterQueue[event][cmid]);	
		return eventCounterQueue[event][cmid];
	}
	
	/**Get cmid column size of event counter queue, it's not the size of cmid*/
	public static int getEventCMIDSize() {
		return eventCounterQueue[0].length;
	}
	
	//Insert event counter rates
	//TODO: Accumulate the rates maybe better?
	public static synchronized void insertToNonskippableMethodsByEventCounter (int cmid, int event, double value) {
		if(cmid >= longMethodsByEventCounter[event].length) {
			growNonskippableMethodsByEventCounterSize(cmid);
		}
		longMethodsByEventCounter[event][cmid] = value;
		//Test
//		if(longMethodsByEventCounter[event][cmid] > 0) {
//			VM.sysWriteln("Kenan: the current method has been profiled before!!");
//		}
	}

	public static synchronized void insertToNonskippableMethods (int cmid) {
		if(cmid >= longMethods.length) {
			growNonskippableMethodByExeTimeSize(cmid);
		}
		longMethods[cmid] = true;
	}
	
	/**
	 * Set the method to be short method
	 * @param cmid Compiled method ID
	 */
	public static synchronized void setSkippableMethod(int cmid) {
		if(cmid >= shortMethod.length) {
			growSkippableMethod(cmid);
		}
		shortMethod[cmid] = true;
	}
	
	/**
	 * Set the method to be long method
	 * @param cmid Compiled method ID
	 */
	public static synchronized void setNonskippableMethod(int cmid) {
		if(cmid >= shortMethod.length) {
			growSkippableMethod(cmid);
		}
		shortMethod[cmid] = false;
	}
	
	/**
	 * Increase the times of methods that 
	 * @param cmid
	 */
	public static synchronized void increaseSkippableMethodJudge(int cmid, double totalWallClockTime) {
		if(cmid >= shortMethodJudge.length) {
			growSkippableMethodJudge(cmid);
		}
		shortMethodJudge[cmid] += 1;
	}
	
	/**
	 * If the current method needs to be inserted to shortMethod.
	 * @param cmid
	 * @return
	 */
	public static synchronized boolean toBeInsertedToSkippableList(int cmid) {
		if(shortMethodJudge.length > cmid && shortMethodJudge[cmid] >= SHORT_METHOD_THRESHOLD) {
			return true;
		} else {
			return false;
		}
	}
	
	public static Boolean isSkippableMethod(int cmid) {
		if(shortMethod.length <= cmid)
			return null;
		return shortMethod[cmid];
	}
	
	public static boolean isNonskippableMethod(int cmid) {
		if(longMethods.length <= cmid)
			return false;
		return longMethods[cmid];
	}
	
	/**
	 * Grow the size of shortMethodJudge
	 * @param cmid
	 */
	public static void growSkippableMethodJudge(int cmid) {
		int [] newJudge = new int[Math.max(cmid + 1, (int) (shortMethodJudge.length * 1.25))];
		System.arraycopy(shortMethodJudge, 0, newJudge, 0, shortMethodJudge.length);
		shortMethodJudge = newJudge;
	}
	
	/**
	 * Grow the size of shortMethod
	 * @param cmid
	 */
	public static void growSkippableMethod(int cmid) {
		Boolean[] newQueue = new Boolean[Math.max(cmid + 1, (int) (shortMethod.length * 1.25))];
		System.arraycopy(shortMethod, 0, newQueue, 0, shortMethod.length);
		shortMethod = newQueue;
	}
	
	public static void growNonskippableMethodsByEventCounterSize(int cmid) {
		double[][] newQueue = new double[Scaler.getTotalEventNum()][Math.max(cmid + 1, (int) (longMethodsByEventCounter[0].length * 1.25))];
		for (int i = 0; i < Scaler.getTotalEventNum(); i++) {
			System.arraycopy(longMethodsByEventCounter[i], 0, newQueue[i], 0, longMethodsByEventCounter[i].length);
			longMethodsByEventCounter[i] = newQueue[i];
		}
	}
	
	public static void growEventCounterQueueSize(int cmid) {
		double[][] newQueue = new double[Scaler.getPerfEnerCounterNum()][Math.max(cmid + 1, (int) (eventCounterQueue[0].length * 1.25))];
		for (int i = 0; i < Scaler.getPerfEnerCounterNum(); i++) {
			System.arraycopy(eventCounterQueue[i], 0, newQueue[i], 0, eventCounterQueue[i].length);
			eventCounterQueue[i] = newQueue[i];
		}
	}
	
	public static void growNonskippableMethodByExeTimeSize(int cmid) {
		Boolean[] newQueue = new Boolean[Math.max(cmid + 1, (int) (longMethods.length * 1.25))];
		System.arraycopy(longMethods, 0, newQueue, 0, longMethods.length);
		longMethods = newQueue;
	}
	
	/**
	 * Naive implementation for energy measurement in IR
	 */

	public static long getMethodProfSize(int sid) {
		InitProfQueue(EnergyCheckUtils.socketNum);
		return methodProfSize[sid];
	}
	
	public static void InitProfQueue(int socketNum) {
		
		if ( methodProfQueue == null) {
			methodProfQueue = new long[entrySize * socketNum][50];
			methodProfSize = new int[entrySize * socketNum];
			for(int i = 0; i < entrySize * socketNum; i++)
				methodProfSize[i] = 0;
		}
	}
	
	public static void addEntry(int sid, long value) {
		InitProfQueue(EnergyCheckUtils.socketNum);
		
		//Check boundary of stack
		if (methodProfSize[sid] == methodProfQueue[sid].length - 1) {
			long[][] newQueue = new long[methodProfQueue.length][methodProfSize[sid] *2];
			//Note methodProfSize array start records from 0
			if (VM.VerifyAssertions) 
				VM._assert(methodProfQueue[sid].length == methodProfSize[sid] + 1, "ProfileQueue error");
			
			System.arraycopy(methodProfQueue[sid],0,newQueue[sid],0,methodProfSize[sid] + 1);
			methodProfQueue[sid] = newQueue[sid];

		}
		methodProfQueue[sid][methodProfSize[sid]++] = value;
	}

}
