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

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.mm.mminterface.CollectorThread;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.osr.ObjectHolder;
import org.jikesrvm.runtime.BootRecord;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import static org.jikesrvm.runtime.SysCall.sysCall;
import org.jikesrvm.scheduler.DebuggerThread;
import org.jikesrvm.scheduler.FinalizerThread;
import org.jikesrvm.scheduler.Lock;
import org.jikesrvm.scheduler.Processor;
import org.jikesrvm.scheduler.ProcessorLock;
import org.jikesrvm.scheduler.ProcessorTable;
import org.jikesrvm.scheduler.Scheduler;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.tuningfork.TraceEngine;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;
import org.vmmagic.pragma.Unpreemptible;

/**
 * Global variables used to implement virtual machine thread scheduler.
 *    - virtual cpus
 *    - threads
 *    - queues
 *    - locks
 */
@Uninterruptible
public final class GreenScheduler extends Scheduler {

  /** Index of initial processor in which "VM.boot()" runs. */
  public static final int PRIMORDIAL_PROCESSOR_ID = 1;

  /** Index of thread in which "VM.boot()" runs */

  // A processors id is its index in the processors array & a threads
  // id is its index in the threads array.  id's start at 1, so that
  // id 0 can be used in locking to represent an unheld lock

  /**
   * Maximum number of Processor's that we can support.
   */
  public static final int MAX_PROCESSORS = 12;   // allow processors = 1 to 12

  /** Type of processor */
  private static final TypeReference greenProcessorType =
    TypeReference.findOrCreate(GreenProcessor.class);

  /** Type of thread */
  private static final TypeReference greenThreadType =
    TypeReference.findOrCreate(GreenThread.class);

  /** Maximum number of Thread's that we can support. */
  public static final int LOG_MAX_THREADS = 14;
  public static final int MAX_THREADS = 1 << LOG_MAX_THREADS;

  /** Scheduling quantum in milliseconds: interruptQuantum * interruptQuantumMultiplier */
  public static int schedulingQuantum = 10;

  // Virtual cpu's.
  //
  /** Total number of virtual processors to be used */
  public static int numProcessors = 1;
  /** Array of all virtual processors (slot 0 always empty) */
  @Entrypoint
  public static ProcessorTable processors;
  /** Have all processors completed initialization? */
  public static boolean allProcessorsInitialized;

  // Thread execution.
  //
  /** threads waiting to wake up from a sleep() */
  static final ThreadProxyWakeupQueue wakeupQueue = new ThreadProxyWakeupQueue();
  /** mutex controlling access to wake up queue */
  static final ProcessorLock wakeupMutex = new ProcessorLock();

  /** thread waiting to service debugging requests */
  public static final GreenThreadQueue debuggerQueue = new GreenThreadQueue();
  public static final ProcessorLock debuggerMutex = new ProcessorLock();

  /** collector threads waiting to be resumed */
  public static final GreenThreadQueue collectorQueue = new GreenThreadQueue();
  public static final ProcessorLock collectorMutex = new ProcessorLock();

  /** Concurrent worker threads wait here when idle */
  public static final GreenThreadQueue concurrentCollectorQueue = new GreenThreadQueue();
  public static final ProcessorLock concurrentCollectorMutex = new ProcessorLock();

  /** Finalizer thread waits here when idle */
  public static final GreenThreadQueue finalizerQueue = new GreenThreadQueue();
  public static final ProcessorLock finalizerMutex = new ProcessorLock();

  /** Collectors wait here while waiting for flushes */
  public static final GreenThreadQueue flushMutatorContextsQueue = new GreenThreadQueue();
  public static final ProcessorLock flushMutatorContextsMutex = new ProcessorLock();
  public static int flushedMutatorCount = 0;

  /** How many extra procs (not counting primordial) ? */
  private static int NUM_EXTRA_PROCS = 0;

  /**
   * Initialize boot image.
   */
  @Override
  @Interruptible
  protected void initInternal() {
    threadAllocationIndex = PRIMORDIAL_THREAD_INDEX;

    // Enable us to dump a Java Stack from the C trap handler to aid in debugging things that
    // show up as recursive use of hardware exception registers (eg the long-standing lisp bug)
    BootRecord.the_boot_record.dumpStackAndDieOffset = Entrypoints.dumpStackAndDieMethod.getOffset();

    // allocate initial processor list
    //
    // first slot unused, then primordial, then extra
    processors = MemoryManager.newProcessorTable(2 + NUM_EXTRA_PROCS);
    processors.set(PRIMORDIAL_PROCESSOR_ID, new GreenProcessor(PRIMORDIAL_PROCESSOR_ID));
    for (int i = 1; i <= NUM_EXTRA_PROCS; i++) {
      processors.set(PRIMORDIAL_PROCESSOR_ID + i, new GreenProcessor(PRIMORDIAL_PROCESSOR_ID + i));
    }

    // allocate lock structures
    //
    Lock.init();
  }

  /**
   * Begin multi-threaded vm operation.
   */
  /** Scheduler specific boot up */
  @Interruptible
  @Override
  protected void bootInternal() {
    if (VM.VerifyAssertions) VM._assert(1 <= numProcessors && numProcessors <= MAX_PROCESSORS);

    if (VM.TraceThreads) {
      trace("Scheduler.boot", "numProcessors =", numProcessors);
    }

    // Create a Processor object for each virtual cpu that we'll be running.
    // Note that the Processor object for the primordial processor
    // (the virtual cpu in whose context we are currently running)
    // was already created in the boot image by init(), above.
    //
    ProcessorTable origProcs = processors;
    processors = MemoryManager.newProcessorTable(1 + numProcessors);

    for (int i = PRIMORDIAL_PROCESSOR_ID; i <= numProcessors; i++) {
      GreenProcessor p = (i < origProcs.length()) ? (GreenProcessor)origProcs.get(i) : null;
      if (p == null) {
        processors.set(i, new GreenProcessor(i));
      } else {
        processors.set(i, p);
      }
    }

    // Create one one idle thread per processor.
    //
    for (int i = 0; i < numProcessors; ++i) {
      int pid = i + 1;
      GreenThread t = new IdleThread(getProcessor(pid), pid != PRIMORDIAL_PROCESSOR_ID);
      getProcessor(pid).idleQueue.enqueue(t);
    }

    // JNI support
    terminated = false;

    // the one we're running on
    getProcessor(PRIMORDIAL_PROCESSOR_ID).isInitialized = true;

    // Create virtual cpu's.
    //

    sysCall.sysCreateThreadSpecificDataKeys();
    if (!VM.withoutInterceptBlockingSystemCalls) {
      /// We now insist on this happening, by using LD_PRELOAD on platforms
      /// that support it.  Do it here for backup.
      // Enable spoofing of blocking native select calls
      System.loadLibrary("syswrap");
    }

    sysCall.sysInitializeStartupLocks(numProcessors);

    if (cpuAffinity != NO_CPU_AFFINITY) {
      sysCall.sysVirtualProcessorBind(cpuAffinity + PRIMORDIAL_PROCESSOR_ID - 1); // bind it to a physical cpu
    }

    for (int i = PRIMORDIAL_PROCESSOR_ID; ++i <= numProcessors;) {
      // create Thread for virtual cpu to execute
      //
      GreenThread target = getProcessor(i).idleQueue.dequeue();

      // Create a virtual cpu and wait for execution to enter the target's
      // code/stack.
      // This is done with GC disabled to ensure that the garbage collector
      // doesn't move code or stack before the C startoff function has a
      // chance to transfer control into the VM image.
      //
      if (VM.TraceThreads) {
        trace("Scheduler.boot", "starting processor id", i);
      }

      getProcessor(i).activeThread = target;
      getProcessor(i).activeThreadStackLimit = target.stackLimit;
      target.registerThread(); // let scheduler know that thread is active.
      if (VM.BuildForPowerPC) {
        sysCall.sysVirtualProcessorCreate(Magic.objectAsAddress(getProcessor(i)),
                                          target.contextRegisters.ip,
                                          target.contextRegisters.getInnermostFramePointer());
        if (cpuAffinity != NO_CPU_AFFINITY) {
          sysCall.sysVirtualProcessorBind(cpuAffinity + i - 1); // bind it to a physical cpu
        }
      } else if (VM.BuildForIA32) {
        sysCall.sysVirtualProcessorCreate(Magic.objectAsAddress(getProcessor(i)),
                                          target.contextRegisters.ip,
                                          target.contextRegisters.getInnermostFramePointer());
      } else if (VM.VerifyAssertions) {
        VM._assert(VM.NOT_REACHED);
      }
    }

    // wait for everybody to start up
    //
    sysCall.sysWaitForVirtualProcessorInitialization();

    allProcessorsInitialized = true;

    //    for (int i = PRIMORDIAL_PROCESSOR_ID; i <= numProcessors; ++i)
    //      processors[i].enableThreadSwitching();
    GreenProcessor.getCurrentProcessor().enableThreadSwitching();

    // Start interrupt driven timeslicer to improve threading fairness and responsiveness.
    //
    schedulingQuantum = VM.interruptQuantum * VM.schedulingMultiplier;
    if (VM.TraceThreads) {
      VM.sysWrite("  schedulingQuantum " + schedulingQuantum);
      VM.sysWrite(" = VM.interruptQuantum " + VM.interruptQuantum);
      VM.sysWrite(" * VM.schedulingMultiplier " + VM.schedulingMultiplier);
      VM.sysWriteln();
    }
    sysCall.sysVirtualProcessorEnableTimeSlicing(VM.interruptQuantum);

    // Allow virtual cpus to commence feeding off the work queues.
    //
    sysCall.sysWaitForMultithreadingStart();

    if (VM.BuildForAdaptiveSystem) {
      ObjectHolder.boot();
    }

    // Start collector threads on each Processor.
    for (int i = getFirstProcessorId(); i <= getLastProcessorId(); i++) {
      GreenThread t = CollectorThread.createActiveCollectorThread(getProcessor(i));
      t.start(getProcessor(i).readyQueue);
    }

    // Start the G.C. system.

    // Create the FinalizerThread
    FinalizerThread tt = new FinalizerThread();
    tt.makeDaemon(true);
    tt.start();

    // Store Processor in pthread
    sysCall.sysStashVmProcessorInPthread(GreenProcessor.getCurrentProcessor());
  }

  /**
   * Terminate all the pthreads that belong to the VM
   * This path is used when the VM is taken down by an external pthread via
   * the JNI call DestroyJavaVM.  All pthreads in the VM must eventually reach this
   * method from Thread.terminate() for the termination to proceed and for control
   * to return to the pthread that calls DestroyJavaVM
   * Going by the order in processor[], the pthread for each processor will join with
   * the next one, and the external pthread calling DestroyJavaVM will join with the
   * main pthread of the VM (see libjni.C)
   *
   * Note:  the NativeIdleThread's don't need to be terminated since they don't have
   * their own pthread;  they run on the external pthreads that had called CreateJavaVM
   * or AttachCurrentThread.
   */
  public static void processorExit(int rc) {
    // trace("Scheduler", ("Exiting with " + numProcessors + " pthreads."));

    // set flag to get all idle threads to exit to Thread.terminate()
    terminated = true;

    // TODO:
    // Get the collector to free system memory:  no more allocation beyond this point

    // Terminate the pthread: each processor waits for the next one
    // find the pthread to wait for
    GreenProcessor myVP = GreenProcessor.getCurrentProcessor();
    GreenProcessor VPtoWaitFor = null;
    for (int i = getFirstProcessorId(); i < getLastProcessorId(); i++) {
      if (getProcessor(i) == myVP) {
        VPtoWaitFor = getProcessor(i + 1);
        break;
      }
    }

    // each join with the expected pthread
    if (VPtoWaitFor != null) {
      sysCall.sysPthreadJoin(VPtoWaitFor.pthread_id);
    }

    // then exit myself with pthread_exit
    sysCall.sysPthreadExit();

    // does not return
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);

  }

  /**
   * Get the current executing thread on this Processor
   */
  public static GreenThread getCurrentThread() {
    return (GreenThread)GreenProcessor.getCurrentProcessor().activeThread;
  }

  /**
   *  Number of available processors
   *  @see Runtime#availableProcessors()
   */
  @Override
  protected int availableProcessorsInternal() {
    return numProcessors;
  }

  /**
   * Schedule concurrent worker threads that are not already running.
   * @see org.jikesrvm.mm.mmtk.Collection
   */
  @Override
  protected void scheduleConcurrentCollectorThreadsInternal() {
    concurrentCollectorMutex.lock("scheduling concurrent collector threads");
    while (!concurrentCollectorQueue.isEmpty()) {
      concurrentCollectorQueue.dequeue().schedule();
    }
    concurrentCollectorMutex.unlock();
  }

  /**
   * Schedule the finalizer thread if its not already running
   * @see org.jikesrvm.mm.mmtk.Collection
   */
  @Override
  protected void scheduleFinalizerInternal() {
    boolean alreadyScheduled = finalizerQueue.isEmpty();
    if (!alreadyScheduled) {
      GreenThread t = finalizerQueue.dequeue();
      GreenProcessor.getCurrentProcessor().scheduleThread(t);
    }
  }

  /**
   * Request all mutators flush their context.
   * @see org.jikesrvm.mm.mmtk.Collection
   */
  @Override
  @Unpreemptible("Becoming another thread interrupts the current thread, avoid preemption in the process")
  protected void requestMutatorFlushInternal() {
    flushMutatorContextsMutex.lock("requesting mutator flush");
    flushedMutatorCount = 0;
    for(int i = getFirstProcessorId(); i <= getLastProcessorId(); i++) {
      getProcessor(i).flushRequested = true;
    }
    GreenScheduler.getCurrentThread().yield(flushMutatorContextsQueue, flushMutatorContextsMutex);
  }

  /**
   *  Number of Processors
   */
  @Override
  protected int getNumberOfProcessorsInternal() {
    return numProcessors;
  }

  /**
   *  First Processor
   */
  protected int getFirstProcessorIdInternal() {
    return PRIMORDIAL_PROCESSOR_ID;
  }

  /**
   *  Last Processor
   */
  protected int getLastProcessorIdInternal() {
    return numProcessors;
  }

  /**
   * Get a Processor
   */
  @Override
  protected Processor getProcessorInternal(int id) {
    return processors.get(id);
  }

  /**
   * Get a Processor
   */
  @Uninterruptible
  public static GreenProcessor getProcessor(int id) {
    if (VM.runningVM) {
      return Magic.processorAsGreenProcessor(processors.get(id));
    } else {
      return bootImageGreenProcessorCast(processors.get(id));
    }
  }

  @UninterruptibleNoWarn("Only called during boot image compilation")
  private static GreenProcessor bootImageGreenProcessorCast(Processor proc) {
    return (GreenProcessor)proc;
  }

  /**
   * Returns if the VM is ready for a garbage collection.
   *
   * @return True if the RVM is ready for GC, false otherwise.
   */
  @Override
  public boolean gcEnabledInternal() {
    /* This test is based upon a review of the code and trial-and-error */
    return GreenProcessor.getCurrentProcessor().threadSwitchingEnabled() &&
      allProcessorsInitialized;
  }

  /**
   * Dump state of virtual machine.
   */
  @Override
  @Unpreemptible
  public void dumpVirtualMachineInternal() {
    GreenProcessor processor;
    VM.sysWrite("\n-- Processors --\n");
    for (int i = getFirstProcessorId(); i <= getLastProcessorId(); i++) {
      processor = getProcessor(i);
      processor.dumpProcessorState();
    }

    // system queues
    VM.sysWrite("\n-- System Queues -- \n");
    VM.sysWrite(" wakeupQueue: ");
    wakeupQueue.dump();
    VM.sysWrite(" debuggerQueue: ");
    debuggerQueue.dump();
    VM.sysWrite(" collectorQueue: ");
    collectorQueue.dump();
    VM.sysWrite(" finalizerQueue: ");
    finalizerQueue.dump();

    VM.sysWrite("\n-- Threads --\n");
    for (int i = 1; i < threads.length; ++i) {
      if (threads[i] != null) {
        threads[i].dumpWithPadding(30);
        VM.sysWrite("\n");
      }
    }
    VM.sysWrite("\n");

    VM.sysWrite("\n-- Locks in use --\n");
    Lock.dumpLocks();

    VM.sysWriteln("Dumping stack of active thread\n");
    dumpStack();

    VM.sysWriteln("Attempting to dump the stack of all other live threads");
    VM.sysWriteln("This is somewhat risky since if the thread is running we're going to be quite confused");
    GreenProcessor.getCurrentProcessor().disableThreadSwitching("disabled by scheduler to dump stack");
    for (int i = 1; i < threads.length; ++i) {
      RVMThread thr = threads[i];
      if (thr != null && thr != Scheduler.getCurrentThread() && thr.isAlive()) {
        thr.dump();
        if (thr.contextRegisters != null)
          dumpStack(thr.contextRegisters.getInnermostFramePointer());
      }
    }
    GreenProcessor.getCurrentProcessor().enableThreadSwitching();
  }

  /** Start the debugger thread */
  @Override
  @Interruptible
  protected void startDebuggerThreadInternal() {
    // Create one debugger thread.
    GreenThread t = new DebuggerThread();
    t.start(GreenScheduler.debuggerQueue);
  }

  /** Scheduler specific sysExit shutdown */
  @Override
  @Uninterruptible
  protected void sysExitInternal() {
    Wait.disableIoWait(); // we can't depend on thread switching being enabled
  }

  //---------------------------//
  // Low level output locking. //
  //---------------------------//
  @Override
  protected void lockOutputInternal() {
    if (GreenScheduler.numProcessors == 1) return;
    GreenProcessor.getCurrentProcessor().disableThreadSwitching("disabled by scheduler to lock output");
    do {
      int processorId = Magic.prepareInt(Magic.getJTOC(), Entrypoints.outputLockField.getOffset());
      if (processorId != 0) {
        // expect 0 but got another processor's ID
        continue;
      }
      // Attempt atomic swap of outputLock to out processor ID
      if(!Magic.attemptInt(Magic.getJTOC(),
          Entrypoints.outputLockField.getOffset(),
          0, GreenProcessor.getCurrentProcessorId())) {
        continue;
      }
    } while (false);
    Magic.isync(); // TODO!! is this really necessary?
  }

  @Override
  protected void unlockOutputInternal() {
    if (GreenScheduler.numProcessors == 1) return;
    Magic.sync(); // TODO!! is this really necessary?
    if (true) {
      outputLock = 0; // TODO!! this ought to work, but doesn't?
    } else {
      do {
        int processorId = Magic.prepareInt(Magic.getJTOC(), Entrypoints.outputLockField.getOffset());
        if (VM.VerifyAssertions && processorId != GreenProcessor.getCurrentProcessorId()) {
          VM.sysExit(VM.EXIT_STATUS_SYSFAIL);
        }
        if (Magic.attemptInt(Magic.getJTOC(), Entrypoints.outputLockField.getOffset(), processorId, 0)) {
          break;
        }
      } while (true);
    }
    GreenProcessor.getCurrentProcessor().enableThreadSwitching();
  }

  /**
   * Give a string of information on how a thread is set to be scheduled
   */
  @Interruptible
  static String getThreadState(GreenThread t) {
    // scan per-processor queues
    //
    for (int i = getFirstProcessorId(); i <= getLastProcessorId(); i++) {
      GreenProcessor p = getProcessor(i);
      if (p == null) continue;
      if (p.transferQueue.contains(t)) return "runnable (incoming) on processor " + i;
      if (p.readyQueue.contains(t)) return "runnable on processor " + i;
      if (p.ioQueue.contains(t)) return "waitingForIO (" + p.ioQueue.getWaitDescription(t) + ") on processor " + i;
      if (p.processWaitQueue.contains(t)) {
        return "waitingForProcess (" + p.processWaitQueue.getWaitDescription(t) + ") on processor " + i;
      }
      if (p.idleQueue.contains(t)) return "waitingForIdleWork on processor " + i;
    }

    // scan global queues
    //
    if (wakeupQueue.contains(t)) return "sleeping";
    if (debuggerQueue.contains(t)) return "waitingForDebuggerWork";
    if (collectorQueue.contains(t)) return "waitingForCollectorWork";

    String lockState = Lock.getThreadState(t);
    if (lockState != null) {
      return lockState;
    }

    // not in any queue
    //
    for (int i = getFirstProcessorId(); i <= getLastProcessorId(); i++) {
      GreenProcessor p = getProcessor(i);
      if (p == null) continue;
      if (p.activeThread == t) {
        return "running on processor " + i;
      }
    }
    return "unknown";
  }

  /**
   * Update internal state of Scheduler to indicate that
   * a thread is about to start
   */
  static void registerThread(GreenThread thread) {
    threadCreationMutex.lock("thread registration");
    numActiveThreads += 1;
    if (thread.isDaemonThread()) numDaemons += 1;
    threadCreationMutex.unlock();
  }

  /**
   * Schedule another thread
   */
  @Override
  @Unpreemptible("Becoming another thread interrupts the current thread, avoid preemption in the process")
  protected void yieldInternal() {
    GreenThread.yield();
  }

  @Override
  @Unpreemptible("Becoming another thread interrupts the current thread, avoid preemption in the process")
  protected void suspendDebuggerThreadInternal() {
    debugRequested = false;
    debuggerMutex.lock("debugger queue mutex");
    GreenScheduler.getCurrentThread().yield(debuggerQueue, debuggerMutex);
  }

  /**
   * Suspend a concurrent worker: it will resume when the garbage collector notifies.
   */
  @Override
  @Unpreemptible("Becoming another thread interrupts the current thread, avoid preemption in the process")
  protected void suspendConcurrentCollectorThreadInternal() {
    concurrentCollectorMutex.lock("suspend concurrent collector thread mutex");
    GreenScheduler.getCurrentThread().yield(concurrentCollectorQueue, concurrentCollectorMutex);
  }

  /**
   * suspend the finalizer thread: it will resume when the garbage collector
   * places objects on the finalizer queue and notifies.
   */
  @Override
  @Unpreemptible("Becoming another thread interrupts the current thread, avoid preemption in the process")
  protected void suspendFinalizerThreadInternal() {
    finalizerMutex.lock("suspend finalizer mutex");
    GreenScheduler.getCurrentThread().yield(finalizerQueue, finalizerMutex);
  }

  /**
   * Schedule thread waiting on l to give it a chance to acquire the lock
   * @param lock the lock to allow other thread chance to acquire
   */
  @Override
  @Unpreemptible("Becoming another thread interrupts the current thread, avoid preemption in the process")
  protected void yieldToOtherThreadWaitingOnLockInternal(Lock lock) {
    GreenLock l = (GreenLock)lock;
    GreenScheduler.getCurrentThread().yield(l.entering, l.mutex); // thread-switching benign
  }

  /**
   * Is it safe to start forcing garbage collects for stress testing?
   */
  @Override
  protected boolean safeToForceGCsInternal() {
    return GreenScheduler.allProcessorsInitialized &&
    GreenProcessor.getCurrentProcessor().threadSwitchingEnabled();
  }

  /**
   * Set up the initial thread and processors as part of boot image writing
   * @return the boot thread
   */
  @Override
  @Interruptible
  protected RVMThread setupBootThreadInternal() {
    int initProc = PRIMORDIAL_PROCESSOR_ID;
    byte[] stack = new byte[ArchitectureSpecific.ArchConstants.STACK_SIZE_BOOT];
    GreenThread startupThread = new Scheduler.ThreadModel(stack, "Jikes_RVM_Boot_Thread");
    startupThread.feedlet = TraceEngine.engine.makeFeedlet("Jikes RVM boot thread", "Thread used to execute the initial boot sequence of Jikes RVM");
    numDaemons++;
    getProcessor(initProc).activeThread = startupThread;
    return startupThread;
  }

  /**
   * Get the type of the thread (to avoid guarded inlining..)
   */
  @Override
  @Interruptible
  protected TypeReference getThreadTypeInternal() {
    return greenThreadType;
  }

  /**
   * Get the type of the processor (to avoid guarded inlining..)
   */
  @Override
  @Interruptible
  protected TypeReference getProcessorTypeInternal() {
    return greenProcessorType;
  }
}
