package org.jikesrvm.energy;

public interface ProfilingTypes {
	public static final int L3CACHEMISSES 			= 0;
	public static final int L3CACHEREFERENCES		= 1;
	public static final int DTLBLOADMISSES			= 2;
	public static final int ITLBLOADMISSES			= 3;
	public static final int L3CACHEMISSRATE			= 4;
	public static final int TLBMISSES				= 5;
//	public static final int WALLCLOCKTIME			= 1;
	
	public static final int DRAMENERGY 				= 6;
	public static final int CPUENERGY 				= 7;
	public static final int PKGENERGY 				= 8;
	
	public static final int INDEX 					= 9;
	public static final int USERTIME 				= 9;
	public static final int CPUTIME 				= 10;
	public static final int COUNT 					= 11;
	public static final int THREADID 				= 12;
	public static final int SAMPLESIGN 				= 13;
	
	

}
