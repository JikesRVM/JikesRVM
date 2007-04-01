/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */

package org.jikesrvm.adaptive;

import org.jikesrvm.*;
import org.jikesrvm.adaptive.controller.VM_Controller;
import org.jikesrvm.scheduler.VM_Thread;
import org.jikesrvm.classloader.*;

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
  @NoInline
  @Uninterruptible
  public static void trigger(int ypTakenInCMID,
                             Offset tsFromFPoff,
                             Offset ypTakenFPoff,
                             int whereFrom) { 


    VM_Thread thread = VM_Thread.getCurrentThread();    
    VM_CompiledMethod ypTakenInCM = VM_CompiledMethods.getCompiledMethod(ypTakenInCMID);
    VM_Method ypTakenInMethod = ypTakenInCM.getMethod(); 
    boolean isInBootImage = ypTakenInMethod.getDeclaringClass().isInBootImage();

    if (isInBootImage) return;
    OSR_OnStackReplacementEvent event = (OSR_OnStackReplacementEvent)thread.onStackReplacementEvent;
    event.suspendedThread = thread;
    event.whereFrom = whereFrom;
    event.CMID = ypTakenInCMID;
    event.tsFromFPoff = tsFromFPoff;
    event.ypTakenFPoff = ypTakenFPoff;

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
