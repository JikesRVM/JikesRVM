package org.jikesrvm.energy;

import java.util.List;
import java.lang.ThreadLocal;

import org.jikesrvm.VM;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.adaptive.controller.Controller;
import org.vmmagic.pragma.Entrypoint;
import org.jikesrvm.runtime.SysCall;

public class Service implements ProfilingTypes, ScalerOptions {
	public native static int scale(int freq);
	public static final int INIT_SIZE = 1000;
	public static final int HIGH_FREQ = 2201000; 
	public static String[] clsNameList = new String[INIT_SIZE];
	public static String[] methodNameList = new String[INIT_SIZE];
	public static long[] methodCount = new long[INIT_SIZE];
	public static double[][] prevProfile = new double[INIT_SIZE*2][3];
	public static boolean profileEnable = false;
	public static long start_ts = System.currentTimeMillis();



		/**Index is composed by hashcode of "method ID#thread ID" in order to differentiate method invocations by different threads*/
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


		public synchronized static int addMethodEntry(String cls, String name){
			//name=name+"\0";
			//cls=cls+"\0";
			String fullName = cls+"$$$$$"+name;
			fullName+="\0";
			return SysCall.sysCall.add_method_entry(fullName.getBytes(),"".getBytes());	
		}


		/**
		 * Do profile
		 * @param profileAttrs the collection that contains profile information 
		 */
		  private static void getProfileAttrs(double[] profileAttrs, String profilePoint, RVMThread thread) {
			double perfCounter = 0.0d;
			int eventId = 0;
			int threadId = (int)Thread.currentThread().getId();
			double startTime = 0.0d;
			//Loop unwinding

			if (thread.isFirstSampleInBurst || prevProfile[threadId][0] == 0 || profilePoint == ServiceConstants.STARTPROFILE) {
				// If this thread is profiled at the first time, record the profile value.
				// No matter if the it's the startProfile or endProfile.
				if (Controller.options.ENABLE_COUNTER_PROFILING) {
					for (int i = 0; i < Scaler.perfCounters; i++) {

						prevProfile[threadId][eventId] = Scaler.perfCheck(i);
						eventId++;
					}
				}
				if (Controller.options.ENABLE_ENERGY_PROFILING) {

					double[] energy = EnergyCheckUtils.getEnergyStats();

					for (int i = 0; i < EnergyCheckUtils.ENERGY_ENTRY_SIZE; i++) {
						prevProfile[threadId][eventId] = energy[i];
						eventId++;
					}
				}

				thread.isFirstSampleInBurst = false;
			} else if (profilePoint == ServiceConstants.ENDPROFILE) {
				// If it's the endProfile point, then calculate the profile value.
				
				if (Controller.options.ENABLE_COUNTER_PROFILING) {
					for (int i = 0; i < Scaler.perfCounters; i++) {

						perfCounter = Scaler.perfCheck(i);

						profileAttrs[eventId] = perfCounter - prevProfile[threadId][eventId];
						prevProfile[threadId][eventId] = perfCounter;
						eventId++;
					}
				}

				if (Controller.options.ENABLE_ENERGY_PROFILING) {
					
					double[] energy = EnergyCheckUtils.getEnergyStats();
					
					for (int i = 0; i < EnergyCheckUtils.ENERGY_ENTRY_SIZE; i++) {
						profileAttrs[eventId] = calculateEnergy(energy[i], prevProfile[threadId][eventId]);
						prevProfile[threadId][eventId] = energy[i];
						eventId++;
					}
				}
			}
		  }

		  public static double calculateEnergy(double end, double start) {
			double delta = 0;
			delta = end - start;

			if(delta < 0) {
				//If the value is set to be 0 during the measurement, it would be negative
				delta += (double)EnergyCheckUtils.wraparoundValue;
			}

			return delta;
		  }

		  public static void enableProfile() {
			VM.sysWriteln("Profiling Enabled ... Stay tuned!");
		  	profileEnable = true;
	 	 }

		/**
		 * Set userspace governnor and speficy the CPU frequency
		 * @param freq The CPU frequency
		 */
		@Entrypoint
		public static void startFreqOptimization(int freq) {

			RVMThread thread = RVMThread.getCurrentThread();

			int mlen = Instrumentation.method_len;
			// If the number of candidate is more than one. Then reduce
			// the sapmling rate.

			/** Without counter based sampling*/
//			if (mlen == 1 && thread.dvfsSliceExpired > RVMThread.FREQ || 
//					mlen > 1 && thread.dvfsSliceExpired > RVMThread.FREQ && 
//					thread.dvfsSliceExpired % 2 == 0) {
//	
//				thread.prevGov = Scaler.getGovernor();
//
//				if (thread.prevGov.equalsIgnoreCase("userspace")) {
//					thread.prevFreq = Scaler.checkCpuFrequency();
//				}
//
//				changeUserSpaceFreq(freq);
//				thread.dvfsIsSet = true;
//				thread.dvfsSliceExpired = 0;
//			}
//
			/** Counter based sampling*/
			if (mlen == 1 && thread.dvfsSliceExpired > RVMThread.FREQ || 
					mlen > 1 && thread.dvfsSliceExpired > RVMThread.FREQ && 
					thread.dvfsSliceExpired % 2 == 0) {

				thread.skippedInvDvfs--;
				//VM.sysWriteln("startFreqOptimization is invoked. The skippedInvDefs is: " + thread.skippedInvDvfs);
				
				if (thread.skippedInvDvfs == 0) {
					thread.prevGov = Scaler.getGovernor();

					//VM.sysWriteln("startFreqOptimization: sample is taken. the current governor before setting is: " + thread.prevGov);
					if (thread.prevGov.equalsIgnoreCase("userspace")) {
						thread.prevFreq = Scaler.checkCpuFrequency();
					}

					changeUserSpaceFreq(freq);

					thread.skippedInvDvfs = RVMThread.STRIDE;
					thread.samplesPerTimerDvfs--;

					if (thread.samplesPerTimerDvfs == 0) {
						thread.samplesPerTimerDvfs = RVMThread.SAMPLES;
						thread.dvfsSliceExpired = 0;
					}
				}
			} 
		}

		/**
		 * Set userspace governnor and speficy the CPU frequency
		 * @param freq The CPU frequency
		 */
		public static void changeUserSpaceFreq(int freq) {
			Scaler.setGovernor(USERSPACE);	
			Scaler.scale(freq);
		}

		/**
		 * Set userspace governnor and speficy the highest CPU frequency
		 * @param freq The CPU frequency
		 */
		public static void changeToHighestFreq() {
			Scaler.setGovernor(USERSPACE);	
			Scaler.scale(HIGH_FREQ);
		}

		/**
		 * Set ondemand governnor 
		 */
		public static void changeOnDemandFreq() {
			Scaler.setGovernor(ONDEMAND);	
		}

		/**
		 * Reset governor and frequency to be the one before setting to the userspace governor
		 */
		public static void resetGovAndFreq(RVMThread thread) {
			String targetGov = thread.prevGov;

			//VM.sysWriteln("endFreqOptimization: sample is taken. the governor needs to be set is: " + targetGov);
			if (targetGov != "") {
				byte[] gov = targetGov.equalsIgnoreCase("userspace") ? USERSPACE : ONDEMAND;
				Scaler.setGovernor(gov);	
				if (targetGov.equalsIgnoreCase("userspace")) {
					Scaler.scale(thread.prevFreq);
				}

			} else {
				Scaler.setGovernor(ONDEMAND);
			}
		}

		/**
		 * In the end of frequency optimization.
		 */
		@Entrypoint
		public static void endFreqOptimization() {
			RVMThread thread = RVMThread.getCurrentThread();
			int mlen = Instrumentation.method_len;

			/** Without counter based sampling*/
//			if (thread.dvfsIsSet) {
//				resetGovAndFreq(thread);
//				thread.dvfsIsSet = false;
//			}
//
			/** Counter based sampling*/
			if (mlen == 1 && thread.dvfsSliceExpired > RVMThread.FREQ || 
				mlen > 1 && thread.dvfsSliceExpired > RVMThread.FREQ && 
				thread.dvfsSliceExpired % 2 == 0) {

				thread.skippedInvDvfs--;
				
				//VM.sysWriteln("startFreqOptimization is invoked. The skippedInvDefs is: " + thread.skippedInvDvfs);
				if (thread.skippedInvDvfs == 0) {

					resetGovAndFreq(thread);

					thread.skippedInvDvfs = RVMThread.STRIDE;
					thread.samplesPerTimerDvfs--;

					if (thread.samplesPerTimerDvfs == 0) {
						thread.samplesPerTimerDvfs = RVMThread.SAMPLES;
						thread.dvfsSliceExpired = 0;
					}
				}
			} 		

		}
		

	public static void init_service() {
		RVMThread.FREQ = Integer.parseInt(VM.KENAN_FREQ); 
		RVMThread.SAMPLES = Integer.parseInt(VM.KENAN_SAMPLES);
		VM.sysWriteln("[Service Constructor Invoked as Expected] ... ");
		VM.sysWriteln("Samples");
		VM.sysWriteln(RVMThread.SAMPLES);
		VM.sysWriteln("Freq");
		VM.sysWriteln(RVMThread.FREQ);
	}

	@Entrypoint
	public static void startProfile(int cmid) {
		profile(cmid, ServiceConstants.STARTPROFILE);
	}

	@Entrypoint
	public static void endProfile(int cmid) {
		profile(cmid, ServiceConstants.ENDPROFILE);
	}

	public static void profile(int cmid, String profilePoint) {
		RVMThread thread = RVMThread.getCurrentThread();
		/*int expired = SysCall.sysCall.quota_expired(cmid);
		if(expired>0) {
			return;
		}*/

		//Using sampling based method to profile
		//
		//SysCall.sysCall.quota_expired(0);
		/*if(RVMThread.FREQ==0) {
			init_service();
		}*/
		if (thread.energyTimeSliceExpired >= RVMThread.FREQ) {
			thread.skippedInvocations--;
			//VM.totalInvocationCount++;
			if (thread.skippedInvocations == 0) {
				/** Event values for the method */
				double[] profileAttrs = new double[Scaler.getPerfEnerCounterNum()];
				int threadId = (int) Thread.currentThread().getId();
				
				//Do profile	
				getProfileAttrs(profileAttrs, profilePoint, thread);
				int ll = profileAttrs.length;
				boolean discard_sample = profileAttrs[ll-1]<=0;

				int freq = (int) Controller.options.FREQUENCY_TO_BE_PRINTED;
				if(!discard_sample) {

				SysCall.sysCall.add_log_entry(profileAttrs,cmid,System.currentTimeMillis() - start_ts,freq);
				}

				thread.skippedInvocations = RVMThread.STRIDE;
				thread.samplesThisTimerInterrupt--;

				if (thread.samplesThisTimerInterrupt == 0) {
					thread.samplesThisTimerInterrupt = RVMThread.SAMPLES;
					thread.energyTimeSliceExpired = 0;
					thread.isFirstSampleInBurst = true;
				}
			}
		}

	}
}
