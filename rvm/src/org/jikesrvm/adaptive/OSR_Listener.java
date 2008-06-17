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

import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.Scheduler;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Code invoked from Thread.yieldpoint for the purposes of OSR.
 */
@Uninterruptible
public class OSR_Listener {

  public static boolean checkForOSRPromotion(int whereFrom, Address yieldpointServiceMethodFP) {
    if (Scheduler.getCurrentThread().isIdleThread()) return false;
    if (Scheduler.getCurrentThread().isSystemThread()) return false;

    // check if there are pending osr request
    if ((Controller.osrOrganizer != null) && (Controller.osrOrganizer.osr_flag)) {
      Controller.osrOrganizer.activate();
    }

    if (whereFrom != RVMThread.BACKEDGE) return false;

    // See if we are at a loop backedge in an outdated baseline compiled method

    Address fp = yieldpointServiceMethodFP;
    fp = Magic.getCallerFramePointer(fp);
    int ypTakenInCMID = Magic.getCompiledMethodID(fp);
    CompiledMethod ypTakenInCM = CompiledMethods.getCompiledMethod(ypTakenInCMID);
    if (ypTakenInCM.isOutdated() && ypTakenInCM.getCompilerType() == CompiledMethod.BASELINE) {

      Address tsFromFP = yieldpointServiceMethodFP;
      Address realFP = Magic.getCallerFramePointer(tsFromFP);

      Address stackbeg = Magic.objectAsAddress(Scheduler.getCurrentThread().getStack());

      Offset tsFromFPoff = tsFromFP.diff(stackbeg);
      Offset realFPoff = realFP.diff(stackbeg);

      OSR_OnStackReplacementTrigger.trigger(ypTakenInCMID, tsFromFPoff, realFPoff, whereFrom);
      return true;
    }
    return false;
  }

  public static void handleOSRFromOpt(Address yieldpointServiceMethodFP) {
    Address tsFromFP = yieldpointServiceMethodFP;
    Address realFP = Magic.getCallerFramePointer(tsFromFP);
    int ypTakenInCMID = Magic.getCompiledMethodID(realFP);
    Address stackbeg = Magic.objectAsAddress(Scheduler.getCurrentThread().getStack());

    Offset tsFromFPoff = tsFromFP.diff(stackbeg);
    Offset realFPoff = realFP.diff(stackbeg);

    OSR_OnStackReplacementTrigger.trigger(ypTakenInCMID, tsFromFPoff, realFPoff, RVMThread.OSROPT);
  }
}
