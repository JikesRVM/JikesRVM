package org.jikesrvm.energy;

import org.jikesrvm.VM;

public class ProfileStack implements ProfilingTypes {
	
	public static int methodCount = 0;
	public static int entrySize = 1000000;
	public static int methodIndexSize = 1000;
	/**
	 * For calculting profiling information
	 */
	public static double[][] stack;
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
		if (stack == null){
			methodIndex = new int[methodIndexSize];
			stack = new double[entrySize * socketNum][50];
			stackSize = new int[entrySize * socketNum];
			VM.sysWriteln("methodIndex: " + methodIndex.length + " stack: " + stack.length + " stackSize: " + stackSize.length);

		}
	}
	
	public static void growMethodIndex(int index) {
		int[] newMethodIndex = new int[(int)Math.max(index + 1, (int)(methodIndex.length * 1.25))];
		System.arraycopy(methodIndex, 0, newMethodIndex, 0, methodIndex.length);
		methodIndex = newMethodIndex;
		
	}
	
	/**
	 * Increase size of first column of stack. 
	 * In order to do this, we also need to initialize varied size of second column respectively.
	 * @param sid
	 */
	public static void growMethodListSize(int sid) {
		//Expand method list column size first
		double[][] newStack = new double[Math.max(sid + 1, (int)(stack.length * 1.25))][50];
		VM.sysWriteln("stack size has been increased, it's :" + newStack.length);
		//Initialize varied stack size for the index only
		for (int i = 0; i < methodIndex.length; i++) {
			//Reinitialize newstack for the method that has been invoked more than 50 times
			if(stack[methodIndex[i]].length > 50) {
				newStack[i] = new double[stack[methodIndex[i]].length];
			}
			//Copy the methods that have been pushed on stack only
			System.arraycopy(stack[methodIndex[i]], 0, newStack[methodIndex[i]], 0, stack[methodIndex[i]].length);
			stack[i] = newStack[i];
		}
		stack = newStack;
		
		int[] newStackSize = new int[Math.max(sid + 1, (int)(sid * 1.25))];

		// Note stackSize array start records from 0
		System.arraycopy(stackSize, 0, newStackSize, 0, stackSize.length);
		stackSize = newStackSize;
		VM.sysWriteln("New size initialization for stack: " + stack.length 
				+ " New size for stackSize: " + stackSize.length  
				+ " Size for methodIndex: " + methodIndex.length + " SID is: " + sid);
	
	}
	
	public static void growStackSize(int sid) {

		// VM.sysWriteln("stack size: " + stackSize[sid] + " stack lenth " +
		// stack[sid].length);

		double[] newStack = new double[Math.max(sid + 1, (int)(sid * 1.25))];
		// Note stackSize array start records from 0
		if (VM.VerifyAssertions)
			VM._assert(stack[sid].length == stackSize[sid] + 1,
					"ProfileStack error");
		System.arraycopy(stack[sid], 0, newStack, 0, stack[sid].length);
		stack[sid] = newStack;

	}
	
	public static boolean isEmpty() {
		return stackSize[COUNT] == 0 ? true : false;
	}
	
	public static long getStackSize(int sid){
//		InitStack(EnergyCheckUtils.socketNum);
		return stackSize[sid];
	}
	
	public static void push(int sid, double value) {
		// Check boundary of stack
		if(methodCount == methodIndex.length) {
			growMethodIndex(methodCount);
		}
		if(sid >= stackSize.length) {
			growMethodListSize(sid);
		}
		if (stackSize[sid] == stack[sid].length - 1) {
			growStackSize(sid);
		}
		methodIndex[methodCount++] = sid;
		synchronized(stackSize) {
			stack[sid][stackSize[sid]++] = value;
		}

	}

	public static double pop(int sid){
		//Epilogue may be sampled before prologue
//		InitStack(EnergyCheckUtils.socketNum);
		if(sid < 0 || sid >= stack.length)
			VM.sysWriteln("stack overflow sid: " + sid);
		if (stackSize[sid] <= 0){
			VM.sysWriteln("pop fail: no element to pop");
			return -1;
		} else 
			VM.sysWriteln("pop value: " + stack[sid][stackSize[sid] - 1]);
		return stack[sid][--stackSize[sid]];
	}
	
	public static double peak(int sid) {
		return stack[sid][stackSize[sid] - 1];
	}
	
	public static void dump(int sid) {
		for(int i = 0; i < stackSize[sid]; i++) {
//			VM.sysWriteln(stack[sid][i]);
		}
	}
}
