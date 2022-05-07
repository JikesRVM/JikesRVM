package org.jikesrvm.energy;





public class EnergyCalc extends TimeCalc implements StandDevCal{

	private static final int PKGNUM = 10;
	private static final int LOOPSIZE = 10;
    
//    public String socketNumCheck = EnergyCheckUtils.EnergyStatCheck();
    
	public static int warmup = 0; 
    public static int loopNum = 0;

    public static int sockNum = 0;
    public static int frequency = 0;
    public static int NumThread = 0;
    
    //args for power limit
    public static int powerOption = 0;
    public static double pkgPower = 0.0;
    public static double dramPower = 0.0;
    //args for time window
    public static int timeOption = 0;
    public static double pkgTime = 0.0;
    public static double dramTime = 0.0;
    
    //Calculation
    
    double wallClockTime = 0.0;
    double curWallClockTime = 0.0;

	String[][] loopEnergyStart = new String[PKGNUM][];
	String[][] loopEnergyStop = new String[PKGNUM][];
	String[] sockPreInfo = new String[PKGNUM];
	String[] sockPostInfo = new String[PKGNUM];
	double[] gpuEnergyWarmup = new double[PKGNUM];
	double[] cpuEnergyWarmup = new double[PKGNUM];
	double[] pkgEnergyWarmup = new double[PKGNUM];
	double[] gpuEnergySum = new double[PKGNUM];
	double[] cpuEnergySum = new double[PKGNUM];
	double[] pkgEnergySum = new double[PKGNUM];
	
	double[][] gpuEnerPerLoop = new double[PKGNUM][LOOPSIZE];
	double[][] cpuEnerPerLoop = new double[PKGNUM][LOOPSIZE];
	double[][] pkgEnerPerLoop = new double[PKGNUM][LOOPSIZE];
	
	double[] wallClockTimePerLoop = new double[LOOPSIZE];
	
	double[] gpuEnerSD = new double [PKGNUM];
	double[] cpuEnerSD = new double [PKGNUM];
	double[] pkgEnerSD = new double [PKGNUM];
	
	double wallClockTimeSD = 0.0;
	
	double[] gpuEnerPowerSum = new double[PKGNUM];
	double[] cpuEnerPowerSum = new double[PKGNUM];
	double[] pkgEnerPowerSum = new double[PKGNUM];

    public double wallClockTimeStart = 0.0;
    public double wallClockTimeEnd = 0.0;
    
    //For standard deviation calculation
    public int loopCount = 0;
    
    public EnergyCalc() {
    	super();

    }
    
    public EnergyCalc(String preEnergy, double wallClockTimeStart, String timePreamble, 
			String timeEpilogue, double wallClockTimeEnd, String postEnergy) {
    	
    	super(timePreamble, timeEpilogue);
	    this.wallClockTimeStart = wallClockTimeStart;
	    this.wallClockTimeEnd = wallClockTimeEnd;
	    this.preEnergy = preEnergy;
	    this.postEnergy = postEnergy;

    }
    
	
//	public void reportInit(String timePreamble, double wallClockTimeStart, String preEnergy, String timeEpilogue, double wallClockTimeEnd, String postEnergy) {
//		this.timePreamble = timePreamble;
//		this.wallClockTimeStart = wallClockTimeStart;
//		this.preEnergy = preEnergy;
//		
//		this.timeEpilogue = timeEpilogue;
//		this.wallClockTimeEnd = wallClockTimeEnd;
//		this.postEnergy = postEnergy;
//	}
    
    public void getStandDev(int loopNum) {
    	for (int j = 0; j < sockNum; j++) {
	    	for(int i = 0; i <= loopCount; i++) {
	    		gpuEnerSD[j] += Math.pow((gpuEnergySum[j]/loopNum - gpuEnerPerLoop[j][i]), 2.0);
	    		cpuEnerSD[j] += Math.pow((cpuEnergySum[j]/loopNum - cpuEnerPerLoop[j][i]), 2.0);
	    		pkgEnerSD[j] += Math.pow((pkgEnergySum[j]/loopNum - pkgEnerPerLoop[j][i]), 2.0);
	    		
//	    		System.out.println("gpu sum: " + gpuEnergySum[j]);
//	    		System.out.println("cpu sum: " + cpuEnergySum[j]);
//	    		System.out.println("pkg sum: " + pkgEnergySum[j]);
//	    		
//	    		System.out.println("gpu per loop: " + gpuEnerPerLoop[j][i]);
//	    		System.out.println("cpu per loop: " + cpuEnerPerLoop[j][i]);
//	    		System.out.println("pkg per loop: " + pkgEnerPerLoop[j][i]);
//	    		
//	    		System.out.println("gpu pwoer: " + Math.pow((gpuEnergySum[j]/loopNum - gpuEnerPerLoop[j][i]), 2.0));
//	    		System.out.println("cpu power: " + Math.pow((cpuEnergySum[j]/loopNum - cpuEnerPerLoop[j][i]), 2.0));
//	    		System.out.println("pkg power: " + Math.pow((pkgEnergySum[j]/loopNum - pkgEnerPerLoop[j][i]), 2.0));
	    		
	    	}
    	}

    	//get wall clock time standard deviation
    	for(int i = 0; i <= loopCount; i++) {
    		wallClockTimeSD += Math.pow(wallClockTime/loopNum - wallClockTimePerLoop[i], 2.0);
//    		System.out.println(wallClockTimeSD);
    	}
//    	System.out.println("gpuenerSD: " + gpuEnerSD[0] + " cpuenerSD: " + cpuEnerSD[0] + " pkgenerSD: " + pkgEnerSD[0] + " wallClockTimeSD " + wallClockTimeSD);
    	
    	wallClockTimeSD = Math.sqrt(wallClockTimeSD / loopNum);
    	
    	for(int i = 0; i < sockNum; i++) {
    		gpuEnerSD[i] = Math.sqrt(gpuEnerSD[i] / loopNum);
    		cpuEnerSD[i] = Math.sqrt(cpuEnerSD[i] / loopNum);
    		pkgEnerSD[i] = Math.sqrt(pkgEnerSD[i] / loopNum);
    	}
    }
    
    public static void preInit(int frequency, int threads, double pkgPower, double dramPower, 
    		double pkgTime, double dramTime, int powerOption, int timeOption, int loopNum, int warmup) {
	    EnergyCalc.frequency = frequency;
	    EnergyCalc.NumThread = threads;
	    
		EnergyCalc.warmup = warmup;
	    EnergyCalc.loopNum = loopNum;
	    EnergyCalc.pkgPower = pkgPower;
	    EnergyCalc.dramPower = dramPower; 
	    EnergyCalc.pkgTime = pkgTime; 
	    EnergyCalc.dramTime = dramTime; 
	    EnergyCalc.powerOption = powerOption;
	    EnergyCalc.timeOption = timeOption;
    	
    }
    
    
	
	public static double calculateEnergy(double end, double start) {
		double delta = 0;
		delta = end - start;
		if(delta < 0)	//If the value is set to be 0 during the measurement, it would be negative
			delta += (double)EnergyCheckUtils.wraparoundValue;

		return delta;
	}
	
	public static double getPower(double ener, double time) {
		return time == 0 ? ener : ener / time; 
		
	}
    
	public void energyCalc(int loopCount) {
		this.loopCount = loopCount;
		double delta = 0.0;
		
		curWallClockTime = wallClockTimeEnd - wallClockTimeStart;
		wallClockTimePerLoop[loopCount] = curWallClockTime;
//		System.out.println("wall clock time per loop: " + wallClockTimePerLoop[loopCount] + "curWallClockTime: " + curWallClockTime);
		wallClockTime += curWallClockTime;
		/*One Socket*/
		if(!preEnergy.contains("@")) {
			sockNum = 1;

			loopEnergyStop[0] = postEnergy.split("#");
			loopEnergyStart[0] = preEnergy.split("#");

		
		} else {
		/*Multiple sockets*/
			sockPreInfo = preEnergy.split("@");

			sockNum = sockPreInfo.length;

			for(int i = 0; i < sockPreInfo.length; i++) {
				loopEnergyStart[i] = sockPreInfo[i].split("#");					
			}
			sockPostInfo = postEnergy.split("@");
			for(int i = 0; i < sockPostInfo.length; i++) {
				loopEnergyStop[i] = sockPostInfo[i].split("#");					
			}
		}

		for(int i = 0; i < sockNum; i++) {
			delta = calculateEnergy(Double.parseDouble(loopEnergyStop[i][0]), Double.parseDouble(loopEnergyStart[i][0]));
			//record for standard deviation
			gpuEnerPerLoop[i][loopCount] = delta;
			//record for average energy/power consumption
			gpuEnergySum[i] += delta;
			
			delta = getPower(delta, curWallClockTime);
			gpuEnerPowerSum[i] += delta;//Restore energy information for each loop
			delta = calculateEnergy(Double.parseDouble(loopEnergyStop[i][1]), Double.parseDouble(loopEnergyStart[i][1]));
			//record for standard deviation
			cpuEnerPerLoop[i][loopCount] = delta;
			//record for average energy/power consumption
			cpuEnergySum[i] += delta;
			delta = getPower(delta, curWallClockTime);
			cpuEnerPowerSum[i] += delta;
			delta = calculateEnergy(Double.parseDouble(loopEnergyStop[i][2]), Double.parseDouble(loopEnergyStart[i][2]));
			//record for standard deviation
			pkgEnerPerLoop[i][loopCount] = delta;
			//record for average energy/power consumption
			pkgEnergySum[i] += delta;
//			System.out.println("loopCount: " + loopCount + " pkgEnerPerLoop" + delta);
			delta = getPower(delta, curWallClockTime);
			pkgEnerPowerSum[i] += delta;
			
//			System.out.println(Double.parseDouble(loopEnergyStop[i][0]) - Double.parseDouble(loopEnergyStart[i][0]));
//			System.out.println(Double.parseDouble(loopEnergyStop[i][1]) - Double.parseDouble(loopEnergyStart[i][1]));
//			System.out.println(Double.parseDouble(loopEnergyStop[i][2]) - Double.parseDouble(loopEnergyStart[i][2]));
//			System.out.println(gpuEnergySum[i]);
//			System.out.println(cpuEnergySum[i]);
//			System.out.println(pkgEnergySum[i]);
		}
	}
	
    
}
