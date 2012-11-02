/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.scheduler;

import org.jikesrvm.VM;
import static org.jikesrvm.runtime.SysCall.sysCall;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;

/**
 * The timer thread.  Although we are using purely native threading, threads
 * need to occasionally be poked for the purposes of sampling and OSR.
 * <p>
 * It should be noted that the implementation of this class prioritizes
 * unobtrusiveness and lock-freedom over precision.  For example, on any given
 * timer release some threads may be missed or poked more than once, with the
 * understanding that if they are missed on one release then they will (with
 * high probability) not be missed on a future release.
 * <p>
 * It may be that to make the system scale, more than one timer thread will
 * be needed.  But for now, this should suffice.
 */
@Uninterruptible
@NonMoving
public class TimerThread extends SystemThread {
  private static final int verbose = 0;
  public TimerThread() {
    super("TimerThread");
  }
  // NOTE: this runs concurrently with stop-the-world GC
  // TODO: consider allowing GC to be sampled to enable profile-directed optimization of MMTk.
  @Override
  public void run() {
    VM.disableYieldpoints();
    if (verbose>=1) VM.sysWriteln("TimerThread run routine entered");
    try {
      for (;;) {
        sysCall.sysNanoSleep(1000L*1000L*VM.interruptQuantum);

        if (VM.BuildForAdaptiveSystem) {
          // grab the lock to prevent threads from getting GC'd while we are
          // iterating (since this thread doesn't stop for GC)
          RVMThread.acctLock.lockNoHandshake();
          RVMThread.timerTicks++;
          for (int i=0;i<RVMThread.numThreads;++i) {
            RVMThread candidate=RVMThread.threads[i];
            if (candidate!=null && candidate.shouldBeSampled()) {
              candidate.timeSliceExpired++;
              candidate.takeYieldpoint=1;
            }
          }
          RVMThread.acctLock.unlock();
        }

        RVMThread.checkDebugRequest();
      }
    } catch (Throwable e) {
      printExceptionAndDie(e);
    }
  }
  @UninterruptibleNoWarn
  private static void printExceptionAndDie(Throwable e) {
    VM.sysWriteln("Unexpected exception thrown in timer thread: ",e.toString());
    e.printStackTrace();
    VM.sysFail("Died in timer thread.");
  }
}

