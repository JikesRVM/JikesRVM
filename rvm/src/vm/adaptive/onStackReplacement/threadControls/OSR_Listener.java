/*
 * (C) Copyright IBM Corp 2002, 2004
 */
//$Id$

package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.*;
import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Code invoked from VM_Thread.yieldpoint for the purposes of OSR.
 * 
 * @author Dave Grove
 * @author Feng Qian
 */
public class OSR_Listener implements Uninterruptible {

  public static boolean checkForOSRPromotion(int whereFrom) throws NoInlinePragma {
    if (VM_Thread.getCurrentThread().isIdleThread()) return false;
    if (VM_Thread.getCurrentThread().isSystemThread()) return false;

    // check if there are pending osr request
    if ((VM_Controller.osrOrganizer != null) 
        && (VM_Controller.osrOrganizer.osr_flag)) {
      VM_Controller.osrOrganizer.activate(); 
    }

    if (whereFrom != VM_Thread.BACKEDGE) return false;

    // See if we are at a loop backedge in an outdated baseline compiled method
    
    // Get pointer to my caller's frame
    Address fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()); 

    // Skip over wrapper to "real" method
    fp = VM_Magic.getCallerFramePointer(fp);                             
    fp = VM_Magic.getCallerFramePointer(fp);                             
    int ypTakenInCMID = VM_Magic.getCompiledMethodID(fp);
    VM_CompiledMethod ypTakenInCM = VM_CompiledMethods.getCompiledMethod(ypTakenInCMID);
    if (ypTakenInCM.isOutdated() &&
        ypTakenInCM.getCompilerType() == VM_CompiledMethod.BASELINE) {

      // get this frame pointer
      Address tsFP = VM_Magic.getFramePointer();
      Address tsFromFP = VM_Magic.getCallerFramePointer(tsFP);
      tsFromFP = VM_Magic.getCallerFramePointer(tsFromFP);
      // Skip over wrapper to "real" method
      Address realFP = VM_Magic.getCallerFramePointer(tsFromFP);
          
      Address stackbeg = VM_Magic.objectAsAddress(VM_Thread.getCurrentThread().stack);
          
      Offset tsFromFPoff = tsFromFP.diff(stackbeg);
      Offset realFPoff = realFP.diff(stackbeg);
      
      OSR_OnStackReplacementTrigger.trigger(ypTakenInCMID, 
                                            tsFromFPoff,
                                            realFPoff,
                                            whereFrom);
      return true;
    }
    return false;
  }
  

  public static void handleOSRFromOpt() throws NoInlinePragma {
    // get this frame pointer
    Address tsFP = VM_Magic.getFramePointer();
    Address tsFromFP = VM_Magic.getCallerFramePointer(tsFP);
    tsFromFP = VM_Magic.getCallerFramePointer(tsFromFP);
    // Skip over wrapper to "real" method
    Address realFP = VM_Magic.getCallerFramePointer(tsFromFP);
    int ypTakenInCMID = VM_Magic.getCompiledMethodID(realFP);
    Address stackbeg = VM_Magic.objectAsAddress(VM_Thread.getCurrentThread().stack);
          
    Offset tsFromFPoff = tsFromFP.diff(stackbeg);
    Offset realFPoff = realFP.diff(stackbeg);
          
    OSR_OnStackReplacementTrigger.trigger(ypTakenInCMID, 
                                          tsFromFPoff,
                                          realFPoff,
                                          VM_Thread.OSROPT);
  }
}
