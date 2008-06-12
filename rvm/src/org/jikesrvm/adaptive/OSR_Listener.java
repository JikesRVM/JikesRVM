/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.adaptive;

import org.jikesrvm.adaptive.controller.VM_Controller;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Code invoked from VM_Thread.yieldpoint for the purposes of OSR.
 */
@Uninterruptible
public class OSR_Listener {

  public static boolean checkForOSRPromotion(int whereFrom, Address yieldpointServiceMethodFP) {
    if (VM_Scheduler.getCurrentThread().isIdleThread()) return false;
    if (VM_Scheduler.getCurrentThread().isSystemThread()) return false;

    // check if there are pending osr request
    if ((VM_Controller.osrOrganizer != null) && (VM_Controller.osrOrganizer.osr_flag)) {
      VM_Controller.osrOrganizer.activate();
    }

    if (whereFrom != RVMThread.BACKEDGE) return false;

    // See if we are at a loop backedge in an outdated baseline compiled method

    Address fp = yieldpointServiceMethodFP;
    fp = VM_Magic.getCallerFramePointer(fp);
    int ypTakenInCMID = VM_Magic.getCompiledMethodID(fp);
    VM_CompiledMethod ypTakenInCM = VM_CompiledMethods.getCompiledMethod(ypTakenInCMID);
    if (ypTakenInCM.isOutdated() && ypTakenInCM.getCompilerType() == VM_CompiledMethod.BASELINE) {

      Address tsFromFP = yieldpointServiceMethodFP;
      Address realFP = VM_Magic.getCallerFramePointer(tsFromFP);

      Address stackbeg = VM_Magic.objectAsAddress(VM_Scheduler.getCurrentThread().getStack());

      Offset tsFromFPoff = tsFromFP.diff(stackbeg);
      Offset realFPoff = realFP.diff(stackbeg);

      OSR_OnStackReplacementTrigger.trigger(ypTakenInCMID, tsFromFPoff, realFPoff, whereFrom);
      return true;
    }
    return false;
  }

  public static void handleOSRFromOpt(Address yieldpointServiceMethodFP) {
    Address tsFromFP = yieldpointServiceMethodFP;
    Address realFP = VM_Magic.getCallerFramePointer(tsFromFP);
    int ypTakenInCMID = VM_Magic.getCompiledMethodID(realFP);
    Address stackbeg = VM_Magic.objectAsAddress(VM_Scheduler.getCurrentThread().getStack());

    Offset tsFromFPoff = tsFromFP.diff(stackbeg);
    Offset realFPoff = realFP.diff(stackbeg);

    OSR_OnStackReplacementTrigger.trigger(ypTakenInCMID, tsFromFPoff, realFPoff, RVMThread.OSROPT);
  }
}
