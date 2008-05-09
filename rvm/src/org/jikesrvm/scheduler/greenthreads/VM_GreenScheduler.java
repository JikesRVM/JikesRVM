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
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.memorymanagers.mminterface.MM_Constants;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.memorymanagers.mminterface.VM_CollectorThread;
import org.jikesrvm.memorymanagers.mminterface.VM_ConcurrentCollectorThread;
import org.jikesrvm.osr.OSR_ObjectHolder;
import org.jikesrvm.runtime.VM_BootRecord;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.runtime.VM_Magic;
import static org.jikesrvm.runtime.VM_SysCall.sysCall;
import org.jikesrvm.scheduler.VM_DebuggerThread;
import org.jikesrvm.scheduler.VM_FinalizerThread;
import org.jikesrvm.scheduler.VM_Lock;
import org.jikesrvm.scheduler.VM_Processor;
import org.jikesrvm.scheduler.VM_ProcessorLock;
import org.jikesrvm.scheduler.VM_ProcessorTable;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.VM_Thread;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;
import org.vmmagic.unboxed.Address;

/**
 * Global variables used to implement virtual machine thread scheduler.
 *    - virtual cpus
 *    - threads
 *    - queues
 *    - locks
 */
@Uninterruptible
public final class VM_GreenScheduler extends VM_Scheduler {

  /** Index of initial processor in which "VM.boot()" runs. */
  public static final int PRIMORDIAL_PROCESSOR_ID = 1;

  /** Index of thread in which "VM.boot()" runs */

  // A processors id is its index in the processors array & a threads
  // id is its index in the threads array.  id's start at 1, so that
  // id 0 can be used in locking to represent an unheld lock

  /**
   * Maximum number of VM_Processor's that we can support.
   */
  public static final int MAX_PROCESSORS = 12;   // allow processors = 1 to 12

  /** Type of processor */
  private static final VM_TypeReference greenProcessorType =
    VM_TypeReference.findOrCreate(VM_GreenProcessor.class);

  /** Type of thread */
  private static final VM_TypeReference greenThreadType =
    VM_TypeReference.findOrCreate(VM_GreenThread.class);

  /** Maximum number of VM_Thread's that we can support. */
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
  public static VM_ProcessorTable processors;
  /** Have all processors completed initialization? */
  public static boolean allProcessorsInitialized;

  // Thread execution.
  //
  /** threads waiting to wake up from a sleep() */
  static final VM_ThreadProxyWakeupQueue wakeupQueue = new VM_ThreadProxyWakeupQueue();
  /** mutex controlling access to wake up queue */
  static final VM_ProcessorLock wakeupMutex = new VM_ProcessorLock();

  /** thread waiting to service debugging requests */
  public static final VM_GreenThreadQueue debuggerQueue = new VM_GreenThreadQueue();
  public static final VM_ProcessorLock debuggerMutex = new VM_ProcessorLock();

  /** collector threads waiting to be resumed */
  public static final VM_GreenThreadQueue collectorQueue = new VM_GreenThreadQueue();
  public static final VM_ProcessorLock collectorMutex = new VM_ProcessorLock();

  /** Concurrent worker threads wait here when idle */
  public static final VM_GreenThreadQueue concurrentCollectorQueue = new VM_GreenThreadQueue();
  public static final VM_ProcessorLock concurrentCollectorMutex = new VM_ProcessorLock();

  /** Finalizer thread waits here when idle */
  public static final VM_GreenThreadQueue finalizerQueue = new VM_GreenThreadQueue();
  public static final VM_ProcessorLock finalizerMutex = new VM_ProcessorLock();

  /** Collectors wait here while waiting for flushes */
  public static final VM_GreenThreadQueue flushMutatorContextsQueue = new VM_GreenThreadQueue();
  public static final VM_ProcessorLock flushMutatorContextsMutex = new VM_ProcessorLock();
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
    VM_BootRecord.the_boot_record.dumpStackAndDieOffset = VM_Entrypoints.dumpStackAndDieMethod.getOffset();

    // allocate initial processor list
    //
    // first slot unused, then primordial, then extra
    processors = MM_Interface.newProcessorTable(2 + NUM_EXTRA_PROCS);
    processors.set(PRIMORDIAL_PROCESSOR_ID, new VM_GreenProcessor(PRIMORDIAL_PROCESSOR_ID));
    for (int i = 1; i <= NUM_EXTRA_PROCS; i++) {
      processors.set(PRIMORDIAL_PROCESSOR_ID + i, new VM_GreenProcessor(PRIMORDIAL_PROCESSOR_ID + i));
    }

    // allocate lock structures
    //
    VM_Lock.init();
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
      trace("VM_Scheduler.boot", "numProcessors =", numProcessors);
    }

    // Create a VM_Processor object for each virtual cpu that we'll be running.
    // Note that the VM_Processor object for the primordial processor
    // (the virtual cpu in whose context we are currently running)
    // was already created in the boot image by init(), above.
    //
    VM_ProcessorTable origProcs = processors;
    processors = MM_Interface.newProcessorTable(1 + numProcessors);

    for (int i = PRIMORDIAL_PROCESSOR_ID; i <= numProcessors; i++) {
      VM_GreenProcessor p = (i < origProcs.length()) ? (VM_GreenProcessor)origProcs.get(i) : null;
      if (p == null) {
        processors.set(i, new VM_GreenProcessor(i));
      } else {
        processors.set(i, p);
        if (VM.BuildForIA32) {
          // TODO: the JTOC doesn't move so this field is redundant
          p.jtoc = VM_Magic.getJTOC();  // only needed for EXTRA_PROCS
        }
      }
    }

    // Create one one idle thread per processor.
    //
    for (int i = 0; i < numProcessors; ++i) {
      int pid = i + 1;
      VM_GreenThread t = new VM_IdleThread(getProcessor(pid), pid != PRIMORDIAL_PROCESSOR_ID);
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
      // create VM_Thread for virtual cpu to execute
      //
      VM_GreenThread target = getProcessor(i).idleQueue.dequeue();

      // Create a virtual cpu and wait for execution to enter the target's
      // code/stack.
      // This is done with GC disabled to ensure that the garbage collector
      // doesn't move code or stack before the C startoff function has a
      // chance to transfer control into the VM image.
      //
      if (VM.TraceThreads) {
        trace("VM_Scheduler.boot", "starting processor id", i);
      }

      getProcessor(i).activeThread = target;
      getProcessor(i).activeThreadStackLimit = target.stackLimit;
      target.registerThread(); // let scheduler know that thread is active.
      if (VM.BuildForPowerPC) {
        // NOTE: It is critical that we acquire the tocPointer explicitly
        //       before we start the SysCall sequence. This prevents
        //       the opt compiler from generating code that passes the AIX
        //       sys toc instead of the RVM jtoc. --dave
        Address toc = VM_Magic.getTocPointer();
        sysCall.sysVirtualProcessorCreate(toc,
                                          VM_Magic.objectAsAddress(getProcessor(i)),
                                          target.contextRegisters.ip,
                                          target.contextRegisters.getInnermostFramePointer());
        if (cpuAffinity != NO_CPU_AFFINITY) {
          sysCall.sysVirtualProcessorBind(cpuAffinity + i - 1); // bind it to a physical cpu
        }
      } else if (VM.BuildForIA32) {
        sysCall.sysVirtualProcessorCreate(VM_Magic.getTocPointer(),
                                          VM_Magic.objectAsAddress(getProcessor(i)),
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
    VM_GreenProcessor.getCurrentProcessor().enableThreadSwitching();

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
      OSR_ObjectHolder.boot();
    }

    // Start collector threads on each VM_Processor.
    for (int i = getFirstProcessorId(); i <= getLastProcessorId(); i++) {
      VM_GreenThread t = VM_CollectorThread.createActiveCollectorThread(getProcessor(i));
      t.start(getProcessor(i).readyQueue);
    }

    if (MM_Constants.NEEDS_CONCURRENT_WORKERS) {
      // Start concurrent collector threads on each VM_Processor.
      for (int i = getFirstProcessorId(); i <= getLastProcessorId(); i++) {
        VM_GreenThread t = VM_ConcurrentCollectorThread.createConcurrentCollectorThread(getProcessor(i));
        t.start(getProcessor(i).readyQueue);
      }
    }

    // Start the G.C. system.

    // Create the VM_FinalizerThread
    VM_FinalizerThread tt = new VM_FinalizerThread();
    tt.makeDaemon(true);
    tt.start();

    // Store VM_Processor in pthread
    sysCall.sysStashVmProcessorInPthread(VM_GreenProcessor.getCurrentProcessor());
  }

  /**
   * Terminate all the pthreads that belong to the VM
   * This path is used when the VM is taken down by an external pthread via
   * the JNI call DestroyJavaVM.  All pthreads in the VM must eventually reach this
   * method from VM_Thread.terminate() for the termination to proceed and for control
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
    // trace("VM_Scheduler", ("Exiting with " + numProcessors + " pthreads."));

    // set flag to get all idle threads to exit to VM_Thread.terminate()
    terminated = true;

    // TODO:
    // Get the collector to free system memory:  no more allocation beyond this point

    // Terminate the pthread: each processor waits for the next one
    // find the pthread to wait for
    VM_GreenProcessor myVP = VM_GreenProcessor.getCurrentProcessor();
    VM_GreenProcessor VPtoWaitFor = null;
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
   * Get the current executing thread on this VM_Processor
   */
  public static VM_GreenThread getCurrentThread() {
    return (VM_GreenThread)VM_GreenProcessor.getCurrentProcessor().activeThread;
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
      VM_GreenThread t = finalizerQueue.dequeue();
      VM_GreenProcessor.getCurrentProcessor().scheduleThread(t);
    }
  }

  /**
   * Request all mutators flush their context.
   * @see org.jikesrvm.mm.mmtk.Collection
   */
  @Override
  protected void requestMutatorFlushInternal() {
    flushMutatorContextsMutex.lock("requesting mutator flush");
    flushedMutatorCount = 0;
    for(int i = getFirstProcessorId(); i <= getLastProcessorId(); i++) {
      getProcessor(i).flushRequested = true;
    }
    VM_GreenScheduler.getCurrentThread().yield(flushMutatorContextsQueue, flushMutatorContextsMutex);
  }

  /**
   *  Number of VM_Processors
   */
  @Override
  protected int getNumberOfProcessorsInternal() {
    return numProcessors;
  }

  /**
   *  First VM_Processor
   */
  protected int getFirstProcessorIdInternal() {
    return PRIMORDIAL_PROCESSOR_ID;
  }

  /**
   *  Last VM_Processor
   */
  protected int getLastProcessorIdInternal() {
    return numProcessors;
  }

  /**
   * Get a VM_Processor
   */
  @Override
  protected VM_Processor getProcessorInternal(int id) {
    return processors.get(id);
  }

  /**
   * Get a VM_Processor
   */
  @UninterruptibleNoWarn
  public static VM_GreenProcessor getProcessor(int id) {
    return (VM_GreenProcessor)processors.get(id);
  }

  /**
   * Returns if the VM is ready for a garbage collection.
   *
   * @return True if the RVM is ready for GC, false otherwise.
   */
  @Override
  public boolean gcEnabledInternal() {
    /* This test is based upon a review of the code and trial-and-error */
    return VM_GreenProcessor.getCurrentProcessor().threadSwitchingEnabled() &&
      allProcessorsInitialized;
  }

  /**
   * Dump state of virtual machine.
   */
  @Override
  public void dumpVirtualMachineInternal() {
    VM_GreenProcessor processor;
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

    VM.sysWrite("\n-- Locks available --\n");
    for (int i = PRIMORDIAL_PROCESSOR_ID; i <= numProcessors; ++i) {
      processor = getProcessor(i);
      processor.dumpLocks();
    }
    VM.sysWrite("\n");

    VM.sysWrite("\n-- Locks in use --\n");
    VM_Lock.dumpLocks();

    VM.sysWriteln("Dumping stack of active thread\n");
    dumpStack();

    VM.sysWriteln("Attempting to dump the stack of all other live threads");
    VM.sysWriteln("This is somewhat risky since if the thread is running we're going to be quite confused");
    VM_GreenProcessor.getCurrentProcessor().disableThreadSwitching("disabled by scheduler to dump stack");
    for (int i = 1; i < threads.length; ++i) {
      VM_Thread thr = threads[i];
      if (thr != null && thr != VM_Scheduler.getCurrentThread() && thr.isAlive()) {
        thr.dump();
        if (thr.contextRegisters != null)
          dumpStack(thr.contextRegisters.getInnermostFramePointer());
      }
    }
    VM_GreenProcessor.getCurrentProcessor().enableThreadSwitching();
  }

  /** Start the debugger thread */
  @Override
  @Interruptible
  protected void startDebuggerThreadInternal() {
    // Create one debugger thread.
    VM_GreenThread t = new VM_DebuggerThread();
    t.start(VM_GreenScheduler.debuggerQueue);
  }

  /** Scheduler specific sysExit shutdown */
  @Override
  @Interruptible
  protected void sysExitInternal() {
    VM_Wait.disableIoWait(); // we can't depend on thread switching being enabled
  }

  //---------------------------//
  // Low level output locking. //
  //---------------------------//
  @Override
  protected void lockOutputInternal() {
    if (VM_GreenScheduler.numProcessors == 1) return;
    VM_GreenProcessor.getCurrentProcessor().disableThreadSwitching("disabled by scheduler to lock output");
    do {
      int processorId = VM_Magic.prepareInt(VM_Magic.getJTOC(), VM_Entrypoints.outputLockField.getOffset());
      if (processorId != 0) {
        // expect 0 but got another processor's ID
        continue;
      }
      // Attempt atomic swap of outputLock to out processor ID
      if(!VM_Magic.attemptInt(VM_Magic.getJTOC(),
          VM_Entrypoints.outputLockField.getOffset(),
          0, VM_GreenProcessor.getCurrentProcessorId())) {
        continue;
      }
    } while (false);
    VM_Magic.isync(); // TODO!! is this really necessary?
  }

  @Override
  protected void unlockOutputInternal() {
    if (VM_GreenScheduler.numProcessors == 1) return;
    VM_Magic.sync(); // TODO!! is this really necessary?
    if (true) {
      outputLock = 0; // TODO!! this ought to work, but doesn't?
    } else {
      do {
        int processorId = VM_Magic.prepareInt(VM_Magic.getJTOC(), VM_Entrypoints.outputLockField.getOffset());
        if (VM.VerifyAssertions && processorId != VM_GreenProcessor.getCurrentProcessorId()) {
          VM.sysExit(VM.EXIT_STATUS_SYSFAIL);
        }
        if (VM_Magic.attemptInt(VM_Magic.getJTOC(), VM_Entrypoints.outputLockField.getOffset(), processorId, 0)) {
          break;
        }
      } while (true);
    }
    VM_GreenProcessor.getCurrentProcessor().enableThreadSwitching();
  }

  /**
   * Give a string of information on how a thread is set to be scheduled
   */
  @Interruptible
  static String getThreadState(VM_GreenThread t) {
    // scan per-processor queues
    //
    for (int i = getFirstProcessorId(); i <= getLastProcessorId(); i++) {
      VM_GreenProcessor p = getProcessor(i);
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

    String lockState = VM_Lock.getThreadState(t);
    if (lockState != null) {
      return lockState;
    }

    // not in any queue
    //
    for (int i = getFirstProcessorId(); i <= getLastProcessorId(); i++) {
      VM_GreenProcessor p = getProcessor(i);
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
  static void registerThread(VM_GreenThread thread) {
    threadCreationMutex.lock("thread registration");
    numActiveThreads += 1;
    if (thread.isDaemonThread()) numDaemons += 1;
    threadCreationMutex.unlock();
  }

  /**
   * Schedule another thread
   */
  @Override
  protected void yieldInternal() {
    VM_GreenThread.yield();
  }

  @Override
  protected void suspendDebuggerThreadInternal() {
    debugRequested = false;
    debuggerMutex.lock("debugger queue mutex");
    VM_GreenScheduler.getCurrentThread().yield(debuggerQueue, debuggerMutex);
  }

  /**
   * Suspend a concurrent worker: it will resume when the garbage collector notifies.
   */
  @Override
  protected void suspendConcurrentCollectorThreadInternal() {
    concurrentCollectorMutex.lock("suspend concurrent collector thread mutex");
    VM_GreenScheduler.getCurrentThread().yield(concurrentCollectorQueue, concurrentCollectorMutex);
  }

  /**
   * suspend the finalizer thread: it will resume when the garbage collector
   * places objects on the finalizer queue and notifies.
   */
  @Override
  protected void suspendFinalizerThreadInternal() {
    finalizerMutex.lock("suspend finalizer mutex");
    VM_GreenScheduler.getCurrentThread().yield(finalizerQueue, finalizerMutex);
  }

  /**
   * Schedule thread waiting on l to give it a chance to acquire the lock
   * @param lock the lock to allow other thread chance to acquire
   */
  @Override
  protected void yieldToOtherThreadWaitingOnLockInternal(VM_Lock lock) {
    VM_GreenLock l = (VM_GreenLock)lock;
    VM_GreenScheduler.getCurrentThread().yield(l.entering, l.mutex); // thread-switching benign
  }

  /**
   * Is it safe to start forcing garbage collects for stress testing?
   */
  @Override
  protected boolean safeToForceGCsInternal() {
    return VM_GreenScheduler.allProcessorsInitialized &&
    VM_GreenProcessor.getCurrentProcessor().threadSwitchingEnabled();
  }

  /**
   * Set up the initial thread and processors as part of boot image writing
   * @return the boot thread
   */
  @Override
  @Interruptible
  protected VM_Thread setupBootThreadInternal() {
    int initProc = PRIMORDIAL_PROCESSOR_ID;
    byte[] stack = new byte[ArchitectureSpecific.VM_ArchConstants.STACK_SIZE_BOOT];
    VM_GreenThread startupThread = new VM_Scheduler.ThreadModel(stack, "Jikes_RVM_Boot_Thread");
    numDaemons++;
    getProcessor(initProc).activeThread = startupThread;
    return startupThread;
  }

  /**
   * Get the type of the thread (to avoid guarded inlining..)
   */
  @Override
  @Interruptible
  protected VM_TypeReference getThreadTypeInternal() {
    return greenThreadType;
  }

  /**
   * Get the type of the processor (to avoid guarded inlining..)
   */
  @Override
  @Interruptible
  protected VM_TypeReference getProcessorTypeInternal() {
    return greenProcessorType;
  }
}
