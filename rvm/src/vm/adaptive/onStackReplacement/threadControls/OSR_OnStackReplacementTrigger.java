/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Trigger an OSR from a running thread.
 * 
 * @author Feng Qian
 */
public class OSR_OnStackReplacementTrigger {
  
  /**
   * Trigger an OSR from a running thread.
   */
  public static void trigger(int ypTakenInCMID,
                             Offset tsFromFPoff,
                             Offset ypTakenFPoff,
                             int whereFrom) 
  throws NoInlinePragma, UninterruptiblePragma {


    VM_Thread thread = VM_Thread.getCurrentThread();    
    VM_CompiledMethod ypTakenInCM = VM_CompiledMethods.getCompiledMethod(ypTakenInCMID);
    VM_Method ypTakenInMethod = ypTakenInCM.getMethod(); 
    boolean isInBootImage = ypTakenInMethod.getDeclaringClass().isInBootImage();

    if (isInBootImage) return;
    
    thread.onStackReplacementEvent.suspendedThread = thread;
    thread.onStackReplacementEvent.whereFrom = whereFrom;
    thread.onStackReplacementEvent.CMID = ypTakenInCMID;
    thread.onStackReplacementEvent.tsFromFPoff = tsFromFPoff;
    thread.onStackReplacementEvent.ypTakenFPoff = ypTakenFPoff;

    // consumer:
    thread.requesting_osr = true;
        
    // osr organizer must be initialized already
    if (VM_Controller.osrOrganizer.osr_flag == false) {
      VM_Controller.osrOrganizer.osr_flag = true;
      VM_Controller.osrOrganizer.activate();
    }

    thread.osrSuspend();
  }
}
