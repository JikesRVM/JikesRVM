package org.jikesrvm.energy;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.Controller;

public class Service implements ProfilingTypes {
	public static final long THREASHOLD = 500;
	
	public static final boolean changed = false;
	public static boolean isJraplInit = false;
	public static boolean isSamplingInit = false;
	public native static int scale(int freq);
	public native static int[] freqAvailable();
	public static final int INIT_SIZE = 500;
	public static boolean titleIsPrinted = false;
	public static String[] clsNameList = new String[INIT_SIZE];
	public static String[] methodNameList = new String[INIT_SIZE];
	public static long[] methodCount = new long[INIT_SIZE];
	
	/**Index is composed by hashcode of "method ID#thread ID" in order to differentiate method invocations by different threads*/
//	public int index;
	public static char [] info = {'i','o', '\n'};

	public static int currentPos = 0;
	/**
	 * Get a smaller hashcode value than String.hashcode(). Since we only need calculate hashcode for combination of numbers
	 * and '#' only. It's less likely be collided if we use a smaller primes, and it would save much memory.
	 * @return
	 */
	public static int getHashCode(String key) {
		char[] str = key.toCharArray();
		int hash = 0;
        if (str.length > 0) {

            for (int i = 0; i < str.length; i++) {
            	hash = 7 * hash + str[i];
            }
        }
        return hash;
	}

	public static int addMethodEntry(String cls, String name){
		
		//Check boundaries of arrays
		if (methodCount.length - 1 == currentPos){
			int len = methodCount.length;
			String[] newClsNameList = new String[len * 2];
			String[] newMethodNameList = new String[len * 2];
			long[] newMethodCount = new long[len * 2];
			if (VM.VerifyAssertions)
				VM._assert(clsNameList.length - 1 == currentPos && methodNameList.length - 1== currentPos && methodCount.length - 1== currentPos, "Service error");
			
			System.arraycopy(clsNameList,0,newClsNameList,0,len);
			System.arraycopy(methodNameList,0,newMethodNameList,0,len);
			System.arraycopy(methodCount,0,newMethodCount,0,len);
			clsNameList = newClsNameList;
			methodNameList = newMethodNameList;
			methodCount = newMethodCount;
		}
		methodNameList[currentPos] = name;
		clsNameList[currentPos] = cls;
		methodCount[currentPos] = 0;
		currentPos++;
		return currentPos - 1;
	}

	  private static void reInitEventCounterQueue(int cmid) {
		  for(int i = 0; i < Scaler.getPerfEnerCounterNum() - 1; i++) {
			  ProfileQueue.insertToEventCounterQueue(cmid, i, 0);
		  }
		  ProfileQueue.insertToEventCounterQueue(cmid, Scaler.getPerfEnerCounterNum() - 1, 0);
	  }
	  
	public static void startSampling(int cmid){
		double perfCounter;
		long threadId = Thread.currentThread().getId();
		if(!ProfileQueue.isLongMethod(cmid)) {
		//Check if the current method is short method
		  if(ProfileQueue.isShortMethod(cmid)) {
			  return;
		  }
		}
		  //Measure hardware counters
		  /*If the event counter hasn't been inserted with a value, store it, return otherwise.**/
			  double wallClockTime = System.currentTimeMillis();
			  //Get value of counters from perf first.
			  if(!Controller.options.ENABLE_COUNTER_PRINTER) {
				  for(int i = 0; i < Scaler.getPerfEnerCounterNum() - 1; i++) {
					  perfCounter = Scaler.perfCheck(i);
					  if(perfCounter == 0) {
						  VM.sysWriteln("Event value is 0, what happens??");
					  } else {
//						  VM.sysWriteln("event value: " + eventValue);
					  }
					  ProfileStack.push(i, (int)threadId, cmid, perfCounter);
				  }
			  } else {
				  //If counter printer is enabled, the data would be stored as socket1: hardware counters
				  //+ energy consumption + socket 2: hardware counters + energy consumption + socket 3: ...
				  double[] energy = EnergyCheckUtils.getEnergyStats();
				  int counterIndex = 0;
				  int enerIndex = 0;
				  for(int i = 0; i < Scaler.getPerfEnerCounterNum() - 1; i++) {
					  if(i < Scaler.perfCounters) {
						  perfCounter = Scaler.perfCheck(counterIndex);
						  //Insert hardware counters in the first socket
						  if(perfCounter == 0) {
							  VM.sysWriteln("Event value is 0, counter index is: " + counterIndex + "what happens??");
						  }
						  ProfileStack.push(i, (int)threadId, cmid, perfCounter);
						  counterIndex++;
					  } else {
						  for(int j = 0; j < EnergyCheckUtils.ENERGY_ENTRY_SIZE; j++) {
							  if(energy[enerIndex] == 0) {
								  VM.sysWriteln("Energy value is 0, what happens??");
							  }
							  ProfileStack.push(i, (int)threadId, cmid, energy[enerIndex]);  
							  i++;
							  enerIndex++;
						  }
					  }
				  }
			  }
			  ProfileStack.push(Scaler.getPerfEnerCounterNum() - 1, (int)threadId, cmid, wallClockTime);
	}

	public static void endSampling(int cmid){
		
		  double startWallClockTime = 0.0d;
		  double totalWallClockTime = 0.0d;
		  double cacheMissRate = 0.0d;
		  double cacheMissRateByTime = 0.0d;
		  double branchMissRate = 0.0d;
		  double branchMissRateByTime = 0.0d;
		  double tlbMisses = 0.0d;
		  double tlbMissByTime = 0.0d;
		  double contextSwitchByTime = 0.0d;
		  double cpuCycleByTime = 0.0d;
		  double cpuClockByTime = 0.0d;
		  double pageFaultByTime = 0.0d;
		  int offset = 0;
		  /**Event values for the method*/
		  double[] eventEnerValues = new double[Scaler.getPerfEnerCounterNum() - 1];
		  int threadId = (int)Thread.currentThread().getId();
		  
		  //Check if the current method is short method
		if (!ProfileQueue.isLongMethod(cmid)) {
			// Check if the current method is short method
			if (ProfileQueue.isShortMethod(cmid)) {
				return;
			}
		}
//		  if(ProfileQueue.isShortButFreqMethod(cmid)) {
//			  return;
//		  }
//		  If the event counter has been inserted with a value do subtraction and get the rate of events, otherwise, drop it.
		  if(ProfileStack.peak(L3CACHEMISSES, threadId, cmid) > 0) {
			  double wallClockTime = System.currentTimeMillis();
			  
			  startWallClockTime = ProfileStack.pop(Scaler.getPerfEnerCounterNum() - 1, threadId, cmid);
			  //Over recursive call threshold check. If it's a negative value, that means the current method
			  //is invoked itself more than a certain threshold times. We only need care the information of the whole 
			  //recursive calls
			  if(startWallClockTime < 0) {
				  return;
			  }
			  totalWallClockTime = wallClockTime - startWallClockTime;
				//Get greater range since the method execution time varies a lot
				double min = Controller.options.HOT_METHOD_TIME_MIN;
				double max = Controller.options.HOT_METHOD_TIME_MAX;
	
			  //The time usage for the method is less than 100, but is considered as hot method by future execution time.
			  //It's more likely the method is very short but frequently invoked. So we don't need consider
			  //this method for dynamic profiling and scaling next time.
			  if(totalWallClockTime < min || totalWallClockTime >= max) {
				  if(ProfileQueue.toBeInsertedToShortList(cmid)) {
					  ProfileQueue.setShortMethod(cmid);
					  return;
				  } else {
					  ProfileQueue.increaseShortMethodJudge(cmid, totalWallClockTime);		
					  return;
				  }
			  } else {
				  //TODO: Hard-coded if-else for experiment, delete later

				  ProfileQueue.setLongMethod(cmid);

			  }
			  
//			  if(totalWallClockTime < THREASHOLD) {
//				  ProfileQueue.insertToShortButFreqMethods(cmid);
//				  return;
//			  }
			  /**Event counter printer object*/
//			  DataPrinter printer = new DataPrinter(EnergyCheckUtils.socketNum, cmid, clsNameList[cmid] + "." + methodNameList[cmid], 
//								  									totalWallClockTime);
			  if(Controller.options.ENABLE_COUNTER_PRINTER && !titleIsPrinted) {
//				  DataPrinter.printEventCounterTitle(Controller.options.ENABLE_COUNTER_PROFILING, Controller.options.ENABLE_ENERGY_PROFILING);
				  titleIsPrinted = true;
			  }
			  if(!Controller.options.ENABLE_COUNTER_PRINTER) {
				  //Only hardware counters are calculated.
				  for (int i = 0; i < Scaler.getPerfEnerCounterNum() - 1; i++) {
					  eventEnerValues[i] = Scaler.perfCheck(i) - ProfileStack.pop(i, threadId, cmid);
	//				  VM.sysWriteln("Kenan: cmid is: " + cmid 
	//						  				+ " cache misses: " + eventEnerValues[i] 
	//						  				+ " cache misses start: " + ProfileQueue.getEventCounter(cmid, i) 
	//						  				+ " cache misses start: " + ProfileQueue.eventCounterQueue[i][cmid]
	//						  				+ " cache misses end: " + Scaler.perfCheck(i) 
	//						  				+ " wall clock time: " + totalWallClockTime 
	//						  				+ " start wall clock time: " + startWallClockTime 
	//						  				+ " end wall clock time: " + wallClockTime
	//						  				);
				  }
			  } else {
				  
				  //If counter printer is enabled, the data would be stored as socket1: hardware counters
				  //+ energy consumption + socket 2: hardware counters + energy consumption + socket 3: ...
				  double[] energy = EnergyCheckUtils.getEnergyStats();
				  int enerIndex = 0;
				  int counterIndex = 0;
				  for(int i = 0; i < Scaler.getPerfEnerCounterNum() - 1; i++) {
					  if(Controller.options.ENABLE_COUNTER_PROFILING && i < Scaler.perfCounters) {
						  //Insert hardware counters in the first socket
						  eventEnerValues[i] = Scaler.perfCheck(counterIndex) - ProfileStack.pop(i, threadId, cmid);
						  counterIndex++;
					  }  else if(Controller.options.ENABLE_ENERGY_PROFILING) {
						  //Insert Energy consumptions of dram/uncore gpu, cpu and package.
						  VM.sysWrite("serviceArrayStack 229");
						  for(int j = 0; j < EnergyCheckUtils.ENERGY_ENTRY_SIZE; j++) {
							  eventEnerValues[i] = energy[enerIndex] - ProfileStack.pop(i, threadId, cmid);
							  i++;
							  enerIndex++;
						  }
					  } 
//					  VM.sysWriteln("Kenan: cmid is: " + cmid 
//							  				+ " cache misses: " + eventEnerValues[i] 
//							  				+ " cache misses start: " + ProfileQueue.getEventCounter(cmid, i) 
//							  				+ " cache misses start: " + ProfileQueue.eventCounterQueue[i][cmid]
//							  				+ " cache misses end: " + Scaler.perfCheck(i) 
//							  				+ " wall clock time: " + totalWallClockTime 
//							  				+ " start wall clock time: " + startWallClockTime 
//							  				+ " end wall clock time: " + wallClockTime
//							  				);
				  }
			  }
			  
			  for(int i = 1; i <= Scaler.getPerfEnerCounterNum() - 1; i++) {
				  
				  //If scaling by counters is enabled, we need calculate cache miss rate and TLB misses
				  //Otherwise, just simply store the perf counters user set from command line.
				  if(Controller.options.ENABLE_SCALING_BY_COUNTERS && i % Scaler.perfCounters == 0) {
					  //Move to the next index for L3CACHEMISSRATE event
					  ++offset;
					  cacheMissRate = ((double)eventEnerValues[i + offset - Scaler.perfCounters] / (double)eventEnerValues[i + offset - Scaler.perfCounters + 1]);
					  //get TLB misses
					  tlbMisses = eventEnerValues[i + offset - Scaler.perfCounters + 2] + eventEnerValues[i + offset -Scaler.perfCounters + 3];
//					  LogQueue.add(i + offset - 1, threadId, cmid, cacheMissRate);
					  //Move to the next index for TLBMISSES
					  ++offset;
//					  LogQueue.add(i + offset - 1, threadId, cmid, tlbMisses);
					  continue;
				  } 
//				  LogQueue.add(i + offset - 1, threadId, cmid, eventEnerValues[i - 1]);
			  }
			  
			  //Print the profiling information based on event counters
			  //TODO: print the results in the end of program running

			  if(Controller.options.ENABLE_COUNTER_PRINTER) {
				  
				  if(Controller.options.ENABLE_COUNTER_PROFILING) {
					  if(Scaler.perfCounters >= 4){
						  //TODO: Assume the first four counters are 'cache-misses' 
						  //'cache-references' 'dTLB-load-misses' 'iTLB-load-misses'. Too ugly.
						  cacheMissRate = eventEnerValues[0] / eventEnerValues[1];
						  cacheMissRateByTime = eventEnerValues[0] / totalWallClockTime;
						  tlbMisses = eventEnerValues[2] + eventEnerValues[3];
						  if(Controller.options.ENABLE_ENERGY_PROFILING) {
							VM.sysWrite("serviceArrayStack 280");
							  DataPrinter.printALl(cmid, clsNameList[cmid] + "." + methodNameList[cmid], totalWallClockTime, 
									  Controller.options.FREQUENCY_TO_BE_PRINTED, eventEnerValues, cacheMissRate, cacheMissRateByTime, tlbMisses);
						  } 
					  } else if(Controller.options.EVENTCOUNTER.equals(Controller.options.CACHE_MISS_RATE)) {
						  //TODO: Requires the sequence as "cache-misses,cache-references" in arguments. Too ugly. 
						  cacheMissRate = eventEnerValues[0] / eventEnerValues[1];
						  cacheMissRateByTime = eventEnerValues[0] / totalWallClockTime;
						  
						  if(Controller.options.ENABLE_ENERGY_PROFILING) {
							VM.sysWrite("serviceArrayStack 290");
							  DataPrinter.printProfInfoTwo(cmid, clsNameList[cmid] + "." + methodNameList[cmid], totalWallClockTime, 
									  Controller.options.FREQUENCY_TO_BE_PRINTED, eventEnerValues, cacheMissRate, cacheMissRateByTime);
						  } else {
							  DataPrinter.printCounterInfo(cmid, clsNameList[cmid] + "." + methodNameList[cmid], totalWallClockTime, 
									  Controller.options.FREQUENCY_TO_BE_PRINTED, eventEnerValues, cacheMissRate, cacheMissRateByTime);
						  }
						  
					  } else if(Controller.options.EVENTCOUNTER.equals(Controller.options.BRANCH_MISS_RATE)) {
						  //TODO: Requires the sequence as "branch-misses,branches" in arguments. Too ugly. 
						  branchMissRate = eventEnerValues[0] / eventEnerValues[1];
						  branchMissRateByTime = eventEnerValues[0] / totalWallClockTime;
						  
						  if(Controller.options.ENABLE_ENERGY_PROFILING) {
							VM.sysWrite("serviceArrayStack 304");
							  DataPrinter.printProfInfoTwo(cmid, clsNameList[cmid] + "." + methodNameList[cmid], totalWallClockTime, 
								  Controller.options.FREQUENCY_TO_BE_PRINTED, eventEnerValues, branchMissRate, branchMissRateByTime);
						  } else {
							  DataPrinter.printCounterInfo(cmid, clsNameList[cmid] + "." + methodNameList[cmid], totalWallClockTime, 
									  Controller.options.FREQUENCY_TO_BE_PRINTED, eventEnerValues, branchMissRate, branchMissRateByTime);
						  }
					  } else if(Controller.options.EVENTCOUNTER.equals(Controller.options.TLB_MISSES)) {
						  tlbMisses = eventEnerValues[0] + eventEnerValues[1];
						  tlbMissByTime = tlbMisses / totalWallClockTime;
						  
						  if(Controller.options.ENABLE_ENERGY_PROFILING) {
							VM.sysWrite("serviceArrayStack 315");
							  DataPrinter.printProfInfoTwo(cmid, clsNameList[cmid] + "." + methodNameList[cmid], totalWallClockTime, 
									  Controller.options.FREQUENCY_TO_BE_PRINTED, eventEnerValues, tlbMisses, tlbMissByTime);
						  } else {
							  DataPrinter.printCounterInfo(cmid, clsNameList[cmid] + "." + methodNameList[cmid], totalWallClockTime, 
									  Controller.options.FREQUENCY_TO_BE_PRINTED, eventEnerValues, tlbMisses, tlbMissByTime);
						  }
					  } else if(Controller.options.EVENTCOUNTER.equals(Controller.options.CONTEXT_SWITCHES)) {
						  contextSwitchByTime = eventEnerValues[0] / totalWallClockTime;
						  
						  if(Controller.options.ENABLE_ENERGY_PROFILING) {
							VM.sysWrite("serviceArrayStack 327");
							  DataPrinter.printProfInfoOne(cmid, clsNameList[cmid] + "." + methodNameList[cmid], totalWallClockTime, 
									  Controller.options.FREQUENCY_TO_BE_PRINTED, eventEnerValues, contextSwitchByTime);
						  } else {
							  DataPrinter.printCounterInfo(cmid, clsNameList[cmid] + "." + methodNameList[cmid], totalWallClockTime, 
									  Controller.options.FREQUENCY_TO_BE_PRINTED, eventEnerValues, 0, contextSwitchByTime);
						  }
					  } else if(Controller.options.EVENTCOUNTER.equals(Controller.options.PAGE_FAULTS)) {
						  pageFaultByTime = eventEnerValues[0] / totalWallClockTime;
						  
						  if(Controller.options.ENABLE_ENERGY_PROFILING) {
							VM.sysWrite("serviceArrayStack 338");
							  DataPrinter.printProfInfoOne(cmid, clsNameList[cmid] + "." + methodNameList[cmid], totalWallClockTime, 
									  Controller.options.FREQUENCY_TO_BE_PRINTED, eventEnerValues, pageFaultByTime);
						  } else {
							  DataPrinter.printCounterInfo(cmid, clsNameList[cmid] + "." + methodNameList[cmid], totalWallClockTime, 
									  Controller.options.FREQUENCY_TO_BE_PRINTED, eventEnerValues, 0, pageFaultByTime);
						  }
					  } else if(Controller.options.EVENTCOUNTER.equals(Controller.options.CPU_CYCLES)) {
						  cpuCycleByTime = eventEnerValues[0] / totalWallClockTime;
						  
						  if(Controller.options.ENABLE_ENERGY_PROFILING) {
							VM.sysWrite("serviceArrayStack 349");
							  DataPrinter.printProfInfoOne(cmid, clsNameList[cmid] + "." + methodNameList[cmid], totalWallClockTime, 
									  Controller.options.FREQUENCY_TO_BE_PRINTED, eventEnerValues, cpuCycleByTime);
						  } else {
							  DataPrinter.printCounterInfo(cmid, clsNameList[cmid] + "." + methodNameList[cmid], totalWallClockTime, 
									  Controller.options.FREQUENCY_TO_BE_PRINTED, eventEnerValues, 0, cpuCycleByTime);
						  }
					  } else if(Controller.options.EVENTCOUNTER.equals(Controller.options.CPU_CLOCK)) {
						  cpuClockByTime = eventEnerValues[0] / totalWallClockTime;
						  
						  if(Controller.options.ENABLE_ENERGY_PROFILING) {
							VM.sysWrite("serviceArrayStack 360");
							  DataPrinter.printProfInfoOne(cmid, clsNameList[cmid] + "." + methodNameList[cmid], totalWallClockTime, 
									  Controller.options.FREQUENCY_TO_BE_PRINTED, eventEnerValues, cpuClockByTime);
						  } else {
							  DataPrinter.printCounterInfo(cmid, clsNameList[cmid] + "." + methodNameList[cmid], totalWallClockTime, 
									  Controller.options.FREQUENCY_TO_BE_PRINTED, eventEnerValues, 0, cpuClockByTime);
						  }
					  }
				  } else if(!Controller.options.ENABLE_COUNTER_PROFILING && Controller.options.ENABLE_ENERGY_PROFILING) {
					VM.sysWrite("serviceArrayStack 369");  
					//Only time and energy measurement
					  DataPrinter.printEnerInfo(cmid, clsNameList[cmid] + "." + methodNameList[cmid], totalWallClockTime, 
							  Controller.options.FREQUENCY_TO_BE_PRINTED, eventEnerValues);
				  }
			  }
			  //Last event is always wall clock time.
//			  LogQueue.add(Scaler.getTotalEventNum() - 1, threadId, cmid, totalWallClockTime);
			  
		  } else {
//			  VM.sysWriteln("Something is wrong, cache miss is 0 in method: " + cmid);
		  }
		
	}

	//TODO: how to deal with other array, like char[] array?
	//Set hander for this arr, next time barrier access the array would reset it.
//	public static void ioArgSampling(Object obj){
//		if (VM.isBooted && VM.dumpMemoryTrace){
//			MiscHeader.setIOTag(obj);
//			Address addr = ObjectReference.fromObject(obj).toAddress().plus((int)VM.ioMemAccOffset);
//			if (VM.dumpMemoryTrace)
//				VM.sysAppendMemoryTrace(addr);
//		}
//	}
}

/*
 * ==========================================
 * Java File I/O: in RVM could do FILE IO : )
 * ==========================================
 *
 * PrintWriter writer = new PrintWriter("the-file-name.txt", "UTF-8");
		writer.println("The first line");
		writer.println("The second line");
		writer.close();
 * */



/*================================
 * Copy Array
 * ===============================
 *
String [] tmp1 = new String[2*len];
String [] tmp2 = new String[2*len];
long [] tmp3 = new long[2*len];
//System.arraycopy(arg0, arg1, arg2, arg3, arg4);
clsNameList = tmp1;
methodNameList = tmp2;
methodCount = tmp3;
 */


/*============================================================
 * These code, i.e load libary, unable to call by RVM, even RVM
 * running scaler could not success.
 *
System.load("/home/jianfeih/workspace/jacob/Jikes-Miss-Prediction/CPUScaler/libCPUScaler.so");
System.out.println("libary loaded");
if (!changed){
	int[] a = freqAvailable();
	scale(a[6]);
	changed f true;
}
 */
