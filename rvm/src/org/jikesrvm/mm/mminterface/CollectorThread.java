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
package org.jikesrvm.mm.mminterface;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.mm.mmtk.Collection;
import org.jikesrvm.mm.mmtk.MMTk_Events;
import org.jikesrvm.mm.mmtk.ScanThread;
import org.jikesrvm.mm.mmtk.Scanning;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Time;
import org.jikesrvm.scheduler.Synchronization;
import org.jikesrvm.scheduler.RVMThread;
import org.mmtk.plan.Plan;
import org.mmtk.utility.heap.HeapGrowthManager;
import org.mmtk.utility.options.Options;
import org.vmmagic.pragma.BaselineNoRegisters;
import org.vmmagic.pragma.BaselineSaveLSRegisters;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.NoOptCompile;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.pragma.UnpreemptibleNoWarn;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * System thread used to preform garbage collections.
 *
 * These threads are created by VM.boot() at runtime startup. One is created for
 * each processor that will (potentially) participate in garbage collection.
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
 * Between collections, the collector threads are parked on a pthread
 * condition variable.  A collection in initiated by a call to the static
 * {@link #collect()} method, which calls
 * {@link Handshake#requestAndAwaitCompletion} to signal the threads.
 * The collection commences when all
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
public final class CollectorThread extends RVMThread {

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
   * @param processorAffinity The processor with which this thread is
   * associated.
   */
  CollectorThread(byte[] stack) {
    super(stack, myName);
    this.collectorContext = new Selected.Collector(this);
    this.collectorContext.initCollector(nextId++);
    makeDaemon(true); // this is redundant, but harmless
  }

  /** Next collector thread id. Collector threads are not created concurrently. */
  private static int nextId = 0;

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
  }
  public static void boot() {
    handshake.boot();
    gcBarrier.boot();
  }

  /**
   * Make a collector thread that will participate in gc.<p>
   *
   * Note: the new thread's stack must be in pinned memory: currently
   * done by allocating it in immortal memory.
   *
   * @return a new collector thread
   */
  @Interruptible
  public static CollectorThread createActiveCollectorThread() {
    byte[] stack = MemoryManager.newStack(ArchitectureSpecific.StackframeLayoutConstants.STACK_SIZE_COLLECTOR);
    return new CollectorThread(stack);
  }

  /**
   * Initiate a garbage collection.  Called by a mutator thread when
   * its allocator runs out of space.  The caller should pass the
   * Handshake that was referenced by the static variable "collect"
   * at the time space was unavailable.
   *
   * @param handshake Handshake for the requested collection
   */
  @Unpreemptible("Becoming another thread interrupts the current thread, avoid preemption in the process")
  public static void collect(Handshake handshake, int why) {
    RVMThread.getCurrentFeedlet().addEvent(MMTk_Events.events.gcStart, why);
    handshake.requestAndAwaitCompletion(why);
    RVMThread.getCurrentFeedlet().addEvent(MMTk_Events.events.gcStop);
  }
  /**
   * Initiate a garbage collection at next GC safe point.  Called by a
   * mutator thread at any time.  The caller should pass the
   * Handshake that was referenced by the static variable
   * "collect".
   *
   * @param handshake Handshake for the requested collection
   */
  @Unpreemptible("Becoming another thread interrupts the current thread, avoid preemption in the process")
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
   * Run method for collector thread (one per processor).  Enters
   * an infinite loop, waiting for collections to be requested,
   * performing those collections, and then waiting again.  Calls
   * Collection.collect to perform the collection, which will be
   * different for the different allocators/collectors that the RVM
   * can be configured to use.
   */
  @NoOptCompile
  // refs stored in registers by opt compiler will not be relocated by GC
  @BaselineNoRegisters
  // refs stored in registers by baseline compiler will not be relocated by GC, so use stack only
  @BaselineSaveLSRegisters
  // and store all registers from previous method in prologue, so that we can stack access them while scanning this thread.
  @Unpreemptible
  public void run() {
    // this is kind of stupid.
    gcOrdinal = Synchronization.fetchAndAdd(participantCount, Offset.zero(), 1) + GC_ORDINAL_BASE;
    RVMThread.getCurrentThread().disableYieldpoints();
    for (int count = 0; ; count++) {
      // wait for collection to start

      RVMThread.getCurrentThread().enableYieldpoints();
      /* suspend this thread: it will resume when scheduled by
       * Handshake.request(). */
      handshake.parkCollectorThread();

      RVMThread.getCurrentThread().disableYieldpoints();
      if (verbose >= 2) VM.sysWriteln("GC Message: CT.run waking up");

      long startTime = Time.nanoTime();

      if (verbose > 2) VM.sysWriteln("GC Message: CT.run entering first rendezvous - gcOrdinal =", gcOrdinal);

      boolean userTriggered = handshake.gcTrigger == Collection.EXTERNAL_GC_TRIGGER;
      boolean internalPhaseTriggered = handshake.gcTrigger == Collection.INTERNAL_PHASE_GC_TRIGGER;
      if (gcOrdinal == GC_ORDINAL_BASE) {
        Plan.setCollectionTrigger(handshake.gcTrigger);
      }
      /* block all threads.  note that some threads will have already blocked
         themselves (if they had made their own GC requests). */
      if (gcOrdinal == GC_ORDINAL_BASE) {
        if (verbose>=2) VM.sysWriteln("Thread #",getThreadSlot()," is about to block a bunch of threads.");
        RVMThread.handshakeLock.lockNoHandshake();
        // fixpoint until there are no threads that we haven't blocked.
        // fixpoint is needed in case some thread spawns another thread
        // while we're waiting.  that is unlikely but possible.
        for (;;) {
          RVMThread.acctLock.lockNoHandshake();
          int numToHandshake=0;
          for (int i=0;i<RVMThread.numThreads;++i) {
            RVMThread t=threads[i];
            if (!(t.isGCThread()) &&
                !t.ignoreHandshakesAndGC()) {
              RVMThread.handshakeThreads[numToHandshake++]=t;
            }
          }
          RVMThread.acctLock.unlock();

          for (int i=0;i<numToHandshake;++i) {
            RVMThread t=RVMThread.handshakeThreads[i];
            t.monitor().lockNoHandshake();
            if (t.blockedFor(RVMThread.gcBlockAdapter) ||
                RVMThread.notRunning(t.asyncBlock(RVMThread.gcBlockAdapter))) {
              // already blocked or not running, remove
              RVMThread.handshakeThreads[i--]=
                RVMThread.handshakeThreads[--numToHandshake];
              RVMThread.handshakeThreads[numToHandshake]=null; // help GC
            }
            t.monitor().unlock();
          }
          // quit trying to block threads if all threads are either blocked
          // or not running (a thread is "not running" if it is NEW or TERMINATED;
          // in the former case it means that the thread has not had start()
          // called on it while in the latter case it means that the thread
          // is either in the TERMINATED state or is about to be in that state
          // real soon now, and will not perform any heap-related stuff before
          // terminating).
          if (numToHandshake==0) break;
          for (int i=0;i<numToHandshake;++i) {
            if (verbose>=2) VM.sysWriteln("waiting for ",RVMThread.handshakeThreads[i].getThreadSlot()," to block");
            RVMThread t=RVMThread.handshakeThreads[i];
            RVMThread.observeExecStatusAtSTW(t.block(RVMThread.gcBlockAdapter));
            RVMThread.handshakeThreads[i]=null; // help GC
          }
        }
        RVMThread.handshakeLock.unlock();

        RVMThread.processAboutToTerminate(); /*
                                              * ensure that any threads that died while
                                              * we were stopping the world notify the
                                              * GC that they had stopped.
                                              */

        if (verbose>=2) {
          VM.sysWriteln("Thread #",getThreadSlot()," just blocked a bunch of threads.");
          RVMThread.dumpAcct();
        }
      }

      /* wait for other collector threads to arrive or be made
       * non-participants */
      if (verbose >= 2) VM.sysWriteln("GC Message: CT.run  initializing rendezvous");
      gcBarrier.startupRendezvous();
      for (;;) {
        /* actually perform the GC... */
        if (verbose >= 2) VM.sysWriteln("GC Message: CT.run  starting collection");
        Selected.Collector.get().collect(); // gc
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
        boolean cont=Selected.Plan.get().lastCollectionFailed() && !Plan.isEmergencyCollection();
        if (!cont) break;
      }

      /* wait for other collector threads to arrive here */
      rendezvous(5210);
      if (verbose > 2) VM.sysWriteln("CollectorThread: past rendezvous 1 after collection");

      if (gcOrdinal == GC_ORDINAL_BASE && !internalPhaseTriggered) {
        /* If the collection failed, we may need to throw OutOfMemory errors.
         * As we have not cleared the GC flag, allocation is not budgeted.
         *
         * This is not flawless in the case we physically can not allocate
         * anything right after a GC, but that case is unlikely (we can
         * not make it happen) and is a lot of work to get around. */
        if (Plan.isEmergencyCollection()) {
          RVMThread.getCurrentThread().setEmergencyAllocation();
          boolean gcFailed = Selected.Plan.get().lastCollectionFailed();
          // Allocate OOMEs (some of which *may* not get used)
          for(int t=0; t < RVMThread.numThreads; t++) {
            RVMThread thread = RVMThread.threads[t];
            if (thread != null) {
              if (thread.getCollectionAttempt() > 0) {
                /* this thread was allocating */
                if (gcFailed || thread.physicalAllocationFailed()) {
                  allocateOOMEForThread(thread);
                }
              }
            }
          }
          RVMThread.getCurrentThread().clearEmergencyAllocation();
        }
      }

      /* Wake up mutators waiting for this gc cycle and reset
       * the handshake object to be used for next gc cycle.
       * Note that mutators will not run until after thread switching
       * is enabled, so no mutators can possibly arrive at old
       * handshake object: it's safe to replace it with a new one. */
      if (gcOrdinal == GC_ORDINAL_BASE) {

        // reset the handshake.  this ensures that once threads are awakened,
        // any new GC requests that they make actually result in GC activity.
        handshake.reset();
        if (verbose>=2) VM.sysWriteln("Thread #",getThreadSlot()," just reset the handshake.");

        Plan.collectionComplete();
        if (verbose>=2) VM.sysWriteln("Marked the collection as complete.");

        collectionAttemptBase = 0;

        if (verbose>=2) VM.sysWriteln("Thread #",getThreadSlot()," is unblocking a bunch of threads.");
        // and now unblock all threads
        RVMThread.handshakeLock.lockNoHandshake();
        RVMThread.acctLock.lockNoHandshake();
        int numToHandshake=0;
        for (int i=0;i<RVMThread.numThreads;++i) {
          RVMThread t=threads[i];
          if (!(t.isGCThread()) &&
              !t.ignoreHandshakesAndGC()) {
            RVMThread.handshakeThreads[numToHandshake++]=t;
          }
        }
        RVMThread.acctLock.unlock();
        for (int i=0;i<numToHandshake;++i) {
          RVMThread.handshakeThreads[i].unblock(RVMThread.gcBlockAdapter);
          RVMThread.handshakeThreads[i]=null; // help GC
        }
        RVMThread.handshakeLock.unlock();
        if (verbose>=2) VM.sysWriteln("Thread #",getThreadSlot()," just unblocked a bunch of threads.");

        /* schedule the FinalizerThread, if there is work to do & it is idle */
        Collection.scheduleFinalizerThread();
      }

      /* final cleanup for initial collector thread */
      if (gcOrdinal == GC_ORDINAL_BASE) {
        /* clear the GC flags */
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
  @UnpreemptibleNoWarn("Calls out to interruptible OOME constructor")
  public void allocateOOMEForThread(RVMThread thread) {
    /* We are running inside a gc thread, so we will allocate if physically possible */
    this.setThreadForStackTrace(thread);
    thread.setOutOfMemoryError(new OutOfMemoryError());
    this.clearThreadForStackTrace();
  }

}

