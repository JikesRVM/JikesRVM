package org.jikesrvm.energy;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.Controller;

public class DataPrinter extends EnergyCalc{
	
	private int count = 0;
	private final int WARMUP = 2;
//	String methodName = null;
	public boolean hasTimeCalc = false;
	int threadsNeedCalcInTime = 0;
//	private int cmid = 0;
//	private double wallClockTime = 0;
//	private long sockNum = 0;
	public static PrintWriter filePrinter;
	
//	public DataPrinter(String methodName, int threadsNeedCalcInTime) {
//		super();
//		this.threadsNeedCalcInTime = threadsNeedCalcInTime;
//    	this.methodName = methodName;
//	}
//	
//    public DataPrinter(String methodName, String preEnergy, double wallClockTimeStart, String timePreamble, 
//			String timeEpilogue, double wallClockTimeEnd, String postEnergy, int threadsNeedCalcInTime) {
//    	super(preEnergy, wallClockTimeStart, timePreamble, timeEpilogue, wallClockTimeEnd, postEnergy);
//    	this.threadsNeedCalcInTime = threadsNeedCalcInTime;
//    	this.methodName = methodName;
//    }
//    
//    public DataPrinter(int sockNum, int cmid, String noisyName, double wallClockTime) {
//    	super();
//    	this.methodName = noisyName;
//    	this.wallClockTime = wallClockTime;
//    	this.cmid = cmid;
//    	this.sockNum = sockNum;
//    }
    
    /**
     * Initialize fileprinter object
     */
    public static void initPrintStream() {
    	try {
//    		filePrinter = new PrintStream(new BufferedOutputStream(new FileOutputStream(System.getProperty("user.dir") + "/" + Controller.options.PROFILER_FILE)));
    		filePrinter = new PrintWriter(new BufferedWriter(new FileWriter(System.getProperty("user.dir") + "/" + Controller.options.PROFILER_FILE, true)));
        } catch (IOException e) {
	        VM.sysWrite("IOException caught in DataPrinter.java while trying to create and start output file.\n");
        }
    }
    
    
    
	public void printResult(String signal, int loopNum) {

		DecimalFormat df = new DecimalFormat("#.##");
		DecimalFormat frq = new DecimalFormat("#.#");
		
		for (int k = 0; k < sockNum; k++) {
			gpuEnerPowerSum[k] = Double.valueOf(df.format(gpuEnerPowerSum[k]
					/ loopNum));
			cpuEnerPowerSum[k] = Double.valueOf(df.format(cpuEnerPowerSum[k]
					/ loopNum));
			pkgEnerPowerSum[k] = Double.valueOf(df.format(pkgEnerPowerSum[k]
					/ loopNum));
			
			gpuEnergySum[k] = Double.valueOf(df.format(gpuEnergySum[k] / loopNum));
			cpuEnergySum[k] = Double.valueOf(df.format(cpuEnergySum[k] / loopNum));
			pkgEnergySum[k] = Double.valueOf(df.format(pkgEnergySum[k] / loopNum));
		}
//		System.out.println("====================================================");
		/**** Time and Energy information ****/
		if(NumThread != 0)
			System.out.print(NumThread + "," + Double.valueOf(frq.format(frequency/1000000.0)) + ",");
		if(powerOption == 0 || powerOption == 1 || (powerOption == 2 && pkgPower != 0))
			System.out.print(pkgPower + "-" + dramPower + "," + pkgTime + "-" + dramTime + ",");
		else 
			System.out.print("power_limit_disable,power_limit_disable,");
		
		System.out.print(signal + ","
				+ Double.valueOf(df.format(wallClockTime / loopNum)) + ","
				+ Double.valueOf(df.format(cpuTime / loopNum)) + ","
				+ Double.valueOf(df.format(userModeTime / loopNum)) + ","
				+ Double.valueOf(df.format(kernelModeTime / loopNum)));

		for (int i = 0; i < sockNum; i++) {
			System.out.print(","
					+ gpuEnergySum[i] 
					+ ","
					+ cpuEnergySum[i] 
					+ ","
					+ pkgEnergySum[i] 
					+ ","); 
			// Power information
			if (wallClockTime != 0.0) {
				System.out.print(gpuEnerPowerSum[i] + "," + cpuEnerPowerSum[i] + ","
						+ pkgEnerPowerSum[i]);
			} else
				System.out.print("0.00," + "0.00," + "0.00");
			
			System.out.print("," + gpuEnerSD[i] + "," + cpuEnerSD[i] + "," + pkgEnerSD[i]);
			
		}
		System.out.print("," + wallClockTimeSD);
		System.out.println();
	}
	
	public static void printEventCounterTitle(boolean isCounterProfiled, boolean isEnergyProfiled) {
//		VM.sysWrite("EventName,");
//		for(int i = 1; i < Scaler.perfCounters; i++) {
//			VM.sysWrite("EventName,");
//		}
		//If number of event counters are more than one, we calculate cache miss rate 
		//and tlb misses

		synchronized(filePrinter) {
			filePrinter.print("Frequency,hotMethodMin,hotMethodMax,MethodID,MethodName,MethodNameHashCode,");
			if(isCounterProfiled) {
				filePrinter.print(Controller.options.EVENTCOUNTER + ",");
			}
			if(isEnergyProfiled) {
				filePrinter.print("DRAM/uncoreGPU,CPU,Package,Package_power,");
			}
			if(isCounterProfiled) {
				if(Scaler.perfCounters >= 4) {
					filePrinter.print("CacheMissRate,,CacheMissRateByTime,TLBMisses,");
				} else if(Controller.options.EVENTCOUNTER.equals(Controller.options.CACHE_MISS_RATE)) {
					filePrinter.print("CacheMissRate,CacheMissRateByTime,");
				} else if(Controller.options.EVENTCOUNTER.equals(Controller.options.BRANCH_MISS_RATE)) {
					filePrinter.print("BranchMissRate,BranchMissRateByTime,");
				} else if(Controller.options.EVENTCOUNTER.equals(Controller.options.TLB_MISSES)) {
					filePrinter.print("TLBMisses,TLBMissesByTime,");
				} else if(Controller.options.EVENTCOUNTER.equals(Controller.options.CONTEXT_SWITCHES)) {
					filePrinter.print("ContextSwitchesRate,ContextSwitchesByTime,");
				} else if(Controller.options.EVENTCOUNTER.equals(Controller.options.CPU_CYCLES)) {
					filePrinter.print("CPUCycleRate,CPUCyclesByTime,");
				} else if(Controller.options.EVENTCOUNTER.equals(Controller.options.CPU_CLOCK)) {
					filePrinter.print("CPUClockRate,CPUClocksByTime,");
				} else if(Controller.options.EVENTCOUNTER.equals(Controller.options.PAGE_FAULTS)) {
					filePrinter.print("PageFaultRate,PageFaultsByTime,");
				}
			}
			filePrinter.print("WallClockTime");

	//		for(int i = 0; i < sockNum; i++) {
	//			VM.sysWrite(",ValueForSocket" + i
	//							+ ",ValueForSocketSD" + i);
	//		}
	//		VM.sysWrite(",WallClockTimeSD");
			filePrinter.println();
		}
	}
	
	public static void printValues(int cmid, String methodName, int frequency, double[] counterValue) {
		filePrinter.print(frequency + "," + Controller.options.HOT_METHOD_TIME_MIN + "," + Controller.options.HOT_METHOD_TIME_MAX + "," + cmid + "," + methodName + ",0"/*hash code column*/);
		for(int i = 0; i < counterValue.length; i++) {
			filePrinter.print("," + counterValue[i]);
		}
		filePrinter.println();
	}

	public static void printValues(int cmid, String methodName, int frequency, double[] counterValue, long wallClockTime, double hotMethodStartTime) {
		filePrinter.print(frequency + "," + Controller.options.HOT_METHOD_TIME_MIN + "," + Controller.options.HOT_METHOD_TIME_MAX + "," + cmid + "," + methodName + ",0"/*hash code column*/);
		for(int i = 0; i < counterValue.length; i++) {
			filePrinter.print("," + counterValue[i]);
		}
		for(int i = 0; i < counterValue.length; i++) {
			filePrinter.print("," + counterValue[i] / wallClockTime);
		}
		filePrinter.print("," + wallClockTime + "," + hotMethodStartTime);
		filePrinter.println();
	}
	/**
	 * @param counterValue event counters and wall clock time
	 * Print out event counters, cache miss rate, tlb misses
	 * and wall clock time.
	 */
	public static void printALl(int cmid, String methodName, int frequency, double[] counterValue, double cacheMissRate, double cacheMissRateByTime, double tlbMisses) {
		synchronized(filePrinter) {
			double wallClockTime = counterValue[counterValue.length - 1];
			printValues(cmid, methodName, frequency, counterValue);
			filePrinter.print("," + cacheMissRate + "," + cacheMissRateByTime + "," + tlbMisses + "," + wallClockTime);
			filePrinter.println();
			filePrinter.flush();
		}
//		printValues(frequency, counterValue);
//		VM.sysWrite("," + cacheMissRate + "," + cacheMissRateByTime + "," + tlbMisses);
//		VM.sysWrite("," + this.wallClockTime);
//		VM.sysWriteln();
	}
	
	public static void printExtra(double counter1, double counter2, double wallClockTime) {
		if(counter1 == 0) {
			filePrinter.print(",," + counter2);
		} else {
			filePrinter.print("," + counter1 + "," + counter2);
		}
		filePrinter.print("," + wallClockTime);
		
		filePrinter.println();
		filePrinter.flush();
//		if(counter1 == 0) {
//			VM.sysWrite(",," + counter2);
//		} else {
//			VM.sysWrite("," + counter1 + "," + counter2);
//		}
//		VM.sysWrite("," + this.wallClockTime);
//		VM.sysWriteln();
	}
	
	/**
	 * Print out energy consumption, elased time and two hardware counter information
	 * @param cmid
	 * @param methodName
	 * @param frequency
	 * @param counterValue
	 * @param anotherCounter1
	 * @param anotherCounter2
	 */
	public static void printProfInfoTwo(int threadId, int cmid, String methodName, int frequency, double[] counterValue, long time,  double hotMethodStartTime) {
		//double wallClockTime = counterValue[counterValue.length - 1];
		filePrinter.print(threadId + ",");
		printValues(cmid, methodName, frequency, counterValue, time, hotMethodStartTime);
		//printExtra(anotherCounter1, anotherCounter2, wallClockTime);
	}
	
	/**
	 * Print out energy consumption, elased time and one hardware counter information
	 * @param cmid
	 * @param methodName
	 * @param frequency
	 * @param counterValue
	 * @param anotherCounter
	 */
	public static void printProfInfoOne(int cmid, String methodName, int frequency, double[] counterValue, double anotherCounter) {
		synchronized(filePrinter) {
			double wallClockTime = counterValue[counterValue.length - 1];
			printValues(cmid, methodName, frequency, counterValue);
			printExtra(0, anotherCounter, wallClockTime);
		}
	}
	
	/**
	 * Print energy and time information only
	 * @param cmid
	 * @param methodName
	 * @param frequency
	 * @param counterValue
	 */
	public static void printEnerInfo(int cmid, String methodName, int frequency, double[] counterValue) {
		synchronized(filePrinter) {
			double wallClockTime = counterValue[counterValue.length - 1];
			printValues(cmid, methodName, frequency, counterValue);
			filePrinter.print("," + wallClockTime);
			filePrinter.println();
			filePrinter.flush();
		}
	}
	/**
	 * Print hardware counter and time information only
	 * @param cmid
	 * @param methodName
	 * @param frequency
	 * @param counterValue
	 */
	public static void printCounterInfo(int cmid, String methodName, int frequency, double[] counterValue, double anotherCounter1, double anotherCounter2) {
		synchronized(filePrinter) {
			//Print columns before counter values
			filePrinter.print(frequency + "," + Controller.options.HOT_METHOD_TIME_MIN + "," + Controller.options.HOT_METHOD_TIME_MAX + "," + cmid + "," + methodName + ",0");
			//Print counter values
			for(int i = 0; i < counterValue.length; i++) {
				filePrinter.print("," + counterValue[i]);
			}
			double wallClockTime = counterValue[counterValue.length - 1];
			printExtra(anotherCounter1, anotherCounter2, wallClockTime);
		}
	}
	
	/**
	 * @param counterValue event counters and wall clock time
	 * Print out event counters and wall clock time only.
	 */
	public void printEventCounterValues(String frequency, double[] counterValue) {
//		printValues(frequency, counterValue);
//		VM.sysWrite("," + this.wallClockTime);
//		VM.sysWriteln();
	}
	
	/**
	 * Boring method to handle the name for csv file (only when this method is used by yield point frame pointer)
	 * @param noisyName
	 * @return pretty printed method name
	 */
	public static String extractYPMethodName(String noisyName) {
		String[] extract = noisyName.split(" ");
		String[] pkg = extract[2].split("/");
		String className = pkg[pkg.length - 1];
		className = className.substring(0, className.length() - 1);
		return className + extract[3];
	}
    
	public static void printTitle(int sockNum) {

		System.out.print("NumberOfThread,Frequency,power_limit(pkg-dram),time_window(pkg-dram),MethodName,WallClockTime," +
				"CpuTime,UserModeTime,KernelModeTime");
		for(int i = 0; i < sockNum; i++) {
			//String str = String.format("socket%d", i);
			System.out.print(",DramEnergy" + i + "," + "CPUEnergy" + i + "," + "PackageEnergy" + i + "," 
								+ "DramPower" + i + "," + "CPUPower" + i + "," + "PackagePower" + i + "," 
								+ "DramEnergySD" + i + "," + "CPUEnergySD" + i + "," + "PackageEnergySD" + i ); 
		}
		System.out.print(",WallClockTimeSD");
		System.out.println();
		
	}

	public void reset() {
		for(int i = 0; i < sockNum; i++) {
			super.gpuEnergySum[i] = 0.0;
			super.cpuEnergySum[i] = 0.0;
			super.pkgEnergySum[i] = 0.0;
			
			super.gpuEnerPowerSum[i] = 0.0;
			super.cpuEnerPowerSum[i] = 0.0;
			super.pkgEnerPowerSum[i] = 0.0;
			
			super.gpuEnerSD[i] = 0.0;
			super.cpuEnerSD[i] = 0.0;
			super.pkgEnerSD[i] = 0.0;
			
			for(int j = 0; j < WARMUP; j++) {
				super.gpuEnerPerLoop[i][j] = 0.0;
				super.cpuEnerPerLoop[i][j] = 0.0;
				super.pkgEnerPerLoop[i][j] = 0.0;

			}
		}
		for(int i = 0; i < WARMUP; i++) {
			super.wallClockTimePerLoop[i] = 0.0;
		}
		super.userModeTime = 0.0;
		super.cpuTime = 0.0;
		super.kernelModeTime = 0.0;
		super.wallClockTime = 0.0;
		super.wallClockTimeSD = 0.0;
		
//	    pkgPower = 0.0;
//		dramPower = 0.0; 
//		pkgTime = 0.0; 
//		dramTime = 0.0; 
//		powerOption = 0;
//		timeOption = 0;
	}
	
	public void setTimeCalc() {
		if(!hasTimeCalc)
			super.timeCalc();
		
		userModeTime /= threadsNeedCalcInTime;
		cpuTime /= threadsNeedCalcInTime;
		kernelModeTime /= threadsNeedCalcInTime;
		
	}

	public void dataReport(String methodName) {
		this.count++;
		//warmup iterations
		if(count <= WARMUP) {
//			super.energyCalc(count);
//			if(count == WARMUP) 
//				printResult(methodName + "Warmup", WARMUP);

		} else {

			if(count == WARMUP + 1 && WARMUP != 0)
				reset();
			super.energyCalc(count - WARMUP - 1);
			setTimeCalc();

			if(count == loopNum) {
				getStandDev(loopNum - WARMUP);
				printResult(methodName, loopNum - WARMUP);
				
			}

		}
	
	}

}
