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
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.mm.mmtk.Collection;
import org.jikesrvm.mm.mmtk.ScanThread;
import org.jikesrvm.mm.mmtk.Scanning;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Time;
import org.jikesrvm.scheduler.Scheduler;
import org.jikesrvm.scheduler.Synchronization;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.scheduler.greenthreads.GreenProcessor;
import org.jikesrvm.scheduler.greenthreads.GreenScheduler;
import org.jikesrvm.scheduler.greenthreads.GreenThread;
import org.mmtk.plan.Plan;
import org.mmtk.utility.heap.HeapGrowthManager;
import org.mmtk.utility.options.Options;
import org.vmmagic.pragma.BaselineNoRegisters;
import org.vmmagic.pragma.BaselineSaveLSRegisters;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.LogicallyUninterruptible;
import org.vmmagic.pragma.NoOptCompile;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * System thread used to preform garbage collections.
 *
 * These threads are created by VM.boot() at runtime startup. One is created for
 * each Processor that will (potentially) participate in garbage collection.
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
 * Between collections, the collector threads reside on the Scheduler
 * collectorQueue. A collection in initiated by a call to the static
 * {@link #collect()} method, which calls
 * {@link Handshake#requestAndAwaitCompletion()} to dequeue the collector
 * threads and schedule them for execution. The collection commences when all
 * scheduled collector threads arrive at the first "rendezvous" in the run
 * methods run loop.
 *
 * An instance of Handshake contains state information for the "current"
 * collection. When a collection is finished, a new Handshake is allocated
 * for the next garbage collection.
 *
 * @see Handshake
 */
@NonMoving
public final class CollectorThread extends GreenThread {

  /***********************************************************************
   *
   * Class variables
   */
  private static final int verbose = 0;

  /** Name used by toString() and when we create the associated
   * java.lang.Thread.  */
  private static final String myName = "CollectorThread";

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

  /** gc threads are indexed from 1 for now... */
  public static final int GC_ORDINAL_BASE = 1;

  /** array of size 1 to count arriving collector threads */
  static final int[] participantCount;

  /** maps processor id to assoicated collector thread */
  static CollectorThread[] collectorThreads;

  /** number of collections */
  static int collectionCount;

  /**
   * The Handshake object that contains the state of the next or
   * current (in progress) collection.  Read by mutators when
   * detecting a need for a collection, and passed to the collect
   * method when requesting a collection.
   */
  public static final Handshake handshake;

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
  public ScanThread getThreadScanner() { return threadScanner; }

  /***********************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).
   */
  static {
    handshake = new Handshake();
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
  CollectorThread(byte[] stack, boolean isActive, GreenProcessor processorAffinity) {
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
  public RVMThread getThreadForStackTrace() {
    if (stackTraceThread.isZero())
      return this;
    return (RVMThread)Magic.addressAsObject(stackTraceThread);
  }

  /**
   * Set the thread to use for building stack traces.
   */
  @Uninterruptible
  public void setThreadForStackTrace(RVMThread thread) {
    stackTraceThread = Magic.objectAsAddress(thread);
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
    collectorThreads = new CollectorThread[1 + GreenScheduler.MAX_PROCESSORS];
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
  public static CollectorThread createActiveCollectorThread(GreenProcessor processorAffinity) {
    byte[] stack = MM_Interface.newStack(ArchitectureSpecific.StackframeLayoutConstants.STACK_SIZE_COLLECTOR, true);
    return new CollectorThread(stack, true, processorAffinity);
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
  static CollectorThread createPassiveCollectorThread(byte[] stack, GreenProcessor processorAffinity) {
    return new CollectorThread(stack, false, processorAffinity);
  }

  /**
   * Initiate a garbage collection.  Called by a mutator thread when
   * its allocator runs out of space.  The caller should pass the
   * Handshake that was referenced by the static variable "collect"
   * at the time space was unavailable.
   *
   * @param handshake Handshake for the requested collection
   */
  @LogicallyUninterruptible
  @Uninterruptible
  public static void collect(Handshake handshake, int why) {
    handshake.requestAndAwaitCompletion(why);
  }

  /**
   * Initiate a garbage collection at next GC safe point.  Called by a
   * mutator thread at any time.  The caller should pass the
   * Handshake that was referenced by the static variable
   * "collect".
   *
   * @param handshake Handshake for the requested collection
   */
  @Uninterruptible
  public static void asyncCollect(Handshake handshake, int why) {
    handshake.requestAndContinue(why);
  }

  /**
   * Override Thread.toString
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
  public int getGCOrdinal() {
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
  public void setGCOrdinal(int ord) {
    gcOrdinal = ord;
  }

  /**
   * Run method for collector thread (one per Processor).  Enters
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
       * Handshake initiateCollection().  while suspended,
       * collector threads reside on the schedulers collectorQueue */
      GreenScheduler.collectorMutex.lock("collector mutex");
      if (verbose >= 1) VM.sysWriteln("GC Message: CT.run yielding");
      if (count > 0) { // resume normal scheduling
        GreenProcessor.getCurrentProcessor().enableThreadSwitching();
      }
      GreenScheduler.getCurrentThread().yield(GreenScheduler.collectorQueue,
          GreenScheduler.collectorMutex);

      /* block mutators from running on the current processor */
      GreenProcessor.getCurrentProcessor().disableThreadSwitching("Disabled in collector to stop mutators from running on current processor");

      if (verbose >= 2) VM.sysWriteln("GC Message: CT.run waking up");

      gcOrdinal = Synchronization.fetchAndAdd(participantCount, Offset.zero(), 1) + GC_ORDINAL_BASE;
      long startTime = Time.nanoTime();

      if (verbose > 2) VM.sysWriteln("GC Message: CT.run entering first rendezvous - gcOrdinal =", gcOrdinal);

      boolean userTriggered = handshake.gcTrigger == Collection.EXTERNAL_GC_TRIGGER;
      boolean internalPhaseTriggered = handshake.gcTrigger == Collection.INTERNAL_PHASE_GC_TRIGGER;
      if (gcOrdinal == GC_ORDINAL_BASE) {
        Plan.setCollectionTrigger(handshake.gcTrigger);
      }

      /* wait for other collector threads to arrive or be made
       * non-participants */
      if (verbose >= 2) VM.sysWriteln("GC Message: CT.run  initializing rendezvous");
      gcBarrier.startupRendezvous();
      do {
        /* actually perform the GC... */
        if (verbose >= 2) VM.sysWriteln("GC Message: CT.run  starting collection");
        if (isActive) Selected.Collector.get().collect(); // gc
        if (verbose >= 2) VM.sysWriteln("GC Message: CT.run  finished collection");

        gcBarrier.rendezvous(5200);

        if (gcOrdinal == GC_ORDINAL_BASE) {
          long elapsedTime = Time.nanoTime() - startTime;
          HeapGrowthManager.recordGCTime(Time.nanosToMillis(elapsedTime));
          if (Selected.Plan.get().lastCollectionFullHeap() && !internalPhaseTriggered) {
            if (Options.variableSizeHeap.getValue() && !userTriggered) {
              // Don't consider changing the heap size if gc was forced by System.gc()
              HeapGrowthManager.considerHeapSize();
            }
            HeapGrowthManager.reset();
          }

          if (internalPhaseTriggered) {
            if (Selected.Plan.get().lastCollectionFailed()) {
              internalPhaseTriggered = false;
              Plan.setCollectionTrigger(Collection.INTERNAL_GC_TRIGGER);
            }
          }

          if (Scanning.threadStacksScanned()) {
            /* Snip reference to any methods that are still marked
             * obsolete after we've done stack scans. This allows
             * reclaiming them on the next GC. */
            CompiledMethods.snipObsoleteCompiledMethods();
            Scanning.clearThreadStacksScanned();

            collectionAttemptBase++;
          }

          collectionCount += 1;
        }

        startTime = Time.nanoTime();
        gcBarrier.rendezvous(5201);
      } while (Selected.Plan.get().lastCollectionFailed() && !Plan.isEmergencyCollection());

      if (gcOrdinal == GC_ORDINAL_BASE && !internalPhaseTriggered) {
        /* If the collection failed, we may need to throw OutOfMemory errors.
         * As we have not cleared the GC flag, allocation is not budgeted.
         *
         * This is not flawless in the case we physically can not allocate
         * anything right after a GC, but that case is unlikely (we can
         * not make it happen) and is a lot of work to get around. */
        if (Plan.isEmergencyCollection()) {
          Scheduler.getCurrentThread().setEmergencyAllocation();
          boolean gcFailed = Selected.Plan.get().lastCollectionFailed();
          // Allocate OOMEs (some of which *may* not get used)
          for(int t=0; t <= Scheduler.getThreadHighWatermark(); t++) {
            RVMThread thread = Scheduler.threads[t];
            if (thread != null) {
              if (thread.getCollectionAttempt() > 0) {
                /* this thread was allocating */
                if (gcFailed || thread.physicalAllocationFailed()) {
                  allocateOOMEForThread(thread);
                }
              }
            }
          }
          Scheduler.getCurrentThread().clearEmergencyAllocation();
        }
      }

      /* Wake up mutators waiting for this gc cycle and reset
       * the handshake object to be used for next gc cycle.
       * Note that mutators will not run until after thread switching
       * is enabled, so no mutators can possibly arrive at old
       * handshake object: it's safe to replace it with a new one. */
      if (gcOrdinal == GC_ORDINAL_BASE) {
        collectionAttemptBase = 0;
        /* notify mutators waiting on previous handshake object -
         * actually we don't notify anymore, mutators are simply in
         * processor ready queues waiting to be dispatched. */
        handshake.notifyCompletion();
        handshake.reset();

        /* schedule the FinalizerThread, if there is work to do & it is idle */
        Collection.scheduleFinalizerThread();
      }

      /* wait for other collector threads to arrive here */
      rendezvous(5210);
      if (verbose > 2) VM.sysWriteln("CollectorThread: past rendezvous 1 after collection");

      /* final cleanup for initial collector thread */
      if (gcOrdinal == GC_ORDINAL_BASE) {
        /* It is VERY unlikely, but possible that some RVM processors
         * were found in C, and were BLOCKED_IN_NATIVE, during the
         * collection, and now need to be unblocked. */
        if (verbose >= 2) VM.sysWriteln("GC Message: CT.run unblocking procs blocked in native during GC");
        for (int i = 1; i <= GreenScheduler.numProcessors; i++) {
          GreenProcessor vp = GreenScheduler.getProcessor(i);
          if (VM.VerifyAssertions) VM._assert(vp != null);
          if (vp.vpStatus == GreenProcessor.BLOCKED_IN_NATIVE) {
            vp.vpStatus = GreenProcessor.IN_NATIVE;
            if (verbose >= 2) VM.sysWriteln("GC Message: CT.run unblocking RVM Processor", vp.id);
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
  public void allocateOOMEForThread(RVMThread thread) {
    /* We are running inside a gc thread, so we will allocate if physically possible */
    this.setThreadForStackTrace(thread);
    thread.setOutOfMemoryError(new OutOfMemoryError());
    this.clearThreadForStackTrace();
  }

  /*
  @Uninterruptible
  public static void printThreadWaitTimes() {
    VM.sysWrite("*** Collector Thread Wait Times (in micro-secs)\n");
    for (int i = 1; i <= Scheduler.numProcessors; i++) {
      CollectorThread ct = Magic.threadAsCollectorThread(Scheduler.processors[i].activeThread );
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
