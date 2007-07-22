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
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.mm.mmtk.Collection;
import org.jikesrvm.mm.mmtk.ScanThread;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Time;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.VM_Synchronization;
import org.jikesrvm.scheduler.VM_Thread;
import org.jikesrvm.scheduler.greenthreads.VM_GreenProcessor;
import org.jikesrvm.scheduler.greenthreads.VM_GreenScheduler;
import org.jikesrvm.scheduler.greenthreads.VM_GreenThread;
import org.mmtk.plan.Plan;
import org.mmtk.utility.heap.HeapGrowthManager;
import org.mmtk.utility.options.Options;
import org.vmmagic.pragma.BaselineNoRegisters;
import org.vmmagic.pragma.BaselineSaveLSRegisters;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.LogicallyUninterruptible;
import org.vmmagic.pragma.NoOptCompile;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * System thread used to preform garbage collections.
 *
 * These threads are created by VM.boot() at runtime startup. One is created for
 * each VM_Processor that will (potentially) participate in garbage collection.
 *
 * <pre>
 * Its &quot;run&quot; method does the following:
 *    1. wait for a collection request
 *    2. synchronize with other collector threads (stop mutation)
 *    3. reclaim space
 *    4. synchronize with other collector threads (resume mutation)
 *    5. goto 1
 * </pre>
 *
 * Between collections, the collector threads reside on the VM_Scheduler
 * collectorQueue. A collection in initiated by a call to the static
 * {@link #collect()} method, which calls
 * {@link VM_Handshake#requestAndAwaitCompletion()} to dequeue the collector
 * threads and schedule them for execution. The collection commences when all
 * scheduled collector threads arrive at the first "rendezvous" in the run
 * methods run loop.
 *
 * An instance of VM_Handshake contains state information for the "current"
 * collection. When a collection is finished, a new VM_Handshake is allocated
 * for the next garbage collection.
 *
 * @see VM_Handshake
 */
public final class VM_CollectorThread extends VM_GreenThread {

  /***********************************************************************
   *
   * Class variables
   */
  private static final int verbose = 0;

  /** Name used by toString() and when we create the associated
   * java.lang.Thread.  */
  private static final String myName = "VM_CollectorThread";

  /** When true, causes RVM collectors to display heap configuration
   * at startup */
  static final boolean DISPLAY_OPTIONS_AT_BOOT = false;

  /**
   * When true, causes RVM collectors to measure time spent in each
   * phase of collection. Will also force summary statistics to be
   * generated.
   */
  public static final boolean TIME_GC_PHASES = false;

  /**
   * When true, collector threads measure time spent waiting for
   * buffers while processing the Work Deque, and time spent waiting
   * in Rendezvous during the collection process. Will also force
   * summary statistics to be generated.
   */
  public static final boolean MEASURE_WAIT_TIMES = false;

  /** array of size 1 to count arriving collector threads */
  static final int[] participantCount;

  /** maps processor id to assoicated collector thread */
  static VM_CollectorThread[] collectorThreads;

  /** number of collections */
  static int collectionCount;

  /**
   * The VM_Handshake object that contains the state of the next or
   * current (in progress) collection.  Read by mutators when
   * detecting a need for a collection, and passed to the collect
   * method when requesting a collection.
   */
  public static final VM_Handshake handshake;

  /** Use by collector threads to rendezvous during collection */
  public static SynchronizationBarrier gcBarrier;

  /** The base collection attempt */
  public static int collectionAttemptBase = 0;

  /***********************************************************************
   *
   * Instance variables
   */
  /** are we an "active participant" in gc? */
  boolean isActive;
  /** arrival order of collectorThreads participating in a collection */
  private int gcOrdinal;

  /** used by each CollectorThread when scanning stacks for references */
  private final ScanThread threadScanner = new ScanThread();

  /** time waiting in rendezvous (milliseconds) */
  int timeInRendezvous;

  static boolean gcThreadRunning;

  /** The thread to use to determine stack traces if Throwables are created **/
  private Address stackTraceThread;

  /** @return the thread scanner instance associated with this instance */
  @Uninterruptible
  public final ScanThread getThreadScanner() { return threadScanner; }

  /***********************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).
   */
  static {
    handshake = new VM_Handshake();
    participantCount = new int[1]; // counter for threads starting a collection
  }

  /**
   * Constructor
   *
   * @param stack The stack this thread will run on
   * @param isActive Whether or not this thread will participate in GC
   * @param processorAffinity The processor with which this thread is
   * associated.
   */
  VM_CollectorThread(byte[] stack, boolean isActive, VM_GreenProcessor processorAffinity) {
    super(stack, myName);
    makeDaemon(true); // this is redundant, but harmless
    this.isActive          = isActive;
    this.processorAffinity = processorAffinity;

    /* associate this collector thread with its affinity processor */
    collectorThreads[processorAffinity.id] = this;
  }

  /**
   * Is this the GC thread?
   * @return true
   */
  @Uninterruptible
  public boolean isGCThread() {
    return true;
  }

  /**
   * Get the thread to use for building stack traces.
   */
  @Uninterruptible
  @Override
  public VM_Thread getThreadForStackTrace() {
    if (stackTraceThread.isZero())
      return this;
    return (VM_Thread)VM_Magic.addressAsObject(stackTraceThread);
  }

  /**
   * Set the thread to use for building stack traces.
   */
  @Uninterruptible
  public void setThreadForStackTrace(VM_Thread thread) {
    stackTraceThread = VM_Magic.objectAsAddress(thread);
  }

  /**
   * Set the thread to use for building stack traces.
   */
  @Uninterruptible
  public void clearThreadForStackTrace() {
    stackTraceThread = Address.zero();
  }

  /**
   * Initialize for boot image.
   */
  @Interruptible
  public static void init() {
    gcBarrier = new SynchronizationBarrier();
    collectorThreads = new VM_CollectorThread[1 + VM_GreenScheduler.MAX_PROCESSORS];
  }

  /**
   * Make a collector thread that will participate in gc.<p>
   *
   * Note: the new thread's stack must be in pinned memory: currently
   * done by allocating it in immortal memory.
   *
   * @param processorAffinity processor to run on
   * @return a new collector thread
   */
  @Interruptible
  public static VM_CollectorThread createActiveCollectorThread(VM_GreenProcessor processorAffinity) {
    byte[] stack = MM_Interface.newStack(ArchitectureSpecific.VM_StackframeLayoutConstants.STACK_SIZE_COLLECTOR, true);
    return new VM_CollectorThread(stack, true, processorAffinity);
  }

  /**
   * Make a collector thread that will not participate in gc. It will
   * serve only to lock out mutators from the current processor.
   *
   * Note: the new thread's stack must be in pinned memory: currently
   * done by allocating it in immortal memory.
   *
   * @param stack stack to run on
   * @param processorAffinity processor to run on
   * @return a new non-particpating collector thread
   */
  @Interruptible
  static VM_CollectorThread createPassiveCollectorThread(byte[] stack, VM_GreenProcessor processorAffinity) {
    return new VM_CollectorThread(stack, false, processorAffinity);
  }

  /**
   * Initiate a garbage collection.  Called by a mutator thread when
   * its allocator runs out of space.  The caller should pass the
   * VM_Handshake that was referenced by the static variable "collect"
   * at the time space was unavailable.
   *
   * @param handshake VM_Handshake for the requested collection
   */
  @LogicallyUninterruptible
  @Uninterruptible
  public static void collect(VM_Handshake handshake, int why) {
    handshake.requestAndAwaitCompletion(why);
  }

  /**
   * Initiate a garbage collection at next GC safe point.  Called by a
   * mutator thread at any time.  The caller should pass the
   * VM_Handshake that was referenced by the static variable
   * "collect".
   *
   * @param handshake VM_Handshake for the requested collection
   */
  @Uninterruptible
  public static void asyncCollect(VM_Handshake handshake, int why) {
    handshake.requestAndContinue(why);
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
   * Returns number of collector threads participating in a collection
   *
   * @return The number of collector threads participating in a collection
   */
  @Uninterruptible
  public static int numCollectors() {
    return (participantCount[0]);
  }

  /**
   * Return the GC ordinal for this collector thread. An integer,
   * 1,2,...  assigned to each collector thread participating in the
   * current collection.  Only valid while GC is "InProgress".
   *
   * @return The GC ordinal
   */
  @Uninterruptible
  public final int getGCOrdinal() {
    return gcOrdinal;
  }

  /**
   * Set the GC ordinal for this collector thread.  An integer,
   * 1,2,...  assigned to each collector thread participating in the
   * current collection.
   *
   * @param ord The new GC ordinal for this thread
   */
  @Uninterruptible
  public final void setGCOrdinal(int ord) {
    gcOrdinal = ord;
  }

  /**
   * Run method for collector thread (one per VM_Processor).  Enters
   * an infinite loop, waiting for collections to be requested,
   * performing those collections, and then waiting again.  Calls
   * Collection.collect to perform the collection, which will be
   * different for the different allocators/collectors that the RVM
   * can be configured to use.
   */
  @LogicallyUninterruptible
  // due to call to snipObsoleteCompiledMethods
  @NoOptCompile
  // refs stored in registers by opt compiler will not be relocated by GC
  @BaselineNoRegisters
  // refs stored in registers by baseline compiler will not be relocated by GC, so use stack only
  @BaselineSaveLSRegisters
  // and store all registers from previous method in prologue, so that we can stack access them while scanning this thread.
  @Uninterruptible
  public void run() {
    for (int count = 0; ; count++) {
      /* suspend this thread: it will resume when scheduled by
       * VM_Handshake initiateCollection().  while suspended,
       * collector threads reside on the schedulers collectorQueue */
      VM_GreenScheduler.collectorMutex.lock("collector mutex");
      if (verbose >= 1) VM.sysWriteln("GC Message: VM_CT.run yielding");
      if (count > 0) { // resume normal scheduling
        VM_GreenProcessor.getCurrentProcessor().enableThreadSwitching();
      }
      VM_GreenScheduler.getCurrentThread().yield(VM_GreenScheduler.collectorQueue,
          VM_GreenScheduler.collectorMutex);

      /* block mutators from running on the current processor */
      VM_GreenProcessor.getCurrentProcessor().disableThreadSwitching("Disabled in collector to stop mutators from running on current processor");

      if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.run waking up");

      gcOrdinal = VM_Synchronization.fetchAndAdd(participantCount, Offset.zero(), 1) + 1;
      long startCycles = VM_Time.cycles();

      if (verbose > 2) VM.sysWriteln("GC Message: VM_CT.run entering first rendezvous - gcOrdinal =", gcOrdinal);

      boolean userTriggered = handshake.gcTrigger == Collection.EXTERNAL_GC_TRIGGER;
      if (gcOrdinal == 1) {
        Plan.setUserTriggeredCollection(userTriggered);
      }

      /* wait for other collector threads to arrive or be made
       * non-participants */
      if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.run  initializing rendezvous");
      gcBarrier.startupRendezvous();
      do {
        /* actually perform the GC... */
        if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.run  starting collection");
        if (isActive) Selected.Collector.get().collect(); // gc
        if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.run  finished collection");

        gcBarrier.rendezvous(5200);

        if (gcOrdinal == 1) {
          long elapsedCycles = VM_Time.cycles() - startCycles;
          HeapGrowthManager.recordGCTime(VM_Time.cyclesToMillis(elapsedCycles));
          if (Selected.Plan.get().lastCollectionFullHeap()) {
            if (Options.variableSizeHeap.getValue() && !userTriggered) {
              // Don't consider changing the heap size if gc was forced by System.gc()
              HeapGrowthManager.considerHeapSize();
            }
            HeapGrowthManager.reset();
          }

          /* Snip reference to any methods that are still marked
           * obsolete after we've done stack scans. This allows
           * reclaiming them on the next GC. */
          VM_CompiledMethods.snipObsoleteCompiledMethods();

          collectionCount += 1;
          collectionAttemptBase++;
        }

        startCycles = VM_Time.cycles();
        gcBarrier.rendezvous(5201);
      } while (Selected.Plan.get().lastCollectionFailed() && !Plan.isEmergencyCollection());

      if (gcOrdinal == 1) {
        /* If the collection failed, we may need to throw OutOfMemory errors.
         * As we have not cleared the GC flag, allocation is not budgeted.
         *
         * This is not flawless in the case we physically can not allocate
         * anything right after a GC, but that case is unlikely (we can
         * not make it happen) and is a lot of work to get around. */
        if (Plan.isEmergencyCollection()) {
          VM_Scheduler.getCurrentThread().setEmergencyAllocation();
          boolean gcFailed = Selected.Plan.get().lastCollectionFailed();
          // Allocate OOMEs (some of which *may* not get used)
          for(int t=0; t <= VM_Scheduler.getThreadHighWatermark(); t++) {
            VM_Thread thread = VM_Scheduler.threads[t];
            if (thread != null) {
              if (thread.getCollectionAttempt() > 0) {
                /* this thread was allocating */
                if (gcFailed || thread.physicalAllocationFailed()) {
                  allocateOOMEForThread(thread);
                }
              }
            }
          }
          VM_Scheduler.getCurrentThread().clearEmergencyAllocation();
        }
      }

      /* Wake up mutators waiting for this gc cycle and reset
       * the handshake object to be used for next gc cycle.
       * Note that mutators will not run until after thread switching
       * is enabled, so no mutators can possibly arrive at old
       * handshake object: it's safe to replace it with a new one. */
      if (gcOrdinal == 1) {
        collectionAttemptBase = 0;
        /* notify mutators waiting on previous handshake object -
         * actually we don't notify anymore, mutators are simply in
         * processor ready queues waiting to be dispatched. */
        handshake.notifyCompletion();
        handshake.reset();

        /* schedule the VM_FinalizerThread, if there is work to do & it is idle */
        Collection.scheduleFinalizerThread();
      }

      /* wait for other collector threads to arrive here */
      rendezvous(5210);
      if (verbose > 2) VM.sysWriteln("VM_CollectorThread: past rendezvous 1 after collection");

      /* final cleanup for initial collector thread */
      if (gcOrdinal == 1) {
        /* It is VERY unlikely, but possible that some RVM processors
         * were found in C, and were BLOCKED_IN_NATIVE, during the
         * collection, and now need to be unblocked. */
        if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.run unblocking procs blocked in native during GC");
        for (int i = 1; i <= VM_GreenScheduler.numProcessors; i++) {
          VM_GreenProcessor vp = VM_GreenScheduler.processors[i];
          if (VM.VerifyAssertions) VM._assert(vp != null);
          if (vp.vpStatus == VM_GreenProcessor.BLOCKED_IN_NATIVE) {
            vp.vpStatus = VM_GreenProcessor.IN_NATIVE;
            if (verbose >= 2) VM.sysWriteln("GC Message: VM_CT.run unblocking RVM Processor", vp.id);
          }
        }

        /* clear the GC flags */
        Plan.collectionComplete();
        gcThreadRunning = false;
      } // if designated thread
      rendezvous(9999);
    }  // end of while(true) loop

  }  // run

  /**
   * Return true if no threads are still in GC.
   *
   * @return <code>true</code> if no threads are still in GC.
   */
  @Uninterruptible
  public static boolean noThreadsInGC() {
    return !gcThreadRunning;
  }

  @Uninterruptible
  public int rendezvous(int where) {
    return gcBarrier.rendezvous(where);
  }

  /**
   * Allocate an OutOfMemoryError for a given thread.
   * @param thread
   */
  @LogicallyUninterruptible
  @Uninterruptible
  public void allocateOOMEForThread(VM_Thread thread) {
    /* We are running inside a gc thread, so we will allocate if physically possible */
    this.setThreadForStackTrace(thread);
    thread.setOutOfMemoryError(new OutOfMemoryError());
    this.clearThreadForStackTrace();
  }

  /*
  @Uninterruptible
  public static void printThreadWaitTimes() {
    VM.sysWrite("*** Collector Thread Wait Times (in micro-secs)\n");
    for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
      VM_CollectorThread ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.processors[i].activeThread );
      VM.sysWrite(i);
      VM.sysWrite(" SBW ");
      if (ct.bufferWaitCount1 > 0)
        VM.sysWrite(ct.bufferWaitCount1-1);  // subtract finish wait
      else
        VM.sysWrite(0);
      VM.sysWrite(" SBWT ");
      VM.sysWrite(ct.bufferWaitTime1*1000000.0);
      VM.sysWrite(" SFWT ");
      VM.sysWrite(ct.finishWaitTime1*1000000.0);
      VM.sysWrite(" FBW ");
      if (ct.bufferWaitCount > 0)
        VM.sysWrite(ct.bufferWaitCount-1);  // subtract finish wait
      else
        VM.sysWrite(0);
      VM.sysWrite(" FBWT ");
      VM.sysWrite(ct.bufferWaitTime*1000000.0);
      VM.sysWrite(" FFWT ");
      VM.sysWrite(ct.finishWaitTime*1000000.0);
      VM.sysWriteln();

    }
  }
  */
}
