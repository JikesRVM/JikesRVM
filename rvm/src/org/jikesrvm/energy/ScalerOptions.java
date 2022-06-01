package org.jikesrvm.energy;

public interface ScalerOptions {
	public final static byte[] USERSPACE = {'u', 's', 'e', 'r', 's', 'p', 'a', 'c', 'e', '\0'}; //userspace
	public final static byte[] ONDEMAND  = {'o', 'n', 'd', 'e', 'm', 'a', 'n', 'd', '\0'};	   //ondemand
	public final static int PAPI_L3_TCM = 0x80000008;	//Level 3 total cache misses
	public final static int PAPI_TLB_TL = 0x80000016;	//Total translation lookaside buffer misses
	public final static int PAPI_RES_STL = 0x80000032;	//Cycles processor is stalled on resource
	public final static int PAPI_FP_INS = 0x80000034;
	public final static int MINIMUM 	 = 0;
	public final static int MAXIMUM 	 = 1;
	public final static int CUSTOMIZE 	 = 2;
}
