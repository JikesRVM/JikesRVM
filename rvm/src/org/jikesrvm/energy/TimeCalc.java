package org.jikesrvm.energy;

public class TimeCalc {
    public String preEnergy = new String();
    public String postEnergy = new String();
    public String timePreamble = new String();
    public String timeEpilogue = new String();
    
    private int numThreads = 0;
    
	String[] timeInfoStart = null;
	String[] timeInfoEnd = null;
	
    double tempUserModeTime = 0.0;
    double tempKernelModeTime = 0.0;
    double tempCpuTime = 0.0;
    
    public double userModeTime = 0.0;
    public double kernelModeTime = 0.0;
    public double cpuTime = 0.0;
    
    public TimeCalc() {
    	
    }

    public TimeCalc(String timePreamble, String timeEpilogue) {
	    this.timePreamble = timePreamble;
	    this.timeEpilogue = timeEpilogue;

    }
    
    public void set(String timePreamble, String timeEpilogue) {
	    this.timePreamble = timePreamble;
	    this.timeEpilogue = timeEpilogue;

    }
    
	public void timeCalc() {
		
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
		
//		System.out.println(userModeTime);
//		System.out.println(cpuTime);
//		System.out.println(kernelModeTime);

	}
	


}
