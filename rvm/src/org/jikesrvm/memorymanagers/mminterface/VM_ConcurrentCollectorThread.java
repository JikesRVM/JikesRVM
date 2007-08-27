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
package org.jikesrvm.memorymanagers.mminterface;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.greenthreads.VM_GreenProcessor;
import org.jikesrvm.scheduler.greenthreads.VM_GreenScheduler;
import org.jikesrvm.scheduler.greenthreads.VM_GreenThread;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.LogicallyUninterruptible;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Threads that perform collector work while mutators are active. these
 * threads wait for the collector to activate them.
 */
public final class VM_ConcurrentCollectorThread extends VM_GreenThread {

  /***********************************************************************
   *
   * Class variables
   */
  private static final int verbose = 0;

  /** Name used by toString() and when we create the associated
   * java.lang.Thread.  */
  private static final String myName = "VM_ConcurrentCollectorThread";


  /** maps processor id to associated collector thread */
  public static VM_ConcurrentCollectorThread[] concurrentCollectorThreads;

  /***********************************************************************
   *
   * Instance variables
   */

  /***********************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param stack The stack this thread will run on
   * @param isActive Whether or not this thread will participate in GC
   * @param processorAffinity The processor with which this thread is
   * associated.
   */
  VM_ConcurrentCollectorThread(byte[] stack, VM_GreenProcessor processorAffinity) {
    super(stack, myName);
    makeDaemon(true); // this is redundant, but harmless
    this.processorAffinity = processorAffinity;

    /* associate this collector thread with its affinity processor */
    concurrentCollectorThreads[processorAffinity.id] = this;
  }

  /**
   * Initialize for boot image.
   */
  @Interruptible
  public static void init() {
    concurrentCollectorThreads = new VM_ConcurrentCollectorThread[1 + VM_GreenScheduler.MAX_PROCESSORS];
  }

  /**
   * Make a concurrent collector thread.<p>
   *
   * Note: the new thread's stack must be in pinned memory: currently
   * done by allocating it in immortal memory.
   *
   * @param processorAffinity processor to run on
   * @return a new collector thread
   */
  @Interruptible
  public static VM_ConcurrentCollectorThread createConcurrentCollectorThread(VM_GreenProcessor processorAffinity) {
    byte[] stack = MM_Interface.newStack(ArchitectureSpecific.VM_StackframeLayoutConstants.STACK_SIZE_COLLECTOR, true);
    return new VM_ConcurrentCollectorThread(stack, processorAffinity);
  }

  /**
   * Override VM_Thread.toString
   *
   * @return A string describing this thread.
   */
  @Uninterruptible
  public String toString() {
    return myName;
  }

  /**
   * Run method for concurrent collector thread.
   */
  @LogicallyUninterruptible
  @Uninterruptible
  public void run() {
    if (verbose >= 1) VM.sysWriteln("GC Message: Concurrent collector thread entered run...");

    while (true) {
      /* suspend this thread: it will resume when the garbage collector
       * notifies it there is work to do. */
      VM_Scheduler.suspendConcurrentCollectorThread();

      if (verbose >= 1) VM.sysWriteln("GC Message: Concurrent collector awake");
      Selected.Collector.get().concurrentCollect();
      if (verbose >= 1) VM.sysWriteln("GC Message: Concurrent collector finished");
    }
  }
}

