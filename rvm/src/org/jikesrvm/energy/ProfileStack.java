package org.jikesrvm.energy;

import org.jikesrvm.VM;

public class ProfileStack implements ProfilingTypes {
	
	public static int methodCount = 0;
	public static int entrySize = 200;
	public static int methodIndexSize = 1000;
	public static final int THREADSIZE = 256;
	public static int socketNumber = 1;
	/**Threshold of number of recursive calls*/
	public static final int recursThreshold = 10;
	public static boolean[][][] isOverRecursive;
	/**
	 * For calculating profiling information
	 * 1st dimension: eventId
	 * 2nd dimension: threadId
	 * 3rd dimension: methodId
	 * 4th dimension: stackId
	 */
	public static double[][][][] stack;
	/**
	 * Stack size for each method. It doesn't need take eventId and threadId
	 * into consideration, since one method is invoked in one thread of certain 
	 * event, it will be, more likely, invoked by other threads and events.
	 */
	public static int[][][] stackSize;
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
	/**
	 * Record the number of recursive calls. If the method is recursive called
	 * over certain times, we only measure the beginning of the first recursive 
	 * call and the end of the last recursive call.
	 */
	public static int[][][] recursiveCounter;
	
	public static void InitStack(int socketNum) {
		// stack stores count, wallclocktime, index, cpuTime, userTime, DramEnergy, CpuEnergy, PkgEnergy
		socketNumber = socketNum;
		isOverRecursive = new boolean[Scaler.getPerfEnerCounterNum()][THREADSIZE][entrySize * socketNum];
		methodIndex = new int[methodIndexSize];
		recursiveCounter = new int[Scaler.getPerfEnerCounterNum()][THREADSIZE][entrySize * socketNum];
		stack = new double[Scaler.getPerfEnerCounterNum()][THREADSIZE][entrySize * socketNum][50];
		stackSize = new int[Scaler.getPerfEnerCounterNum()][THREADSIZE][entrySize * socketNum];
		VM.sysWriteln("methodIndex: " + methodIndex.length + " stack: " + stack.length + " stackSize: " + stackSize.length);

	}
	
	public static void growMethodIndex(int index) {
		int[] newMethodIndex = new int[(int)Math.max(index + 1, (int)(methodIndex.length * 1.25))];
		System.arraycopy(methodIndex, 0, newMethodIndex, 0, methodIndex.length);
		methodIndex = newMethodIndex;
		
	}
	
	/**
	 * Once stack size of one methodId exceeds the stack boundary, increase the stack size for 
	 * all threads and events of this method stack
	 * @param methodId
	 */
	
	public static void growStackSize(int eventId, int threadId, int methodId) {
//		VM.sysWriteln("grow Stack size is invoked!!!!!");
		// VM.sysWriteln("stack size: " + stackSize[sid] + " stack lenth " +
		// stack[sid].length);
		double[] newStack = new double[(int)(stackSize[eventId][threadId][methodId] * 1.25)];

		for(int j = 0; j < stack.length; j++) {
			for(int k = 0; k < stack[0].length; k++) {
				System.arraycopy(stack[j][k][methodId], 0, newStack, 0, stack[j][k][methodId].length);
				stack[j][k][methodId] = newStack;
			}
		}
	}
	
	/**
	 * Grow size of threadId and methodId on stack.
	 * @param eventId
	 * @param threadId
	 * @param methodId
	 */
	public static void growStack(int eventId, int threadId, int methodId) {
		double[][][][] newStack;
		int[][][] newRecursiveCounter;
		//Expand method list column size first
		if (threadId < stack[eventId].length && methodId >= stack[eventId][threadId].length) {
			//Only methodId is out of the stack boundary
			newStack = new double[Scaler.getPerfEnerCounterNum()][stack[eventId].length][(int)(methodId * 1.25)][50];
			newRecursiveCounter = new int[Scaler.getPerfEnerCounterNum()][stack[eventId].length][(int)(methodId * 1.25)];
		} else if (threadId >= stack[eventId].length) {
			//Only methodId is out of the stack boundary
			newStack = new double[Scaler.getPerfEnerCounterNum()][(int)(threadId * 1.25)][stack[eventId][0].length][50];
			newRecursiveCounter = new int[Scaler.getPerfEnerCounterNum()][(int)(threadId * 1.25)][stack[eventId][0].length];
			
			if (methodId >= newRecursiveCounter[eventId][threadId].length) {
				//Both methodId and threadId are out of the stack boundary
				newStack = new double[Scaler.getPerfEnerCounterNum()][newStack[eventId].length][(int)(methodId * 1.25)][50];
				newRecursiveCounter = new int[Scaler.getPerfEnerCounterNum()][newRecursiveCounter[eventId].length][(int)(methodId * 1.25)];
			}
		} else {
			return;
		}

		for(int j = 0; j < stack.length; j++) {
			for(int k = 0; k < stack[j].length; k++) {
				//Increase recursiveCounter
				System.arraycopy(recursiveCounter[j][k], 0, newRecursiveCounter[j][k], 0, recursiveCounter[j][k].length);
				recursiveCounter[j][k] = newRecursiveCounter[j][k];
				for (int i = 0; i < stack[j][k].length; i++) {
					//Initialize the stack size of methods that are nested invoked more than original stack size, and copy its contents 
					if(methodIndex[i] < stack[j][k].length) {
						//Check if the current method is smaller than method number of stack
						int stackSize = stack[j][k][methodIndex[i]].length;
	//					VM.sysWriteln("i is: " + i + " j is: " + j + " k is: " + k);
	//					VM.sysWriteln("newStack.length is: " + newStack.length + " newStack[i].length is: " + newStack[j].length + " newStack[i][k].length is: " + newStack[j][k].length);
						int newStackSize = newStack[j][k][methodIndex[i]].length;
						//Reinitialize newstack for the method that has been invoked more than 50 times
						if(stackSize > newStackSize) {
							double[] newSize = new double[stackSize];
							newStack[j][k][methodIndex[i]] = newSize;
						}
					}
					//Copy the methods that have been pushed on stack only
					System.arraycopy(stack[j][k][methodIndex[i]], 0, newStack[j][k][methodIndex[i]], 0, stack[j][k][methodIndex[i]].length);
					stack[j][k][methodIndex[i]] = newStack[j][k][methodIndex[i]];
				}
				stack[j] = newStack[j];
				recursiveCounter[j] = newRecursiveCounter[j];
//				VM.sysWriteln("stack.length is: " + stack.length + " stack[j].length is: " + stack[j].length + " stack[j][k].length is: " + stack[j][k].length);
			}
		}

	}
	
	public static boolean isEmpty(int eventId, int threadId, int methodId) {
		return stackSize[eventId][threadId][methodId] == 0 ? true : false;
	}
	
	public static long getStackSize(int eventId, int threadId, int methodId){
//		InitStack(EnergyCheckUtils.socketNum);
		return stackSize[eventId][threadId][methodId];
	}
	
	/**
	 * Grow size of stackSize and isOverRecursive 
	 * @param eventId
	 * @param threadId
	 * @param methodId
	 */
	public static void grow(int eventId, int threadId, int methodId) {
		// Note stackSize array start records from 0
		int[][][] newStackSize;
		boolean[][][] newIsOverRecursive;
		//When methodId is out of boundary, increase the size of stackSize
		if (threadId < stack[eventId].length && methodId >= stack[eventId][threadId].length) {
			//Only methodId is out of the stack boundary
			newIsOverRecursive = new boolean[Scaler.getPerfEnerCounterNum()][stack[eventId].length][(int)(methodId * 1.25)];
			newStackSize = new int[Scaler.getPerfEnerCounterNum()][stack[eventId].length][(int)(methodId * 1.25)];
		} else if (threadId >= stack[eventId].length) {
			//Only threadId is out of the stack boundary
			newIsOverRecursive = new boolean[Scaler.getPerfEnerCounterNum()][(int)(threadId * 1.25)][stack[eventId][0].length];
			newStackSize = new int[Scaler.getPerfEnerCounterNum()][(int)(threadId * 1.25)][stack[eventId][0].length];
			
			if (methodId >= newIsOverRecursive[eventId][threadId].length) {
				//Both methodId and threadId are out of the stack boundary
				newIsOverRecursive = new boolean[Scaler.getPerfEnerCounterNum()][newIsOverRecursive[eventId].length][(int)(methodId * 1.25)];
				newStackSize = new int[Scaler.getPerfEnerCounterNum()][newStackSize[eventId].length][(int)(methodId * 1.25)];
			}
		} else {
//			VM.sysWriteln("No need to increase size for any dimensions!");
			return;
		}
		for(int j = 0; j < stack.length; j++) {
			for(int k = 0; k < stack[j].length; k++) { 
				System.arraycopy(stackSize[j][k], 0, newStackSize[j][k], 0, stackSize[j][k].length);
				stackSize[j][k] = newStackSize[j][k];
//				VM.sysWriteln("size of the stack after increasing for stackSize: " + stackSize[j][k].length);
				//Increase recursiveCounter
				System.arraycopy(isOverRecursive[j][k], 0, newIsOverRecursive[j][k], 0, isOverRecursive[j][k].length);
				isOverRecursive[j][k] = newIsOverRecursive[j][k];
			}
			stackSize[j] = newStackSize[j];
			isOverRecursive[j] = newIsOverRecursive[j];
		}

//		VM.sysWriteln("New size initialization for stack: " + stack[eventId][threadId].length 
//				+ " New size for stackSize: " + stackSize[eventId][threadId].length  
//				+ " Size for methodIndex: " + methodIndex.length + " methodId is: " + methodId);
	}
	
	public static synchronized void push(int eventId, int threadId, int methodId, double value) {
		/*Check boundary of stack*/
		//Check the boundary of method index array
		if(methodCount == methodIndex.length) {
			growMethodIndex(methodCount);
//			VM.sysWriteln("grow method index is invoked!!!!!");
		}
		grow(eventId, threadId, methodId);
		//Check the boundary of eventId, threadId and methodId, if the stack size is over
		//a threshold, ignore the following method invocations.
//		VM.sysWriteln("stackSize thread size: " + stackSize[eventId].length + " thead: " + threadId);
//		VM.sysWriteln("stackSize method size: " + stackSize[eventId][threadId].length + " methodID: " + methodId);
//		VM.sysWriteln("isOverRecursive thread size: " + isOverRecursive[eventId].length + " thead: " + threadId);
//		VM.sysWriteln("isOverRecursive method size: " + isOverRecursive[eventId][threadId].length + " methodID: " + methodId);
		if(stackSize[eventId][threadId][methodId] < recursThreshold - 1 && !isOverRecursive[eventId][threadId][methodId]) {
			growStack(eventId, threadId, methodId);
		} else if(stackSize[eventId][threadId][methodId] == recursThreshold - 1 && !isOverRecursive[eventId][threadId][methodId]) {
			//Once stack size of the method is more than a certain threshold, drop the 
			//following measurements until hits the endSampling of the last recursive call.
			recursiveCounter[eventId][threadId][methodId] = recursThreshold - 1;
			//Decrease stack size as 1, so the index of method stack is also changed to 1
			stackSize[eventId][threadId][methodId] = 1;
			isOverRecursive[eventId][threadId][methodId] = true;
		} else if(isOverRecursive[eventId][threadId][methodId]) {
			//If the method is over the threshold of stack size, record the following invocation counters
			recursiveCounter[eventId][threadId][methodId]++;
			return;
		} else if (stackSize[eventId][threadId][methodId] == stack[eventId][threadId][methodId].length - 1) {
			//Check the boundary of stackSize for specified method. (once one methodId
			//is out of boundary, increase size for all threads and events of this method)
			growStackSize(eventId, threadId, methodId);
		}
		methodIndex[methodCount++] = methodId;
//		VM.sysWriteln("stack.length is: " + stack.length + " stack[eventId].length is: " + stack[eventId].length + " stack[eventId][threadId].length is: " + stack[eventId][threadId].length);
//		VM.sysWriteln("stackSize.length is: " + stackSize.length + " stackSize[eventId].length is: " + stackSize[eventId].length + " stackSize[eventId][threadId].length is: " + stackSize[eventId][threadId].length);
		stack[eventId][threadId][methodId][stackSize[eventId][threadId][methodId]++] = value;
	}

	public static synchronized double pop(int eventId, int threadId, int methodId){
		//Epilogue may be sampled before prologue
//		InitStack(EnergyCheckUtils.socketNum);
		if(methodId < 0 || methodId >= stack[0][0].length)
			VM.sysWriteln("stack overflow sid: " + methodId);
		if (stackSize[eventId][threadId][methodId] <= 0){
			VM.sysWriteln("pop fail: no element to pop");
			return -1;
		} else 
//			VM.sysWriteln("pop value: " + stack[eventId][threadId][methodId][stackSize[eventId][threadId][methodId] - 1]);
		//If the number of current method recursive calls is over the threshold, keep poping until 
		//the last entry on stack.
		if(isOverRecursive[eventId][threadId][methodId]) {
			recursiveCounter[eventId][threadId][methodId]--;
			if(recursiveCounter[eventId][threadId][methodId] == 0) {
				//Pop until the last entry (first recursive call)
				isOverRecursive[eventId][threadId][methodId] = false;
				stackSize[eventId][threadId][methodId] = 0;
				VM.sysWriteln("value in endsampling: " + stack[eventId][threadId][methodId][stackSize[eventId][threadId][methodId]]);
				return stack[eventId][threadId][methodId][stackSize[eventId][threadId][methodId]];
			}
			return -1.0;
		}
		return stack[eventId][threadId][methodId][--stackSize[eventId][threadId][methodId]];
	}
	
	public static double peak(int eventId, int threadId, int methodId) {
		if(stackSize[eventId][threadId][methodId] - 1 >= 0) {
			return stack[eventId][threadId][methodId][stackSize[eventId][threadId][methodId] - 1];
		} else {
			VM.sysWriteln("the stack of current method is empty!");
			return -1.0d;
		}
	}
	
	public static void dump() {
		for(int j = 0; j < stack.length; j++) {
			for(int k = 0; k < stack[j].length; k++) {
				for (int i = 0; i < methodIndex.length; i++) {
					for(int h = 0; h < stackSize[j][k][i]; h++) {
						VM.sysWriteln("stack entry number is: " + stack[j][k][i][h]);
					}
				}
			}
		}
	}
}
