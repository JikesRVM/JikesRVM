package org.jikesrvm.energy;

import java.lang.management.*;
import java.util.Random;

public class TimeCheckUtils {

	private static long totalTime = -1L;
	private static long[] timeInfo = new long[2];
	private static long totalCpuTime = -1L;
	private static long totalUserTime = -1L;

	public TimeCheckUtils() {
		totalTime = -1L;
		totalCpuTime = -1L;
		totalUserTime = -1L;
		timeInfo[0] = -1L;
		timeInfo[1] = -1L;
	}
	/********Get current thread time info*************/

	public static long getCurrentThreadCpuTime() {						//Total CPU time usage
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		return bean.isCurrentThreadCpuTimeSupported() ? bean.getCurrentThreadCpuTime() : -1L;
	}

	public static long getCurrentThreadKernelTime() {					//Kernel mode thread CPU time usage? maybe include context switch
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		return bean.isCurrentThreadCpuTimeSupported() ? bean.getCurrentThreadCpuTime() - bean.getCurrentThreadUserTime() : -1L;
	}
	
	public static long getCurrentThreadUserTime() {						//User level thread CPU time usage
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		return bean.isCurrentThreadCpuTimeSupported() ? bean.getCurrentThreadUserTime() : -1L;
	}

	/**
	 * @return  First elements: User mode time. Second elements: CPU time.
	 */
	public static long[] getCurrentThreadTimeInfo() {						//Get time info for current thread
		
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		if(!bean.isCurrentThreadCpuTimeSupported()) {
//			System.out.println("***********current thread cpu time is not supported!!!!***********");
			return timeInfo;
		}
		timeInfo[0] = bean.getCurrentThreadUserTime();
		timeInfo[1] = bean.getCurrentThreadCpuTime();
		
		return timeInfo;
	}

	/********Get specified thread time info*************/

	public static long getThreadCpuTime(long tid) {
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		if(!bean.isCurrentThreadCpuTimeSupported())
			System.out.println("current thread doesn't support cpu time");
		return bean.isThreadCpuTimeSupported() ? bean.getThreadCpuTime(tid) : -1L;
	}

	public static long getThreadUserTime(long tid) {
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		return bean.isThreadCpuTimeSupported() ? bean.getThreadUserTime(tid) : -1L;
	}


	public static long getThreadKernelTime(long tid) {
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		return bean.isThreadCpuTimeSupported() ? bean.getThreadUserTime(tid) : -1L;
	}

	/**
	 * @return  First elements: User mode time. Second elements: CPU time.
	 */
	public static long[] getThreadTimeInfo(long tid) {						//Get time info for the specified thread 
		
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		if(!bean.isThreadCpuTimeSupported()) 
			return timeInfo;

		timeInfo[0] = bean.getThreadUserTime(tid);
		timeInfo[1] = bean.getThreadCpuTime(tid);
		
		return timeInfo;
	}

	/********Get number of threads time info*************/

	public static long getTotalThreadCpuTime(long[] tids) {				//get multiple threads cpu time usage to prevent overhead for multiple 
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();		//method invocations of getThreadCpuTime(long tid)
		if(!bean.isThreadCpuTimeSupported()) 							//If JVM supports for CPU time measurement for any thread, it returns true
			return -1L;
		for(long i : tids) {
			long currentTime = bean.getThreadCpuTime(i);
			if(currentTime != -1) {										//Check if the thread CPU measurement is enableed.
				totalTime += currentTime;
			}
		}
		return totalTime;
	}

	public static long getTotalThreadUserTime(long[] tids) {			//Get multiple threads user time usage			 
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();		
		if(!bean.isThreadCpuTimeSupported()) 							
			return -1L;
		
		for(long i : tids) {
			long currentTime = bean.getThreadUserTime(i);
			if(currentTime != -1) {										
				totalTime += currentTime;
			}
		}
		return totalTime;
	}

	public static long getTotalThreadKernelTime(long[] tids) {			//Get multiple threads Kernel time usage	 
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();		
		if(!bean.isThreadCpuTimeSupported()) 							
			return -1L;
		
		for(long i : tids) {
			long currentCpuTime = bean.getThreadCpuTime(i);
			long currentUserTime = bean.getThreadUserTime(i);
			if(currentCpuTime != -1 && currentUserTime != -1) {										
				totalTime += (currentCpuTime - currentUserTime);
			}
		}
		return totalTime;
	}
	
	/**
	 * @return  First elements: User mode time. Second elements: CPU time.
	 */
	public static long[] getTotalThreadTimeInfo(long[] tids) {			//Get time info  for multiple threads time information 

		ThreadMXBean bean = ManagementFactory.getThreadMXBean();		
		if(!bean.isThreadCpuTimeSupported()) 							
			return timeInfo;
		for(long i : tids) {
			long currentUserTime = bean.getThreadUserTime(i);
			long currentCpuTime = bean.getThreadCpuTime(i);
			if(currentCpuTime != -1) {										
				totalCpuTime += currentCpuTime;
				totalUserTime += currentUserTime;
			}
		
		}
		timeInfo[0] = totalUserTime;
		timeInfo[1] = totalCpuTime;
		
		return timeInfo;

	}

	public static void main(String[] args) {
		String start, end;
		String[] preamble = null;
		String[] epilogue = null;
		long cpuTime, userTime, kernelTime;
		long wallClockStart, wallClockEnd, wallClockTimeUse;
		Random rand = new Random();
		int randNum;
		int option = 2;
		wallClockStart = System.currentTimeMillis();	
		
		if(option == 1) {
			for(int i = 0; i < 10000000; i++) {
				for(int j = 0; j < 100; j++) {
					randNum = rand.nextInt()%1000;
				}
			}
		} else if(option == 2) {
			for(int i = 0; i < 10000000; i++) {
				//for(int j = 0; j < 10; j++) {
					System.out.println("++++");
				//}
			}
		}
		wallClockEnd = System.currentTimeMillis();	
			
			userTime = Long.parseLong(epilogue[0]) - Long.parseLong(preamble[0]);
			cpuTime = Long.parseLong(epilogue[1]) - Long.parseLong(preamble[1]);
			kernelTime = cpuTime - userTime;
			System.out.println("CPU time usage: " + cpuTime/1000000);
			System.out.println("user time usage: " + userTime/1000000);
			System.out.println("kernel time usage: " + kernelTime/1000000);
		wallClockTimeUse = wallClockEnd - wallClockStart;
		System.out.println("Wall clock time usage: " + wallClockTimeUse);
	}
}
