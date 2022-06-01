package org.jikesrvm.energy;

import org.jikesrvm.VM;
import java.util.List;
import java.util.LinkedList;
import java.util.ArrayList;
import org.jikesrvm.adaptive.controller.Controller;

/**
 * Almost the same as ProfileStack, This class must be used with thread safe way since 
 * its methods are not synchronzed.
 */
public class LogQueue implements ProfilingTypes {
	
	/**Record the log entry in start profiling stage*/
	public static LinkedList<LogEntry> startLogQueue;
	/**Record the log entry in end profiling stage*/
	public static LinkedList<LogEntry> endLogQueue;
	/**Record the post calculated log entry*/
	public static LinkedList<LogEntry> logQueue;

	public static void initRawDataQueue(int socketNum) {
		if (startLogQueue == null) {
			startLogQueue = new LinkedList();
			endLogQueue = new LinkedList();
		}
	}

	public static void initQueue(int socketNum) {
		if (logQueue == null) {
			logQueue = new LinkedList();
		}
	}
	
	/**
	 * Add the profiling value into startLogQueue
	 * @param threadId the corresponding thread ID 
	 * @param methodId the corresponding method ID
	 * @param profileAttrs    the profiling values which needs to be recorded 
	 */
	public static synchronized void addStartLogQueue(int threadId, int methodId, int invocationCounter, double[] profileAttrs) {
		LogEntry entry = new LogEntry(threadId, methodId, invocationCounter, profileAttrs);
		startLogQueue.offer(entry);
	}

	/**
	 * Add the profiling value into endLogQueue
	 * @param threadId the corresponding thread ID 
	 * @param methodId the corresponding method ID
	 * @param profileAttrs    the profiling values which needs to be recorded 
	 */
	public static synchronized void addEndLogQueue(int threadId, int methodId, int invocationCounter, double[] profileAttrs) {
		LogEntry entry = new LogEntry(threadId, methodId, invocationCounter, profileAttrs);
		endLogQueue.offer(entry);
	}

	/**
	 * Add the profiling value into LogQueue
	 * @param threadId the corresponding thread ID 
	 * @param methodId the corresponding method ID
	 * @param profileAttrs    the profiling values which needs to be recorded 
	 */
	public static void addLogQueue(int threadId, int methodId, double[] profileAttrs, long time, double hotMethodStartTime) {
		LogEntry entry = new LogEntry(threadId, methodId, profileAttrs, time, hotMethodStartTime);
		logQueue.offer(entry);

	}


	/**
	 * Dump the profiling information with the data has been calculated
	 */
	public static void dumpLogQueue(String[] clsNameList, String[] methodNameList) {
		int count = 0;
		int i = 0;
		for (LogEntry entry : logQueue) {
		//for (int i = logQueue.size() - 1; i >= 0; i--) {

			//DataPrinter.filePrinter.println("entry size remains: " + (logQueue.size() - count++) + " index is: " + i);
			//DataPrinter.filePrinter.println("entry size remains: " + (logQueue.size() - count++));
			//double missRate = entry.counters[0] / entry.counters[1];
			//double missRateByTime = entry.counters[0] / totalWallClockTime;
		//	LogEntry entry = logQueue.get(i);
//			if (entry == null) {
//				DataPrinter.filePrinter.println("Index: " + i + " is null");
//				continue;
//			}
//			i++;
			//DataPrinter.filePrinter.println(entry.threadId);
			//DataPrinter.filePrinter.println(entry.counters[entry.counters.length - 1]);


			DataPrinter.printProfInfoTwo(entry.threadId, entry.methodId, clsNameList[entry.methodId] + "." + methodNameList[entry.methodId], Controller.options.FREQUENCY_TO_BE_PRINTED, entry.counters, entry.time, entry.hotMethodStartTime);   
			DataPrinter.filePrinter.flush();
		}
	}

	/**
	 * Dump the profiling information with the data hasn't been calculated
	 */
	public static void dumpWithRawData(String[] clsNameList, String[] methodNameList) {

		while (startLogQueue != null && !startLogQueue.isEmpty()) {

			LogEntry startEntry = startLogQueue.removeFirst();

			VM.sysWriteln(startLogQueue.size());

			//LogEntry startEntry= startLogQueue.poll();
			int entryId = 0;
			for (LogEntry endEntry : endLogQueue) {
				entryId++;
				if (startEntry.threadId == endEntry.threadId && 
					startEntry.invocationCounter == endEntry.invocationCounter) {
					int timeEntry = endEntry.counters.length - 1;
					double totalWallClockTime = endEntry.counters[timeEntry] - startEntry.counters[timeEntry];
					double missRate = (endEntry.counters[0] - startEntry.counters[0]) / (endEntry.counters[1] - startEntry.counters[1]);
					double missRateByTime = (endEntry.counters[0] - startEntry.counters[0]) / totalWallClockTime;
					//DataPrinter.printProfInfoTwo(startEntry, clsNameList[startEntry.methodId] + "." + methodNameList[startEntry.methodId], Controller.options.FREQUENCY_TO_BE_PRINTED, TODO: result array, missRate, missRateByTime);   

					endLogQueue.remove(entryId);
					break;
				}
			}
		}
	}
}
