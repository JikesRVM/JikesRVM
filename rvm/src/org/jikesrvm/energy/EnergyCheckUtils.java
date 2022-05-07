package org.jikesrvm.energy;
import java.lang.reflect.Field;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.SysCall;
import org.jikesrvm.util.StringUtilities;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NoOptCompile;
public class EnergyCheckUtils {

	/*****Somehow syscall cannot be called at this time*****/
//	public static int wraparoundValue = SysCall.sysCall.ProfileInit();	
//	
//	public static int socketNum = SysCall.sysCall.GetSocketNum();
	public static int wraparoundValue;
	public static int socketNum = 1;
	public static final int ENERGY_ENTRY_SIZE = 3;
	public static final int INIT_SIZE = 500;
	public static boolean isJraplInit = false;
	
	public static long[][] cumProfInfo;
	public static String [] clsNameList;
	public static String [] methodNameList;
	public static long [] methodCount;
	
	public static void initProf() {
		clsNameList = new String[INIT_SIZE];
		methodNameList = new String[INIT_SIZE];
		methodCount = new long[INIT_SIZE];
	}
	
	/**
	 * @return an array of current energy information.
	 * The first entry is: Dram/uncore gpu energy(depends on the cpu architecture.
	 * The second entry is: CPU energy
	 * The third entry is: Package energy
	 */
	@NoInline
	@NoOptCompile
	public static void initJrapl() {
		if(!isJraplInit) {
			wraparoundValue = SysCall.sysCall.ProfileInit();
			isJraplInit = true;
		}
	}
	@NoInline
	@NoOptCompile
	public static double[] getEnergyStats() {
		//Scaler.initScaler();
		//int socketNum = SysCall.sysCall.GetSocketNum();
		//Three 60s is hardcoded size of dram/uncore gpu, cpu and package information.
		final int enerInfoSize = socketNum * (60 + 60 + 60 + 4);
		
		byte[] energyBuf = new byte[enerInfoSize];
		
		SysCall.sysCall.EnergyStatCheck(energyBuf);
		
		String energyInfo = StringUtilities.asciiBytesToString(energyBuf).trim();
		
		/*One Socket*/
		if(socketNum == 1) {
			//VM.sysWriteln("Added by mahmou1 to trace Parse Excetion  \n\n");
			double[] stats = new double[3];
			String[] energy = energyInfo.split("#");
			//VM.sysWriteln("\n\n\n\n energyInfo:(" + energyInfo + ")\n\n\n\n");
			stats[0] = Double.parseDouble(energy[0]);
			stats[1] = Double.parseDouble(energy[1]);
			stats[2] = Double.parseDouble(energy[2]);
			return stats;
		
		} else {
		/*Multiple sockets*/
			String[] perSockEner = energyInfo.split("@");
			double[] stats = new double[3];
			int count = 0;

			for(int i = 0; i < perSockEner.length; i++) {
				String[] energy = perSockEner[i].split("#");
				for(int j = 0; j < energy.length; j++) {
//					if (stats[j] == 0) {
//						VM.sysWriteln("stats " + j + "energy is 0");
//					}
					stats[j] += Double.parseDouble(energy[j]);
				}
			}
			return stats;
		}

	}
	
//	public static void main(String[] args) {
//		//Info info = (Info)energyInfo.EnergyStatCheck();
//		/*For jni header generation*/
//		int socketNum = 0;
//		double[] info1 = GetPackagePowerSpec();
////		double[] info2 = GetDramPowerSpec();
//
//		String[][] before_info = new String[2][];
//		String[][] after_info = new String[2][];
//		String[] sockPreInfo = new String[2];
//		String[] sockPostInfo = new String[2];
//
//
//		//for(int i = 0; i < 4; i++)
//			//System.out.println("package: " + info1[i]);
//		
//		SetPackagePowerLimit(0, 0, 150.0);
////		SetPackagePowerLimit(1, 0, 150.0);
////		SetDramPowerLimit(0, 0, 130.0);
////		SetDramPowerLimit(1, 0, 130.0);
//
//		SetPackageTimeWindowLimit(0, 1, 1.0);
////		SetPackageTimeWindowLimit(1, 1, 1.0);
//		
//		/*
//		EnergyStatCheck();
//		ProfileDealloc();
//
//		int[] a = freqAvailable();
//		scale(10000);
//		*/
//		
//
//		String before = EnergyStatCheck();
//		//System.out.println(before);
//		try {
//			Thread.sleep(10000);
//		} catch(Exception e) {
//		}
//		String after = EnergyStatCheck();
//		//System.out.println(after);
//		if(before.contains("@")) {
//			socketNum = 2;
//			sockPreInfo = before.split("@");
//			sockPostInfo = after.split("@");
//
//			for(int i = 0; i < sockPreInfo.length; i++) {
//				before_info[i] = sockPreInfo[i].split("#");
//				after_info[i] = sockPostInfo[i].split("#");
//			}
//		} else {
//			socketNum = 1;
//			before_info[0] = before.split("#");
//			after_info[0] = after.split("#");
//		}
//
//		//System.out.println(after_info[0][0]);
//		//System.out.println(before_info[0][0]);
//		for(int i = 0; i < socketNum; i++) {
//		System.out.println("gpu: " + (Double.parseDouble(after_info[i][0]) - Double.parseDouble(before_info[i][0])) / 10.0 + " cpu: " + (Double.parseDouble(after_info[i][1]) - Double.parseDouble(before_info[i][1])) / 10.0 + " package: " + (Double.parseDouble(after_info[i][2]) - Double.parseDouble(before_info[i][2])) / 10.0);
//		}
//		ProfileDealloc();
//	}

}
