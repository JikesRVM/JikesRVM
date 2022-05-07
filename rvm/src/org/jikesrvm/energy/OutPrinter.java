package org.jikesrvm.energy;

import java.text.DecimalFormat;


public class OutPrinter {
	
	private static final int WARMUP = 0;
	private static final int PKGNUM = 10;
	static String preEnergy = new String();
	static String postEnergy = new String();
	static String timePreamble = new String();
	static String timeEpilogue = new String();
	static double wallClockTimeStart = 0.0;
	static double wallClockTimeEnd = 0.0;
	static int frequency = 0;
	static int NumThread = 0;
	
	static double pkgPowLimit = 0;
	static double dramPowLimit = 0; 
	static double pkgTimeWin = 0; 
	static double dramTimeWin = 0; 
	static int powerOption = 0;
	static int timeOption = 0;
    
    static double tempUserModeTime = 0.0;
    static double tempKernelModeTime = 0.0;
    static double tempCpuTime = 0.0;
    
    static double userModeTime = 0.0;
    static double kernelModeTime = 0.0;
    static double cpuTime = 0.0;
    static double wallClockTime = 0.0;
    
    static int socketNum = 1;
	static String[][] loopEnergyStart = new String[PKGNUM][];
	static String[][] loopEnergyStop = new String[PKGNUM][];
	static String[] sockPreInfo = new String[PKGNUM];
	static String[] sockPostInfo = new String[PKGNUM];
	static double[] gpuEnergyWarmup = new double[PKGNUM];
	static double[] cpuEnergyWarmup = new double[PKGNUM];
	static double[] pkgEnergyWarmup = new double[PKGNUM];
	static double[] gpuEnergy = new double[PKGNUM];
	static double[] cpuEnergy = new double[PKGNUM];
	static double[] pkgEnergy = new double[PKGNUM];
	
	static double[] gpuEnerPower = new double[PKGNUM];
	static double[] cpuEnerPower = new double[PKGNUM];
	static double[] pkgEnerPower = new double[PKGNUM];
	
	static String[] timeInfoStart = null;
	static String[] timeInfoEnd = null;
	
	private static int count = 0;
	
	public static void printTitle(int socketNum) {

		System.out.print("NumberOfThread,Frequency,power_limit(pkg-dram),time_window(pkg-dram),MethodName,WallClockTime," +
				"CpuTime,UserModeTime,KernelModeTime");
		for(int i = 0; i < socketNum; i++) {
			//String str = String.format("socket%d", i);
			System.out.print(",DramEnergy" + i + "," + "CPUEnergy" + i + "," + "PackageEnergy" + i + "," 
								+ "DramPower" + i + "," + "CPUPower" + i + "," + "PackagePower" + i); 
		}
		System.out.println();
		
	}
    
	public static void init(String preEnergy, double wallClockTimeStart, String timePreamble, 
			String timeEpilogue, double wallClockTimeEnd, String postEnergy, int frequency, int NumThread,
			double pkgPowLimit, double dramPowLimit, double pkgTimeWin, double dramTimeWin, int powerOption, int timeOption) {
	    OutPrinter.preEnergy = preEnergy;
	    OutPrinter.postEnergy = postEnergy;
	    OutPrinter.timePreamble = timePreamble;
	    OutPrinter.timeEpilogue = timeEpilogue;
	    OutPrinter.wallClockTimeStart = wallClockTimeStart;
	    OutPrinter.wallClockTimeEnd = wallClockTimeEnd;
	    OutPrinter.frequency = frequency;
	    OutPrinter.NumThread = NumThread;
	    
	    OutPrinter.pkgPowLimit = pkgPowLimit;
		OutPrinter.dramPowLimit = dramPowLimit; 
		OutPrinter.pkgTimeWin = pkgTimeWin; 
		OutPrinter.dramTimeWin = dramTimeWin; 
		OutPrinter.powerOption = powerOption;
		OutPrinter.timeOption = timeOption;
	}
	
	public static void reset() {
		for(int i = 0; i < socketNum; i++) {
			gpuEnergy[i] = 0.0;
			cpuEnergy[i] = 0.0;
			pkgEnergy[i] = 0.0;
		}
		userModeTime = 0.0;
		cpuTime = 0.0;
		kernelModeTime = 0.0;
		wallClockTime = 0.0;
		
	    OutPrinter.pkgPowLimit = 0.0;
		OutPrinter.dramPowLimit = 0.0; 
		OutPrinter.pkgTimeWin = 0.0; 
		OutPrinter.dramTimeWin = 0.0; 
		OutPrinter.powerOption = 0;
		OutPrinter.timeOption = 0;
	}
	
	
	public static void printResult(String signal, int loopNum) {

		
		DecimalFormat df = new DecimalFormat("#.##");
		DecimalFormat frq = new DecimalFormat("#.#");
		
		for (int k = 0; k < socketNum; k++) {
			gpuEnerPower[k] = Double.valueOf(df.format(gpuEnergy[k]
					/ wallClockTime));
			cpuEnerPower[k] = Double.valueOf(df.format(cpuEnergy[k]
					/ wallClockTime));
			pkgEnerPower[k] = Double.valueOf(df.format(pkgEnergy[k]
					/ wallClockTime));
		}
//		System.out.println("====================================================");
		/**** Time and Energy information ****/
		if(NumThread != 0)
			System.out.print(NumThread + "," + Double.valueOf(frq.format(frequency/1000000.0)) + ",");
		if(powerOption == 0 || powerOption == 1 || (powerOption == 2 && pkgPowLimit != 0))
			System.out.print(pkgPowLimit + "-" + dramPowLimit + "," + pkgTimeWin + "-" + dramTimeWin + ",");
		else 
			System.out.print("power_limit_disable,power_limit_disable,");
		
		System.out.print(signal + ","
				+ Double.valueOf(df.format(wallClockTime / loopNum)) + ","
				+ Double.valueOf(df.format(cpuTime / loopNum)) + ","
				+ Double.valueOf(df.format(userModeTime / loopNum)) + ","
				+ Double.valueOf(df.format(kernelModeTime / loopNum)));

		for (int i = 0; i < socketNum; i++) {
			System.out.print(","
					+ Double.valueOf(df.format(gpuEnergy[i] / loopNum))
					+ ","
					+ Double.valueOf(df.format(cpuEnergy[i] / loopNum))
					+ ","
					+ Double.valueOf(df.format(pkgEnergy[i] / loopNum))
					+ ","); 
			// Power information
			if (wallClockTime != 0.0) {
				System.out.print(gpuEnerPower[i] + "," + cpuEnerPower[i] + ","
						+ pkgEnerPower[i]);
			} else
				System.out.print("0.00," + "0.00," + "0.00");
			
		}
		System.out.println();
	}
	
	public static double calculateEnergy(double end, double start) {
		double delta = 0;
		delta = end - start;
		if(delta < 0)	//If the value is set to be 0 during the measurement, it would be negative
			delta += (double)EnergyCheckUtils.wraparoundValue;

		return delta;
	}
	
	public static void doCalculate() {
		double delta = 0.0;
		/*One Socket*/
		if(!preEnergy.contains("@")) {
			socketNum = 1;

			loopEnergyStop[0] = postEnergy.split("#");
			loopEnergyStart[0] = preEnergy.split("#");

		
		} else {
		/*Multiple sockets*/
			sockPreInfo = preEnergy.split("@");

			socketNum = sockPreInfo.length;

			for(int i = 0; i < sockPreInfo.length; i++) {
				loopEnergyStart[i] = sockPreInfo[i].split("#");					
			}
			sockPostInfo = postEnergy.split("@");
			for(int i = 0; i < sockPostInfo.length; i++) {
				loopEnergyStop[i] = sockPostInfo[i].split("#");					
			}
		}

		for(int i = 0; i < socketNum; i++) {
			delta = calculateEnergy(Double.parseDouble(loopEnergyStop[i][0]), Double.parseDouble(loopEnergyStart[i][0]));
			gpuEnergy[i] += delta;//Restore energy information for each loop
			
			delta = calculateEnergy(Double.parseDouble(loopEnergyStop[i][1]), Double.parseDouble(loopEnergyStart[i][1]));
			cpuEnergy[i] += delta;
			
			delta = calculateEnergy(Double.parseDouble(loopEnergyStop[i][2]), Double.parseDouble(loopEnergyStart[i][2]));
			pkgEnergy[i] += delta;
			
//			System.out.println(Double.parseDouble(loopEnergyStop[i][0]) - Double.parseDouble(loopEnergyStart[i][0]));
//			System.out.println(Double.parseDouble(loopEnergyStop[i][1]) - Double.parseDouble(loopEnergyStart[i][1]));
//			System.out.println(Double.parseDouble(loopEnergyStop[i][2]) - Double.parseDouble(loopEnergyStart[i][2]));
//			System.out.println(gpuEnergy[i]);
//			System.out.println(cpuEnergy[i]);
//			System.out.println(pkgEnergy[i]);
		}

		timeInfoStart = timePreamble.split("#");	//Split CPU time and user mode time
		timeInfoEnd = timeEpilogue.split("#");

		tempUserModeTime = Double.parseDouble(timeInfoEnd[0]) - Double.parseDouble(timeInfoStart[0]);		//Calcualte time usage	
		tempCpuTime = Double.parseDouble(timeInfoEnd[1]) - Double.parseDouble(timeInfoStart[1]);
		tempKernelModeTime = tempCpuTime - tempUserModeTime;
		
		tempUserModeTime /= 1000000000.0;	//Get second
		tempCpuTime /= 1000000000.0;
		
		tempKernelModeTime = tempKernelModeTime > 0.0 ? tempKernelModeTime / 1000000000.0 : 0.0;	//it could be negative because of precision problem for Java API
		
//		System.out.println(tempUserModeTime);
//		System.out.println(tempCpuTime);
//		System.out.println(tempKernelModeTime);
		
		userModeTime += tempUserModeTime;
		cpuTime += tempCpuTime;
		kernelModeTime += tempKernelModeTime;
		wallClockTime += wallClockTimeEnd - wallClockTimeStart;
		
//		System.out.println(userModeTime);
//		System.out.println(cpuTime);
//		System.out.println(kernelModeTime);
//		System.out.println(wallClockTime);


	}

	
	public static void dataReport(String preEnergy, double wallClockTimeStart, String timePreamble, 
			String timeEpilogue, double wallClockTimeEnd, String postEnergy, int loopNum, int frequency, int NumThread, 
			double pkgPowLimit, double dramPowLimit, double pkgTimeWin, double dramTimeWin, int powerOption, int timeOption) {
		
		init(preEnergy, wallClockTimeStart, timePreamble, 
				timeEpilogue, wallClockTimeEnd, postEnergy, frequency, 
				NumThread, pkgPowLimit, dramPowLimit, pkgTimeWin, dramTimeWin, powerOption, timeOption);
		
		count++;
		//warmup iterations
		if(count <= WARMUP) {
			doCalculate();
			if(count == WARMUP) 
				printResult("SunFlowWarmup", WARMUP);
			

			//tempUserModeTime = Double.valueOf(df.format(tempUserModeTime));	//Round 2 digits
			//tempCpuTime = Double.valueOf(df.format(tempCpuTime));

//			umTimeWarmup[j] = tempUserModeTime;	//Restore Time info for each loop
//			cpuTimeWarmup[j] = tempCpuTime;
//			kmTimeWarmup[j] = tempCpuTime - tempUserModeTime;
//			wcTimeWarmup[j] = wallClockTimeEnd - wallClockTimeStart;

		} else {
			if(count == WARMUP + 1)
				reset();
			doCalculate();
			
			if(count == loopNum)
				printResult("sunflow", loopNum - WARMUP);
			//tempUserModeTime = Double.valueOf(df.format(tempUserModeTime));										//Round 2 digits
			//tempCpuTime = Double.valueOf(df.format(tempCpuTime));

//			umTime[j] = tempUserModeTime;
//			cpuTime[j] = tempCpuTime;
//			kmTime[j] = tempCpuTime - tempUserModeTime;
//			wcTime[j] = wallClockTimeEnd - wallClockTimeStart;

		}
	
	
	}
	
}
