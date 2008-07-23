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
package org.jikesrvm.scheduler;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.mm.mminterface.MM_Interface;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Reflection;
import org.vmmagic.pragma.NonMoving;

/**
 * Finalizer thread.
 *
 * This thread is created by Scheduler.boot() at runtime startup.
 * Its "run" method does the following:
 *    1. yield to the gcwaitqueue, until scheduled by g.c.
 *    2. For all objects on finalize Q, run the finalize() method
 *    3. Go to 1
 *
 * This thread comes out of wait state via notify from the garbage collector
 */
@NonMoving
public class FinalizerThread extends Scheduler.ThreadModel {

  private static final int verbose = 0; // currently goes up to 2

  private final Object[] none = new Object[0];

  public FinalizerThread() {
    super("FinalizerThread");
  }

  /** Run the finalizer thread (one per RVM) */
  @Override
  public void run() {
    if (verbose >= 1) {
      Scheduler.trace("FinalizerThread ", "run routine entered");
    }

    try {
      while (true) {

        // suspend this thread: it will resume when the garbage collector
        // places objects on the finalizer queue and notifies.
        Scheduler.suspendFinalizerThread();

        if (verbose >= 1) {
          VM.sysWriteln("FinalizerThread starting finalization");
        }

        while (true) {
          Object o = MM_Interface.getFinalizedObject();
          if (o == null) break;
          if (verbose >= 2) {
            VM.sysWrite("FinalizerThread finalizing object at ", Magic.objectAsAddress(o));
            VM.sysWrite(" of type ");
            VM.sysWrite(Magic.getObjectType(o).getDescriptor());
            VM.sysWriteln();
          }
          try {
            RVMMethod method = Magic.getObjectType(o).asClass().getFinalizer();
            if (VM.VerifyAssertions) VM._assert(method != null);
            Reflection.invoke(method, o, none);
          } catch (Exception e) {
            if (verbose >= 1) VM.sysWriteln("Throwable exception caught for finalize call");
          }
          if (verbose >= 2) {
            VM.sysWriteln("FinalizerThread done with object at ", Magic.objectAsAddress(o));
          }
        }
        if (verbose >= 1) VM.sysWriteln("FinalizerThread finished finalization");

      }          // while (true)
    } catch (Exception e) {
      VM.sysWriteln("Unexpected exception thrown in finalizer thread: ", e.toString());
      e.printStackTrace();
    }

  }  // run

}
