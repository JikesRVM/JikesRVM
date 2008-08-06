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
package org.jikesrvm.scheduler.greenthreads;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.Magic;
import static org.jikesrvm.runtime.SysCall.sysCall;
import org.jikesrvm.runtime.Time;
import org.jikesrvm.scheduler.Scheduler;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Low priority thread to run when there's nothing else to do.
 * This thread also handles initializing the virtual processor
 * for execution.
 *
 * This follows the Singleton pattern.
 */
@NonMoving
final class IdleThread extends GreenThread {
  /**
   * Should we call Processor.initializeProcessor as the first action
   * of run?  True for every idle thread except the one that runs on the
   * primordial processor.
   */
  private boolean runInitProc;

  /**
   * A thread to run if there is no other work for a virtual processor.
   */
  IdleThread(GreenProcessor processorAffinity, boolean runInitProcessor) {
    super("IdleThread");
    makeDaemon(true);
    super.processorAffinity = processorAffinity;
    runInitProc = runInitProcessor;
    setPriority(Thread.MIN_PRIORITY);
  }

  /**
   * Is this the idle thread?
   * @return true
   */
  @Uninterruptible
  @Override
  public boolean isIdleThread() {
    return true;
  }

  @Override
  public void run() { // overrides Thread
    if (state != State.RUNNABLE)
      changeThreadState(State.NEW, State.RUNNABLE);
    GreenProcessor myProcessor = GreenProcessor.getCurrentProcessor();
    if (VM.ExtremeAssertions) VM._assert(myProcessor == processorAffinity);

    if (runInitProc) myProcessor.initializeProcessor();

    // Only perform load balancing if there is more than one processor.
    final boolean loadBalancing = GreenScheduler.numProcessors > 1;
    long spinNano = loadBalancing ? ((long)1e6) : 0;
    main:
    while (true) {
      if (Scheduler.terminated) terminate();
      if (VM.VerifyAssertions) VM._assert(processorAffinity.idleQueue.isEmpty());
      long t = Time.nanoTime() + spinNano;

      if (Scheduler.debugRequested) {
        VM.sysWriteln("debug requested in idle thread");
        Scheduler.debugRequested = false;
      }

      do {
        GreenProcessor.idleProcessor = myProcessor;
        if (availableWork(myProcessor)) {
          if (VM.ExtremeAssertions) {
            VM._assert(myProcessor == GreenProcessor.getCurrentProcessor());
          }
          GreenThread.yield(GreenProcessor.getCurrentProcessor().idleQueue);
          continue main;
        }
      } while (Time.nanoTime() < t);

      /* Now go into the long-term sleep/check-for-work loop. */
      for (; ;) {
        sysCall.sysVirtualProcessorYield();
        if (availableWork(myProcessor)) {
          continue main;
        }
        GreenThread.yield(GreenProcessor.getCurrentProcessor().idleQueue);
        /* Doze a millisecond (well, Linux rounds it up to a centisecond)  */
        sysCall.sysNanosleep((long)1e6);
      }
    }
  }

  /**
   * @return true, if there appears to be a runnable thread for the processor to execute
   */
  private static boolean availableWork(GreenProcessor p) {
    if (!p.readyQueue.isEmpty()) return true;
    Magic.isync();
    if (!p.transferQueue.isEmpty()) return true;
    if (p.ioQueue.isReady()) return true;
    if (GreenScheduler.wakeupQueue.isReady()) {
      GreenScheduler.wakeupMutex.lock("wakeup mutex");
      GreenThread t = GreenScheduler.wakeupQueue.dequeue();
      GreenScheduler.wakeupMutex.unlock();
      if (t != null) {
        t.schedule();
        return true;
      }
    }
    return false;
  }
}
