/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import org.mmtk.vm.Plan;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Multiplex execution of large number of VM_Threads on small 
 * number of o/s kernel threads.
 *
 * @author Bowen Alpern 
 * @author Derek Lieber
 * @modified Peter F. Sweeney (added HPM support)
 */
public final class VM_Processor 
//-#if RVM_WITH_JMTK_INLINE_PLAN
extends Plan 
//-#endif
implements Uninterruptible, VM_Constants {

  // definitions for VP status for implementation of jni
  public static final int IN_JAVA                 = 1;
  public static final int IN_NATIVE               = 2;
  public static final int BLOCKED_IN_NATIVE       = 3;

  /**
   * For builds where thread switching is deterministic rather than timer driven
   * Initialized in constructor
   */
  public int deterministicThreadSwitchCount;

  /**
   * For builds using counter-based sampling.  This field holds a
   * processor-specific counter so that it can be updated efficiently
   * on SMP's.
   */
  public int processor_cbs_counter;

  // fields to track attached processors - processors created for user
  // pthreads that "enter" the VM via attachJVM.
  //
  public static int            numberAttachedProcessors   = 0;
  public static VM_Processor[] attachedProcessors         = new VM_Processor[100];

  //-#if RVM_WITH_HPM
  // Keep HPM information for each Virtual Processor.
  public  VM_HardwarePerformanceMonitor hpm;
  //-#endif

  // How many times timer interrupt has occurred since last thread switch
  public int interruptQuantumCounter = 0;

  /**
   * Create data object to be associated with an o/s kernel thread 
   * (aka "virtual cpu" or "pthread").
   * @param id id that will be returned by getCurrentProcessorId() for 
   * this processor.
   */ 
  VM_Processor (int id) {
    //-#if RVM_FOR_IA32
    // presave JTOC register contents 
    // (so lintel compiler can us JTOC for scratch)
    if (VM.runningVM) this.jtoc = VM_Magic.getJTOC();
    //-#endif

    this.id = id;
    this.transferMutex     = new VM_ProcessorLock();
    this.transferQueue     = new VM_GlobalThreadQueue(this.transferMutex);
    this.readyQueue        = new VM_ThreadQueue();
    this.ioQueue           = new VM_ThreadIOQueue();
    this.processWaitQueue  = new VM_ThreadProcessWaitQueue();
    this.processWaitQueueLock = new VM_ProcessorLock();
    this.idleQueue         = new VM_ThreadQueue();
    this.lastLockIndex     = -1;
    this.isInSelect        = false;
    this.vpStatus = IN_JAVA;

    if (VM.BuildForDeterministicThreadSwitching) { // where we yield every N yieldpoints executed
      this.deterministicThreadSwitchCount = VM.deterministicThreadSwitchInterval;
    }

    MM_Interface.setupProcessor(this);
    //-#if RVM_WITH_HPM
    hpm = new VM_HardwarePerformanceMonitor(id);
    //-#endif
  }


  /**
   * Code executed to initialize a virtual processor and
   * prepare it to execute Java threads.
   */
  void initializeProcessor() {
    // bind our execution to a physical cpu
    //
    if (VM_Scheduler.cpuAffinity != VM_Scheduler.NO_CPU_AFFINITY)
      VM_SysCall.sysVirtualProcessorBind(VM_Scheduler.cpuAffinity + id - 1);
     
    // get pthread_id from AIX and store into vm_processor field
    // 
    pthread_id = VM_SysCall.sysPthreadSelf();
    
    //
    // tell VM_Scheduler.boot() that we've left the C startup
    // code/stack and are now running vm code/stack
    //
    isInitialized = true;
    
    //-#if !RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
    VM_SysCall.sysWaitForVirtualProcessorInitialization();
    //-#endif

    // enable multiprocessing
    //
    enableThreadSwitching();

    // wait for all other processors to do likewise
    //
    //-#if !RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
    VM_SysCall.sysWaitForMultithreadingStart();
    //-#endif

    //-#if !RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS
    // Store VM_Processor in pthread
    stashProcessorInPthread();
    //-#endif
  }

  
  /**
   * Is it ok to switch to a new VM_Thread in this processor?
   */ 
  public boolean threadSwitchingEnabled() throws InlinePragma {
    return threadSwitchingEnabledCount == 1;
  }

  /**
   * Enable thread switching in this processor.
   */ 
  public void enableThreadSwitching () {
    ++threadSwitchingEnabledCount;
    if (VM.VerifyAssertions) {
      VM._assert(threadSwitchingEnabledCount <= 1);
      if (MM_Interface.gcInProgress()) 
          VM._assert(threadSwitchingEnabledCount <1 || getCurrentProcessorId()==0);
    }
    if (threadSwitchingEnabled() && threadSwitchPending) { 
      // re-enable a deferred thread switch
      threadSwitchRequested = -1;
      threadSwitchPending   = false;
    }
  }

  /**
   * Disable thread switching in this processor.
   */ 
  public void disableThreadSwitching() throws InlinePragma {
    --threadSwitchingEnabledCount;
  }

  /**
   * Get processor that's being used to run the current java thread.
   */
  public static VM_Processor getCurrentProcessor() throws InlinePragma {
    return VM_ProcessorLocalState.getCurrentProcessor();
  }

  /**
   * Get id of processor that's being used to run the current java thread.
   */ 
  public static int getCurrentProcessorId() throws InlinePragma {
    return getCurrentProcessor().id;
  }

  /**
   * Become next "ready" thread.
   * Note: This method is ONLY intended for use by VM_Thread.
   * @param timerTick   timer interrupted if true
   */ 
  void dispatch (boolean timerTick) {
    if (VM.VerifyAssertions) VM._assert(lockCount == 0);// no processor locks should be held across a thread switch

    VM_Thread newThread = getRunnableThread();
    while (newThread.suspendPending) {
      newThread.suspendLock.lock();
      newThread.suspended = true;
      newThread.suspendLock.unlock();
      newThread = getRunnableThread();
    }

    previousThread = activeThread;
    activeThread   = newThread;

    if (!previousThread.isDaemon && 
        idleProcessor != null && !readyQueue.isEmpty()) {
      // if we've got too much work, transfer some of it to another 
      // processor that has nothing to do
      // don't schedule when switching away from a daemon thread...
      // kludge to avoid thrashing when VM is underloaded with real threads.
      VM_Thread t = readyQueue.dequeue();
      if (VM.TraceThreadScheduling > 0) VM_Scheduler.trace("VM_Processor", "dispatch: offload ", t.getIndex());
      scheduleThread(t);
    }

    // Accumulate CPU time on a per thread basis.
    // Used by the adaptive system and compilation measurement.
    long now = VM_Time.cycles();
    previousThread.endQuantum(now);
    newThread.startQuantum(now);
    
    //-#if RVM_WITH_HPM
    // set start time of thread
    newThread.startOfWallTime = VM_Magic.getTimeBase();
    //-#endif

    threadId       = newThread.getLockingId();
    activeThreadStackLimit = newThread.stackLimit; // Delay this to last possible moment so we can sysWrite
    VM_Magic.threadSwitch(previousThread, newThread.contextRegisters);
  }

  /**
   * Find a thread that can be run by this processor and remove it 
   * from its queue.
   */ 
  private VM_Thread getRunnableThread() throws InlinePragma {

    int loopcheck = 0;
    for (int i=transferQueue.length(); 0<i; i--) {
      transferMutex.lock();
      VM_Thread t = transferQueue.dequeue();
      transferMutex.unlock();
      if (t.isGCThread){
        if (VM.TraceThreadScheduling > 1) VM_Scheduler.trace("VM_Processor", "getRunnableThread: collector thread", t.getIndex());
        return t;
      } else if (t.beingDispatched && t != VM_Thread.getCurrentThread()) { // thread's stack in use by some OTHER dispatcher
        if (VM.TraceThreadScheduling > 1) VM_Scheduler.trace("VM_Processor", "getRunnableThread: stack in use", t.getIndex());
        transferMutex.lock();
        transferQueue.enqueue(t);
        transferMutex.unlock();
      } else {
        if (VM.TraceThreadScheduling > 1) VM_Scheduler.trace("VM_Processor", "getRunnableThread: transfer to readyQueue", t.getIndex());
        readyQueue.enqueue(t);
      }
    }

    if ((epoch % VM_Scheduler.numProcessors) + 1 == id) {
      // it's my turn to check the io queue early to avoid starvation
      // of threads in io wait.
      // We round robin this among the virtual processors to avoid serializing
      // thread switching in the call to select.
      if (ioQueue.isReady()) {
        VM_Thread t = ioQueue.dequeue();
        if (VM.TraceThreadScheduling > 1) VM_Scheduler.trace("VM_Processor", "getRunnableThread: ioQueue (early)", t.getIndex());
        if (VM.VerifyAssertions) VM._assert(t.beingDispatched == false || t == VM_Thread.getCurrentThread()); // local queue: no other dispatcher should be running on thread's stack
        return t;
      }
    }

    // FIXME - Need to think about whether we want a more
    // intelligent way to do this; for example, handling SIGCHLD,
    // and using that to implement the wakeup.  Polling is considerably
    // simpler, however.
    if ((epoch % NUM_TICKS_BETWEEN_WAIT_POLL) == id) {
      VM_Thread result = null;

      processWaitQueueLock.lock();
      if (processWaitQueue.isReady()) {
        VM_Thread t = processWaitQueue.dequeue();
        if (VM.VerifyAssertions) VM._assert(t.beingDispatched == false || t == VM_Thread.getCurrentThread()); // local queue: no other dispatcher should be running on thread's stack
        result = t;
      }
      processWaitQueueLock.unlock();
      if (result != null)
        return result;
    }

    if (!readyQueue.isEmpty()) {
      VM_Thread t = readyQueue.dequeue();
      if (VM.TraceThreadScheduling > 1) VM_Scheduler.trace("VM_Processor", "getRunnableThread: readyQueue", t.getIndex());
      if (VM.VerifyAssertions) VM._assert(t.beingDispatched == false || t == VM_Thread.getCurrentThread()); // local queue: no other dispatcher should be running on thread's stack
      return t;
    }

    if (ioQueue.isReady()) {
      VM_Thread t = ioQueue.dequeue();
      if (VM.TraceThreadScheduling > 1) VM_Scheduler.trace("VM_Processor", "getRunnableThread: ioQueue", t.getIndex());
      if (VM.VerifyAssertions) VM._assert(t.beingDispatched == false || t == VM_Thread.getCurrentThread()); // local queue: no other dispatcher should be running on thread's stack
      return t;
    }

    if (!idleQueue.isEmpty()) {
      VM_Thread t = idleQueue.dequeue();
      if (VM.TraceThreadScheduling > 1) VM_Scheduler.trace("VM_Processor", "getRunnableThread: idleQueue", t.getIndex());
      if (VM.VerifyAssertions) VM._assert(t.beingDispatched == false || t == VM_Thread.getCurrentThread()); // local queue: no other dispatcher should be running on thread's stack
      return t;
    }

    VM._assert(VM.NOT_REACHED); // should never get here (the idle thread should always be: running, on the idleQueue, or (maybe) on the transferQueue)
    return null;
  }

  //-----------------//
  //  Load Balancing //
  //-----------------//

  /**
   * Add a thread to this processor's transfer queue.
   */ 
  private void transferThread (VM_Thread t) {
    if (this != getCurrentProcessor() || t.isGCThread || (t.beingDispatched && t != VM_Thread.getCurrentThread())) {
      transferMutex.lock();
      transferQueue.enqueue(t);
      transferMutex.unlock();
    } else if (t.isIdleThread) {
      idleQueue.enqueue(t);
    } else {
      readyQueue.enqueue(t);
    }
  }

  /**
   * non-null --> a processor that has no work to do
   */
  static VM_Processor idleProcessor; 

  /**
   * Put thread onto most lightly loaded virtual processor.
   */ 
  public void scheduleThread (VM_Thread t) {
    // if thread wants to stay on specified processor, put it there
    if (t.processorAffinity != null) {
      if (VM.TraceThreadScheduling > 0) {
        VM_Scheduler.trace("VM_Processor.scheduleThread", "outgoing to specific processor:", t.getIndex());
      }
      t.processorAffinity.transferThread(t);
      return;
    }

    // if t is the last runnable thread on this processor, don't move it
    if (t == VM_Thread.getCurrentThread() && readyQueue.isEmpty() && transferQueue.isEmpty()) {
      if (VM.TraceThreadScheduling > 0) VM_Scheduler.trace("VM_Processor.scheduleThread",  "staying on same processor:", t.getIndex());
      getCurrentProcessor().transferThread(t);
      return;
    }

    // if a processor is idle, transfer t to it
    VM_Processor idle = idleProcessor;
    if (idle != null) {
      idleProcessor = null;
      if (VM.TraceThreadScheduling > 0) VM_Scheduler.trace("VM_Processor.scheduleThread", "outgoing to idle processor:", t.getIndex());
      idle.transferThread(t);
      return;
    }

    // otherwise distribute threads round robin
    if (VM.TraceThreadScheduling > 0) VM_Scheduler.trace("VM_Processor.scheduleThread",  "outgoing to round-robin processor:", t.getIndex());
    chooseNextProcessor(t).transferThread(t);

  }

  /**
   * Cycle (round robin) through the available processors.
   */
  private VM_Processor chooseNextProcessor (VM_Thread t) {
    t.chosenProcessorId = (t.chosenProcessorId % VM_Scheduler.numProcessors) + 1; 
    return VM_Scheduler.processors[t.chosenProcessorId];
  }

  //----------------------------------//
  // System call interception support //
  //----------------------------------//

  //-#if !RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS
  /**
   * Called during thread startup to stash the 
   * {@link VM_Processor} in its pthread's thread-specific storage,
   * which will allow us to access the VM_Processor from
   * arbitrary native code.  This is enabled IFF we are intercepting
   * blocking system calls.
   */
  void stashProcessorInPthread() {
    // Store address of the VM_Processor in thread-specific storage,
    // so we can access it later on from aribitrary native code.
    VM_SysCall.sysStashVmProcessorInPthread(this);
  }
  //-#endif

  //---------------------//
  // Garbage Collection  //
  //---------------------//

  public boolean unblockIfBlockedInC () {
    int newState, oldState;
    boolean result = true;
    int offset = VM_Entrypoints.vpStatusField.getOffset();
    do {
      oldState = VM_Magic.prepareInt(this, offset);
      if (oldState != BLOCKED_IN_NATIVE) {
        result = false;
        break;
      }
      newState = IN_NATIVE;
    } while (!(VM_Magic.attemptInt(this, offset, oldState, newState)));
    return result;
  }

  /**
   * sets the VP status to BLOCKED_IN_NATIVE if it is currently IN_NATIVE (ie C)
   * returns true if BLOCKED_IN_NATIVE
   */ 
  public boolean lockInCIfInC () {
    int oldState;
    int offset = VM_Entrypoints.vpStatusField.getOffset();
    do {
      oldState = VM_Magic.prepareInt(this, offset);
      if (VM.VerifyAssertions) VM._assert(oldState != BLOCKED_IN_NATIVE) ;
      if (oldState != IN_NATIVE) {
        if (VM.VerifyAssertions) VM._assert(oldState==IN_JAVA);
        return false;
      }
    } while (!(VM_Magic.attemptInt(this, offset, oldState, BLOCKED_IN_NATIVE)));
    return true;
  }

  //----------------//
  // Implementation //
  //----------------//

  /*
   * NOTE: The order of field declarations determines
   *       the layout of the fields in the processor object
   *       For IA32, it is valuable (saves code space) to
   *       declare the most frequently used fields first so that
   *       they can be accessed with 8 bit immediates.
   *       On PPC, we have plenty of bits of immediates in 
   *       load/store instructions, so it doesn't matter.
   */

  /*
   * BEGIN FREQUENTLY ACCESSED INSTANCE FIELDS
   */

  /**
   * Is it time for this processor's currently running VM_Thread 
   * to call its "threadSwitch" method?
   * A value of: 
   *    -1 means yes
   *     0 means no
   * This word is set by a timer interrupt every 10 milliseconds and
   * interrogated by every compiled method, typically in the method's prologue.
   */ 
  public int threadSwitchRequested;

  /**
   * thread currently running on this processor
   */
  public VM_Thread activeThread;

  /**
   * cached activeThread.stackLimit;
   * removes 1 load from stackoverflow sequence.
   */
  public Address activeThreadStackLimit;

  /**
   * Cache the results of activeThread.getLockingId()
   * for use in monitor operations.
   */
  public int threadId;

  //-#if RVM_FOR_IA32
  // On powerpc, these values are in dedicated registers,
  // we don't have registers to burn on IA32, so we indirect
  // through the PR register to get them instead.
  /**
   * Base pointer of JTOC (VM_Statics.slots)
   */
  Object jtoc;
  /**
   * FP for current frame
   */
  Address framePointer;        
  /**
   * "hidden parameter" for interface invocation thru the IMT
   */
  int hiddenSignatureId;   
  /**
   * "hidden parameter" from ArrayIndexOutOfBounds trap to C trap handler
   */
  int arrayIndexTrapParam; 
  //-#endif

  //-#if !RVM_WITH_JMTK_INLINE_PLAN
  public final Plan mmPlan = new Plan();
  //-#endif

  // More GC fields
  //
  public int    large_live;             // count live objects during gc
  public int    small_live;             // count live objects during gc
  public long   totalBytesAllocated;    // used for instrumentation in allocators
  public long   totalObjectsAllocated; // used for instrumentation in allocators
  public long   synchronizedObjectsAllocated; // used for instrumentation in allocators

  /*
   * END FREQUENTLY ACCESSED INSTANCE FIELDS
   */

  /**
   * Identity of this processor.
   * Note: 1. VM_Scheduler.processors[id] == this processor
   *      2. id must be non-zero because it is used in 
   *      VM_ProcessorLock ownership tests
   */ 
  public int id;

  /**
   * Has this processor's pthread initialization completed yet?
   * A value of:
   *   false means "cpu is still executing C code (on a C stack)"
   *   true  means "cpu is now executing vm code (on a vm stack)"
   */ 
  boolean isInitialized;

  /**
   * Should this processor dispatch a new VM_Thread when 
   * "threadSwitch" is called?
   * Also used to decide if it's safe to call yield() when 
   * contending for a lock.
   * A value of:
   *    1 means "yes" (switching enabled)
   * <= 0 means "no"  (switching disabled)
   */ 
  int threadSwitchingEnabledCount;

  /**
   * Was "threadSwitch" called while this processor had 
   * thread switching disabled?
   */ 
  boolean threadSwitchPending;

  /**
   * thread previously running on this processor
   */
  public VM_Thread previousThread;  

  /**
   * threads to be added to ready queue
   */
  public VM_GlobalThreadQueue transferQueue; 
  public VM_ProcessorLock     transferMutex; // guard for above

  /**
   * threads waiting for a timeslice in which to run
   */
  VM_ThreadQueue readyQueue;

  /**
   * Threads waiting for a subprocess to exit.
   */
  VM_ThreadProcessWaitQueue processWaitQueue;

  /**
   * Lock protecting a process wait queue.
   * This is needed because a thread may need to switch
   * to a different <code>VM_Processor</code> in order to perform
   * a waitpid.  (This is because of Linux's weird pthread model,
   * in which pthreads are essentially processes.)
   */
  VM_ProcessorLock processWaitQueueLock;

  /**
   * threads waiting for i/o
   */
  public VM_ThreadIOQueue ioQueue;       

  /**
   * thread to run when nothing else to do
   */
  public VM_ThreadQueue idleQueue;

  /**
   * number of processor locks currently held (for assertion checking)
   */
  public int lockCount;

  /**
   * Is the processor doing a select with a wait option
   * A value of:
   *   false means "processor is not executing a select"
   *   true  means "processor is  executing a select with a wait option"
   */ 
  boolean isInSelect;

  /**
   * This thread's successor on a VM_ProcessorQueue.
   */ 
  public VM_Processor next; 


  // count timer interrupts to round robin early checks to ioWait queue.
  // This is also used to activate checking of the processWaitQueue.
  static int epoch = 0;

  /**
   * Number of timer ticks between checks of the process wait
   * queue.  Assuming a tick frequency of 10 milliseconds, we will
   * check about twice per second.  Waiting for processes
   * to die is almost certainly not going to be on a performance-critical
   * code path, and we don't want to add unnecessary overhead to
   * the thread scheduler.
   */
  public static final int NUM_TICKS_BETWEEN_WAIT_POLL = 50;

  /**
   * Status of the processor.
   * Always one of IN_JAVA, IN_NATIVE or BLOCKED_IN_NATIVE.
   */
  public int vpStatus;

  /**
   * pthread_id (AIX's) for use by signal to wakeup
   * sleeping idle thread (signal accomplished by pthread_kill call)
   * 
   * CRA, Maria
   */
  public int      pthread_id;

  // manage thick locks 
  int     firstLockIndex;
  int     lastLockIndex;
  int     nextLockIndex;
  VM_Lock freeLock;
  int     freeLocks;
  int     locksAllocated;
  int     locksFreed;

  // to handle contention for processor locks
  //
  VM_ProcessorLock awaitingProcessorLock;
  VM_Processor     contenderLink;

  // Scratch area for use for gpr <=> fpr transfers by 
  // PPC baseline compiler
  private double scratchStorage;

  public void dumpProcessorState() throws InterruptiblePragma {
    VM.sysWrite("Processor "); 
    VM.sysWriteInt(id);
    if (this == VM_Processor.getCurrentProcessor()) VM.sysWrite(" (me)");
    VM.sysWrite(" running thread");
    if (activeThread != null) activeThread.dump();
    else VM.sysWrite(" NULL Active Thread");
    VM.sysWrite("\n");
    VM.sysWrite(" system thread id ");
    VM.sysWriteInt(pthread_id);
    VM.sysWrite("\n");
    VM.sysWrite(" transferQueue:");
    if (transferQueue!=null) transferQueue.dump();
    VM.sysWrite(" readyQueue:");
    if (readyQueue!=null) readyQueue.dump();
    VM.sysWrite(" ioQueue:");
    if (ioQueue!=null) ioQueue.dump();
    VM.sysWrite(" processWaitQueue:");
    if (processWaitQueue!=null) processWaitQueue.dump();
    VM.sysWrite(" idleQueue:");
    if (idleQueue!=null) idleQueue.dump();
    VM.sysWrite(" status: "); 
    int status = vpStatus;
    if (status ==  IN_NATIVE) VM.sysWrite("IN_NATIVE\n");
    if (status ==  IN_JAVA) VM.sysWrite("IN_JAVA\n");
    if (status ==  BLOCKED_IN_NATIVE) VM.sysWrite("BLOCKED_IN_NATIVE\n");
    VM.sysWrite(" threadSwitchRequested: ");
    VM.sysWriteInt(threadSwitchRequested); 
    VM.sysWrite("\n");
  }


  //-#if RVM_FOR_POWERPC
  /**
   * flag indicating this processor need synchronization.
   */
  public boolean needsSync = false;
  //-#endif
}
