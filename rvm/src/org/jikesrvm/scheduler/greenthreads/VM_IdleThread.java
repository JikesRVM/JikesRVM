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
import org.jikesrvm.runtime.VM_Magic;
import static org.jikesrvm.runtime.VM_SysCall.sysCall;
import org.jikesrvm.runtime.VM_Time;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Low priority thread to run when there's nothing else to do.
 * This thread also handles initializing the virtual processor
 * for execution.
 *
 * This follows the Singleton pattern.
 */
final class VM_IdleThread extends VM_GreenThread {

  /**
   * Attempt rudimentary load balancing.  If a virtual processor
   * has no work it asks for some then spins for time waiting for
   * an runnable thread to arrive.  If none does, or if this variable
   * is false, the remaining time-slice is returned to the operating
   * system.
   */
  private static boolean loadBalancing;

  /**
   * Should we call VM_Processor.initializeProcessor as the first action
   * of run?  True for every idle thread except the one that runs on the
   * primordial processor.
   */
  private boolean runInitProc;

  private static final String myName = "VM_IdleThread";

  /**
   * A thread to run if there is no other work for a virtual processor.
   */
  VM_IdleThread(VM_GreenProcessor processorAffinity, boolean runInitProcessor) {
    super(myName);
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
  public void run() { // overrides VM_Thread
    if (state != State.RUNNABLE)
      changeThreadState(State.NEW, State.RUNNABLE);
    loadBalancing = VM_GreenScheduler.numProcessors > 1;
    VM_GreenProcessor myProcessor = VM_GreenProcessor.getCurrentProcessor();
    if (VM.ExtremeAssertions) VM._assert(myProcessor == processorAffinity);

    if (runInitProc) myProcessor.initializeProcessor();
    long spinInterval = loadBalancing ? VM_Time.millisToCycles(1) : 0;
    main:
    while (true) {
      if (VM_Scheduler.terminated) terminate();
      if (VM.VerifyAssertions) VM._assert(processorAffinity.idleQueue.isEmpty());
      long t = VM_Time.cycles() + spinInterval;

      if (VM_Scheduler.debugRequested) {
        VM.sysWriteln("debug requested in idle thread");
        VM_Scheduler.debugRequested = false;
      }

      do {
        VM_GreenProcessor.idleProcessor = myProcessor;
        if (availableWork(myProcessor)) {
          if (VM.ExtremeAssertions) {
            VM._assert(myProcessor == VM_GreenProcessor.getCurrentProcessor());
          }
          VM_GreenThread.yield(VM_GreenProcessor.getCurrentProcessor().idleQueue);
          continue main;
        }
      } while (VM_Time.cycles() < t);

      /* Now go into the long-term sleep/check-for-work loop. */
      for (; ;) {
        sysCall.sysVirtualProcessorYield();
        if (availableWork(myProcessor)) {
          continue main;
        }
        VM_GreenThread.yield(VM_GreenProcessor.getCurrentProcessor().idleQueue);
        /* Doze a millisecond (well, Linux rounds it up to a centisecond)  */
        sysCall.sysNanosleep(1000 * 1000);
      }
    }
  }

  /**
   * @return true, if there appears to be a runnable thread for the processor to execute
   */
  private static boolean availableWork(VM_GreenProcessor p) {
    if (!p.readyQueue.isEmpty()) return true;
    VM_Magic.isync();
    if (!p.transferQueue.isEmpty()) return true;
    if (p.ioQueue.isReady()) return true;
    if (VM_GreenScheduler.wakeupQueue.isReady()) {
      VM_GreenScheduler.wakeupMutex.lock("wakeup mutex");
      VM_GreenThread t = VM_GreenScheduler.wakeupQueue.dequeue();
      VM_GreenScheduler.wakeupMutex.unlock();
      if (t != null) {
        t.schedule();
        return true;
      }
    }
    return false;
  }

}
