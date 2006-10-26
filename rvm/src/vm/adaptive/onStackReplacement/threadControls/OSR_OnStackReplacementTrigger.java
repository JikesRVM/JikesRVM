/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.jikesrvm.adaptive;

import com.ibm.jikesrvm.*;
import com.ibm.jikesrvm.classloader.*;

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
