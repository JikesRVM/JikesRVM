package org.jikesrvm.energy;

import org.jikesrvm.VM;

/**
 * Record the method information for print out in the end of program.
 * This class should be used in shutdown() method of VM.java
 */
public class PrintoutQueue implements ProfilingTypes {
	public static PrintProfile[][] printoutQueue;
	public static int[] size;

	public static void initPrintoutQueue() {
		printoutQueue = new String[10][500];	
		size = new int[printoutQueue.length];
	}

	public static void growSize(int threadId) {
		int[] newSize;
		if (threadId >= size.length) {
			newSize = new int[size.length * 1.25];
		}

	}

	public static void growQueueSize(int threadId, int[] size) {
		PrintProfile[][] newQueue;
		size[threadId]++;	

		size = Math.max(PrintProfile[threadId].length, size[threadId]); 

		if (threadId >= printoutQueue.length && size >= printoutQueue[threadId].length) {
			newQueue = new PrintProfile[printoutQueue.length * 1.25][printoutQueue[threadId].length * 1.25];
			System.arraycopy(printoutQueue, 0, newQueue, 0, printoutQueue.length);
			printoutQueue = newQueue;
		} else if (threadId >= printoutQueue.length) {
			newQueue = new PrintProfile[printoutQueue.length * 1.25][printoutQueue[threadId].length];
		} else if (size >= printoutQueue[threadId].length) {

		}
	}

	/**
	 * Add profiling information in the array
	 * @param threadId thread ID 
	 * @param profInfo profiling information 
	 */
	public static void add(int threadId, PrintProfile profInfo) {

		growQueueSize(threadId, size);
				
	}

}

