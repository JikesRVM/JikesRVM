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
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.NonMoving;

/**
 * Finalizer thread.
 *
 * This thread is created by RVMThread.boot() at runtime startup.
 * Its "run" method does the following:
 *    1. yield to the gcwaitqueue, until scheduled by g.c.
 *    2. For all objects on finalize Q, run the finalize() method
 *    3. Go to 1
 *
 * This thread comes out of wait state via notify from the garbage collector
 */
@NonMoving
public class FinalizerThread extends RVMThread {

  private static final int verbose = 0; // currently goes up to 2

  private final Object[] none = new Object[0];
  private static boolean shouldRun;
  private static HeavyCondLock schedLock;
  public static void boot() {
    schedLock=new HeavyCondLock();
    FinalizerThread ft=new FinalizerThread();
    ft.makeDaemon(true);
    ft.start();
  }
  @Uninterruptible
  public static void schedule() {
    schedLock.lock();
    shouldRun=true;
    schedLock.broadcast();
    schedLock.unlock();
  }

  public FinalizerThread() {
    super("FinalizerThread");
  }

  /** Run the finalizer thread (one per RVM) */
  @Override
  public void run() {
    if (verbose >= 1) {
      RVMThread.trace("FinalizerThread ", "run routine entered");
    }

    try {
      while (true) {

        // suspend this thread: it will resume when the garbage collector
        // places objects on the finalizer queue and notifies.
        schedLock.lock();
        if (!shouldRun) {
          schedLock.waitNicely();
        }
        shouldRun=false;
        schedLock.unlock();

        if (verbose >= 1) {
          VM.sysWriteln("FinalizerThread starting finalization");
        }

        while (true) {
          Object o = MemoryManager.getFinalizedObject();
          if (o == null) break;
          if (verbose >= 2) {
            VM.sysWrite("FinalizerThread finalizing object at ", Magic.objectAsAddress(o));
            VM.sysWrite(" of type ");
            VM.sysWrite(Magic.getObjectType(o).getDescriptor());
            VM.sysWriteln();
          }
          try {
            java.lang.JikesRVMSupport.invokeFinalize(o);
          } catch (Throwable e) {
            if (verbose >= 1) VM.sysWriteln("Throwable exception caught for finalize call");
          }
          if (verbose >= 2) {
            VM.sysWriteln("FinalizerThread done with object at ", Magic.objectAsAddress(o));
          }
        }
        if (verbose >= 1) VM.sysWriteln("FinalizerThread finished finalization");

      }          // while (true)
    } catch (Throwable e) {
      VM.sysWriteln("Unexpected exception thrown in finalizer thread: ", e.toString());
      e.printStackTrace();
    }

  }  // run

}
