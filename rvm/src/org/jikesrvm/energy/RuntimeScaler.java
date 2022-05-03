package org.jikesrvm.energy;

import org.jikesrvm.VM;
import org.jikesrvm.ia32.StackframeLayoutConstants;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.SysCall;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;

@Uninterruptible
public class RuntimeScaler implements ProfilingTypes, ScalerOptions {
	private static long cacheMissRateThresh = 3000;
	private static long tlbMissesThresh = 10;
	
	//For debug
	public static int isOutOfBoundaryCount = 0;
	public static int isEventCounterIsEmptyCount = 0;
	public static boolean outputIsPrinted = false;
	
	/**
     * Dynamically scale cpu by hardware events hot method queue
     */
	@Uninterruptible
	public static void dynamicScale(int whereFrom, Address yieldpointServiceMethodFP) {
	    if (VM.UseEpilogueYieldPoints) {
		    Address ypTakenInFP = Magic.getCallerFramePointer(yieldpointServiceMethodFP); // method that took yieldpoint
		    // Get the cmid for the method in which the yieldpoint was taken.
		    int ypTakenInCMID = Magic.getCompiledMethodID(ypTakenInFP);
		    
		    if(ProfileQueue.isOutOfBoundary(L3CACHEMISSES, ypTakenInCMID)) {
		    	return;
		    }
//		    VM.sysWriteln("isOutOfBoundaryCount: " + isOutOfBoundaryCount);
//		    if(!outputIsPrinted) {
//			    for(int i = 0; i < ProfileQueue.eventCounterQueue[0].length; i++) {
//			    	if(ProfileQueue.eventCounterQueue[0][i] != 0) {
//			    		VM.sysWriteln(ProfileQueue.eventCounterQueue[0][i]);
//			    	}
//			    }
//			    outputIsPrinted = true;
//		    }
		    //if the current cmid is considered as hot method, do energy profiling.
		    if(ProfileQueue.eventCounterIsEmpty(L3CACHEMISSES, ypTakenInCMID)) {
		    	return;
		    }

//		    VM.sysWriteln("Kenan: reached after eventCounterIsEmpty!!");
			// Get the cmid for that method's caller.
			Address ypTakenInCallerFP = Magic
					.getCallerFramePointer(ypTakenInFP);
			int ypTakenInCallerCMID = Magic
					.getCompiledMethodID(ypTakenInCallerFP);

			// Determine if ypTakenInCallerCMID corresponds to a real Java
			// stackframe.
			// If one of the following conditions is detected, set
			// ypTakenInCallerCMID to -1
			// Caller is out-of-line assembly (no RVMMethod object) or
			// top-of-stack psuedo-frame
			// Caller is a native method
			CompiledMethod ypTakenInCM = CompiledMethods
					.getCompiledMethod(ypTakenInCMID);
			
//			VM.sysWriteln("hot method id is: " + ypTakenInCMID + " its event counter is: " + ProfileQueue.hotMethodsByEventCounter[0][ypTakenInCMID]);
			
			if (ypTakenInCallerCMID == StackframeLayoutConstants.INVISIBLE_METHOD_ID
					|| ypTakenInCM.getMethod().getDeclaringClass()
							.hasBridgeFromNativeAnnotation()) {
				//Do nothing
			} else {
		    //Memory intensive method
//			if (ProfileQueue.hotMethodsByEventCounter[0][ypTakenInCMID] > memIntenseThresh && 
//				ProfileQueue.hotMethodsByEventCounter[1][ypTakenInCMID] < ioIntenseThresh) {
			if(ProfileQueue.getHotMethodCounter(ypTakenInCMID, L3CACHEMISSRATE) > cacheMissRateThresh
					&& ProfileQueue.getHotMethodCounter(ypTakenInCMID, TLBMISSES) < tlbMissesThresh) {
				if (whereFrom == RVMThread.PROLOGUE) {
					/**
					 * If the current method hasn't set to be userspace, set the
					 * frequency now. It has race condition here, but we need
					 * focus performance more.
					 */
					int core = SysCall.sysCall.getCurrentCpu();
					if (!Scaler.governor[core].equals(USERSPACE)) {
						 Scaler.governor[core] = USERSPACE;
//						 Scaler.scale(Integer.parseInt(Controller.options.FREQUENCY));
						 Scaler.scale(3);
					}

					// Scaler.pkgPowerLimit(MAXIMUM, 0.0);
				} else if (whereFrom == RVMThread.EPILOGUE) {
					/**
					 * If the current method has set to be other governors,
					 * reset it back to ondemand. It has race condition here,
					 * but we need focus performance more.
					 */
					int core = SysCall.sysCall.getCurrentCpu();
					if (!Scaler.governor[core].equals(ONDEMAND)) {
						Scaler.setGovernor(ONDEMAND);
					}
				}
			} 
			//IO intensive method
//			else if (ProfileQueue.hotMethodsByEventCounter[1][ypTakenInCMID] > tlbMissesThresh) {
			else if (ProfileQueue.getHotMethodCounter(ypTakenInCMID, TLBMISSES) > tlbMissesThresh) {
				if (whereFrom == RVMThread.PROLOGUE) {
					
					ypTakenInCM = CompiledMethods.getCompiledMethod(ypTakenInCMID);
					
//					VM.sysWriteln("IO intensive method is: " + ypTakenInCM.getMethod());
					/**
					 * If the current method hasn't set to be userspace, set the
					 * frequency now. It has race condition here, but we need
					 * focus performance more.
					 */
					int core = SysCall.sysCall.getCurrentCpu();
					if (!Scaler.governor[core].equals(USERSPACE)) {
						 Scaler.governor[core] = USERSPACE;
//						 Scaler.scale(Integer.parseInt(Controller.options.FREQUENCY));
						 Scaler.scale(12);
					}

					// Scaler.pkgPowerLimit(MAXIMUM, 0.0);
				} else if (whereFrom == RVMThread.EPILOGUE) {
					/**
					 * If the current method has set to be other governors,
					 * reset it back to ondemand. It has race condition here,
					 * but we need focus performance more.
					 */
					int core = SysCall.sysCall.getCurrentCpu();
					if (!Scaler.governor[core].equals(ONDEMAND)) {
						Scaler.setGovernor(ONDEMAND);
					}
				}
			} 
			//CPU intensive method
			else if (ProfileQueue.getHotMethodCounter(ypTakenInCMID, L3CACHEMISSRATE) < cacheMissRateThresh) {
				if (whereFrom == RVMThread.PROLOGUE) {
					/**
					 * If the current method hasn't set to be userspace, set the
					 * frequency now. It has race condition here, but we need
					 * focus performance more.
					 */
					int core = SysCall.sysCall.getCurrentCpu();
					if (!Scaler.governor[core].equals(USERSPACE)) {
						 Scaler.governor[core] = USERSPACE;
//						 Scaler.scale(Integer.parseInt(Controller.options.FREQUENCY));
						 Scaler.scale(0);
					}

					// Scaler.pkgPowerLimit(MAXIMUM, 0.0);
				} else if (whereFrom == RVMThread.EPILOGUE) {
					/**
					 * If the current method has set to be other governors,
					 * reset it back to ondemand. It has race condition here,
					 * but we need focus performance more.
					 */
					int core = SysCall.sysCall.getCurrentCpu();
					if (!Scaler.governor[core].equals(ONDEMAND)) {
						Scaler.setGovernor(ONDEMAND);
					}
				}
			}
			}
	    }
		
	}
}
