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
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.scheduler.Scheduler;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * Trigger an OSR from a running thread.
 */
public class OSR_OnStackReplacementTrigger {

  /**
   * Trigger an OSR from a running thread.
   */
  @NoInline
  @Uninterruptible
  public static void trigger(int ypTakenInCMID, Offset tsFromFPoff, Offset ypTakenFPoff, int whereFrom) {

    RVMThread thread = Scheduler.getCurrentThread();
    CompiledMethod ypTakenInCM = CompiledMethods.getCompiledMethod(ypTakenInCMID);
    RVMMethod ypTakenInMethod = ypTakenInCM.getMethod();
    boolean isInBootImage = ypTakenInMethod.getDeclaringClass().isInBootImage();

    if (isInBootImage) return;
    OSR_OnStackReplacementEvent event = (OSR_OnStackReplacementEvent) thread.onStackReplacementEvent;
    event.suspendedThread = thread;
    event.whereFrom = whereFrom;
    event.CMID = ypTakenInCMID;
    event.tsFromFPoff = tsFromFPoff;
    event.ypTakenFPoff = ypTakenFPoff;

    // make sure that the above stores don't get ordered after the flagging
    // this thread is requesting OSR.
    Magic.sync();

    // consumer:
    thread.requesting_osr = true;

    // make sure that the flag is set to activate the OSR organizer after
    // this thread has flagged its request for OSR.
    Magic.sync();

    // osr organizer must be initialized already
    if (!Controller.osrOrganizer.osr_flag) {
      Controller.osrOrganizer.osr_flag = true;
      Controller.osrOrganizer.activate();
    }

    thread.osrPark();
  }
}
