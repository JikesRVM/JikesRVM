/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

//-#if RVM_WITH_JMTK
import com.ibm.JikesRVM.memoryManagers.JMTk.Plan;
//-#endif

//-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
import com.ibm.JikesRVM.memoryManagers.watson.VM_ContiguousHeap;
import com.ibm.JikesRVM.memoryManagers.watson.VM_SizeControl;
import com.ibm.JikesRVM.memoryManagers.watson.VM_SegregatedListHeap;
//-#endif

/**
 * Multiplex execution of large number of VM_Threads on small 
 * number of o/s kernel threads.
 *
 * @author Bowen Alpern 
 * @author Derek Lieber
 * @author Peter F. Sweeney (add HPM support)
 */
public final class VM_Processor 
//-#if RVM_WITH_JMTK_INLINE_PLAN
extends Plan 
//-#endif
implements VM_Uninterruptible, VM_Constants {

  // Processor modes
  //
  static final int RVM = 0;
  static final int NATIVE   = 1;
  static final int NATIVEDAEMON   = 2;

  public static int trace = 0;
  private static final boolean debug_native = false;

  /**
   * For builds where thread switching is deterministic rather than timer driven
   * Initialized in constructor
   */
  public int deterministicThreadSwitchCount;

  // fields to track native processors - processors created for java threads 
  // found blocked in native code
  //
  public static int            numberNativeProcessors   = 0;
  public static VM_Synchronizer nativeProcessorCountLock = new VM_Synchronizer();
  public static VM_Processor[] nativeProcessors         = new VM_Processor[100];

  // fields to track attached processors - processors created for user
  // pthreads that "enter" the VM via attachJVM.
  //
  public static int            numberAttachedProcessors   = 0;
  public static VM_Processor[] attachedProcessors         = new VM_Processor[100];


  //-#if RVM_WITH_HPM
  /*  one per virtual processor  */
  /*
   * Keep counter values for each Virtual Processor.
   */
  public HPM_counters hpm_counters;

  /* one per Jikes RVM */
  /*
   * set true in VM.boot() when we can collect hpm data!  
   */
  public  static boolean hpm_safe = false;
  /*
   * trace hpm events?
   */
  public static boolean hpm_trace = false;  
  //-#endif


  /**
   * Create data object to be associated with an o/s kernel thread 
   * (aka "virtual cpu" or "pthread").
   * @param id id that will be returned by getCurrentProcessorId() for 
   * this processor.
   */ 
  VM_Processor (int id,  int processorType ) {

    //-#if RVM_FOR_IA32
    // presave JTOC register contents 
    // (so lintel compiler can us JTOC for scratch)
    if (VM.runningVM) jtoc = VM_Magic.getJTOC();
    //-#endif

    this.id = id;
    this.transferMutex     = new VM_ProcessorLock();
    this.transferQueue     = new VM_GlobalThreadQueue(VM_EventLogger.TRANSFER_QUEUE, this.transferMutex);
    this.readyQueue        = new VM_ThreadQueue(VM_EventLogger.READY_QUEUE);
    this.ioQueue           = new VM_ThreadIOQueue(VM_EventLogger.IO_QUEUE);
    this.processWaitQueue  = new VM_ThreadProcessWaitQueue(VM_EventLogger.PROCESS_WAIT_QUEUE);
    this.processWaitQueueLock = new VM_ProcessorLock();
    this.idleQueue         = new VM_ThreadQueue(VM_EventLogger.IDLE_QUEUE);
    this.lastLockIndex     = -1;
    this.isInSelect        = false;
    this.processorMode     = processorType;

    lastVPStatusIndex = (lastVPStatusIndex + VP_STATUS_STRIDE) % VP_STATUS_SIZE;
    this.vpStatusIndex = lastVPStatusIndex;
    this.vpStatusAddress = VM_Magic.objectAsAddress(vpStatus).add(this.vpStatusIndex << 2);
    if (VM.VerifyAssertions) VM._assert(vpStatus[this.vpStatusIndex] == UNASSIGNED_VP_STATUS);
    vpStatus[this.vpStatusIndex] = IN_JAVA;

    if (VM.BuildForDeterministicThreadSwitching) { // where we set THREAD_SWITCH_BIT every N method calls
      this.deterministicThreadSwitchCount = VM.deterministicThreadSwitchInterval;
    }

    VM_Interface.setupProcessor(this);
    //-#if RVM_WITH_HPM
    hpm_counters = new HPM_counters();
    //-#endif
  }

  /**
   * Is it ok to switch to a new VM_Thread in this processor?
   */ 
  boolean threadSwitchingEnabled() throws VM_PragmaInline {
    return threadSwitchingEnabledCount == 1;
  }

  /**
   * Enable thread switching in this processor.
   */ 
  public void enableThreadSwitching () {
    ++threadSwitchingEnabledCount;
    if (VM.VerifyAssertions) 
      VM._assert(threadSwitchingEnabledCount <= 1);
    if (VM.VerifyAssertions && VM_Interface.gcInProgress()) 
      VM._assert(threadSwitchingEnabledCount <1 || getCurrentProcessorId()==0);
    if (threadSwitchingEnabled() && threadSwitchPending) { 
      // re-enable a deferred thread switch
      threadSwitchRequested = -1;
      threadSwitchPending   = false;
    }
  }

  /**
   * Disable thread switching in this processor.
   */ 
  public void disableThreadSwitching() throws VM_PragmaInline {
    --threadSwitchingEnabledCount;
  }

  /**
   * Get processor that's being used to run the current java thread.
   */
  public static VM_Processor getCurrentProcessor() throws VM_PragmaInline {
    return VM_ProcessorLocalState.getCurrentProcessor();
  }

  /**
   * Get id of processor that's being used to run the current java thread.
   */ 
  public static int getCurrentProcessorId() throws VM_PragmaInline {
    return getCurrentProcessor().id;
  }

  /**
   * Become next "ready" thread.
   * Note: This method is ONLY intended for use by VM_Thread.
   * @param timerTick   timer interrupted if true
   */ 
  void dispatch (boolean timerTick) {

    if (VM.VerifyAssertions) VM._assert(lockCount == 0);// no processor locks should be held across a thread switch
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logDispatchEvent();

    VM_Thread newThread = getRunnableThread();
    while (newThread.suspendPending) {
      newThread.suspendLock.lock();
      newThread.suspended = true;
      newThread.suspendLock.unlock();
      newThread = getRunnableThread();
    }

    previousThread = activeThread;
    activeThread   = newThread;

    //-#if RVM_FOR_IA32
    threadId       = newThread.getLockingId();
    //-#endif
    if (!previousThread.isDaemon && 
        idleProcessor != null && !readyQueue.isEmpty() 
        && getCurrentProcessor().processorMode != NATIVEDAEMON) { 
      // if we've got too much work, transfer some of it to another 
      // processor that has nothing to do
      // don't schedule when switching away from a daemon thread...
      // kludge to avoid thrashing when VM is underloaded with real threads.
      VM_Thread t = readyQueue.dequeue();
      if (trace > 0) VM_Scheduler.trace("VM_Processor", "dispatch: offload ", t.getIndex());
      scheduleThread(t);
    }

    if (VM.EnableCPUMonitoring) {
      double now = VM_Time.now();
      // primordial thread: ignore first time slice
      if (previousThread.cpuStartTime != -1) {
	previousThread.cpuTotalTime += now - previousThread.cpuStartTime;
      }
      previousThread.cpuStartTime = 0;    // this thread has stopped running
      newThread.cpuStartTime = now;  // this thread has started running
    }

    if (VM.BuildForHPM) {	      // update HPM counters
      updateHPMcounters(previousThread, newThread, timerTick);
    }

    activeThreadStackLimit = newThread.stackLimit; // Delay this to last possible moment so we can sysWrite
    VM_Magic.threadSwitch(previousThread, newThread.contextRegisters);
  }

  /*
   * Update HPM counters.
   * @param previous_thread     thread that is being switched out
   * @param current_thread      thread that is being scheduled
   * @param timerTick   	timer interrupted if true
   */
  public void updateHPMcounters(VM_Thread previous_thread, VM_Thread current_thread, boolean timerTick)
  {
    //-#if RVM_WITH_HPM
    // native calls cause stack to be grown and cause an assertion failure.
    if (hpm_safe) {
      if (previousThread.hpm_counters == null) {
	previous_thread.hpm_counters = new HPM_counters();
	VM.sysWriteln("***VM_Processor.dispatch() Previous thread id ",
		      previous_thread.getIndex(),"'s hpm_counters was null!***");
      }
      int n_counters = VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMgetCountersIP);

      if (hpm_trace) {
	int processor_id = VM_Processor.getCurrentProcessorId();
	int thread_id    = previous_thread.getIndex();
	if (! timerTick) {
	  thread_id = -thread_id;
	}
	VM.sysWrite("VP ", processor_id,", tid ",thread_id);
      }

      // dump real time and change in real time
      long real_time       = VM_Magic.getTimeBase();
      long real_time_delta = real_time - previous_thread.startOfRealTime;
      if (real_time_delta < 0) {
	VM.sysWrite("***VM_Processor.updateHPMcounters(");
	VM.sysWrite(previous_thread.getClass().getName());
	VM.sysWrite(") real time overflowed: start ",previous_thread.startOfRealTime);
	VM.sysWrite(" current ",real_time);
	VM.sysWrite(" delta ",real_time_delta);VM.sysWriteln("!***");
      } else {
	previous_thread.hpm_counters.counters[0] += real_time_delta;
                        hpm_counters.counters[0] += real_time_delta;
      }
      if (current_thread != null) 
	current_thread.startOfRealTime = real_time;
      if (hpm_trace) {
	VM.sysWrite(" RT "); VM.sysWriteLong(real_time);
	VM.sysWrite(" D "); VM.sysWriteLong(real_time_delta);
      }
      // dump counters
      for (int i=1; i<=n_counters; i++) {
	long value = VM.sysCall_L_I(VM_BootRecord.the_boot_record.sysHPMgetCounterIP,i);
	if (hpm_trace && value > 0) { 
	  VM.sysWrite(" ",i,": "); VM.sysWrite(value);
	}
                        hpm_counters.counters[i] += value;// update virtual processor HPM counters
	previous_thread.hpm_counters.counters[i] += value;// update thread HPM counters
      }
      if (hpm_trace) VM.sysWriteln();
      VM.sysCall0(VM_BootRecord.the_boot_record.sysHPMresetCountersIP);
    }
    //-#endif
  }

  /**
   * Find a thread that can be run by this processor and remove it 
   * from its queue.
   */ 
  private VM_Thread getRunnableThread() throws VM_PragmaInline {

    int loopcheck = 0;
    for (int i=transferQueue.length(); 0<i; i--) {
      transferMutex.lock();
      VM_Thread t = transferQueue.dequeue();
      transferMutex.unlock();
      if (t.isGCThread){
        if (trace > 1) VM_Scheduler.trace("VM_Processor", "getRunnableThread: collector thread", t.getIndex());
        return t;
      } else if (t.beingDispatched && t != VM_Thread.getCurrentThread()) { // thread's stack in use by some OTHER dispatcher
        if (trace > 1) VM_Scheduler.trace("VM_Processor", "getRunnableThread: stack in use", t.getIndex());
        transferMutex.lock();
        transferQueue.enqueue(t);
        transferMutex.unlock();
        if (processorMode == NATIVE) {
          // increase loop counter so this thread will be looked at 
          // again - isbeingdispatched goes off when dispatcher stops
          // running on the RVM processor, using this thread's stack.
          // RARE CASE: the stuck thread has returned from this native
          // processor, but being dispatched in the nativeidlethread has not
          // yet gone off because its stack is still being used by dispatcher
          // on the RVM	
          i++;
          if (loopcheck++ >= 1000000) break;
          if (VM.VerifyAssertions) VM._assert (t.isNativeIdleThread);
        }
      } else {
        if (trace > 1) VM_Scheduler.trace("VM_Processor", "getRunnableThread: transfer to readyQueue", t.getIndex());
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
        if (trace > 1) VM_Scheduler.trace("VM_Processor", "getRunnableThread: ioQueue (early)", t.getIndex());
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
      if (trace > 1) VM_Scheduler.trace("VM_Processor", "getRunnableThread: readyQueue", t.getIndex());
      if (VM.VerifyAssertions) VM._assert(t.beingDispatched == false || t == VM_Thread.getCurrentThread()); // local queue: no other dispatcher should be running on thread's stack
      return t;
    }

    if (ioQueue.isReady()) {
      VM_Thread t = ioQueue.dequeue();
      if (trace > 1) VM_Scheduler.trace("VM_Processor", "getRunnableThread: ioQueue", t.getIndex());
      if (VM.VerifyAssertions) VM._assert(t.beingDispatched == false || t == VM_Thread.getCurrentThread()); // local queue: no other dispatcher should be running on thread's stack
      return t;
    }

    if (!idleQueue.isEmpty()) {
      VM_Thread t = idleQueue.dequeue();
      if (trace > 1) VM_Scheduler.trace("VM_Processor", "getRunnableThread: idleQueue", t.getIndex());
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
      if (trace > 0) {
	VM_Scheduler.trace("VM_Processor.scheduleThread", "outgoing to specific processor:", t.getIndex());
      }
      t.processorAffinity.transferThread(t);
      return;
    }

    // if t is the last runnable thread on this processor, don't move it
    if (t == VM_Thread.getCurrentThread() && readyQueue.isEmpty() && transferQueue.isEmpty()) {
      if (trace > 0) VM_Scheduler.trace("VM_Processor.scheduleThread",  "staying on same processor:", t.getIndex());
      getCurrentProcessor().transferThread(t);
      return;
    }

    // if a processor is idle, transfer t to it
    VM_Processor idle = idleProcessor;
    if (idle != null) {
      idleProcessor = null;
      if (trace > 0) VM_Scheduler.trace("VM_Processor.scheduleThread", "outgoing to idle processor:", t.getIndex());
      idle.transferThread(t);
      return;
    }

    // otherwise distribute threads round robin
    if (trace > 0) VM_Scheduler.trace("VM_Processor.scheduleThread",  "outgoing to round-robin processor:", t.getIndex());
    chooseNextProcessor(t).transferThread(t);

  }

  /**
   * Cycle (round robin) through the available processors.
   */
  private VM_Processor chooseNextProcessor (VM_Thread t) {
    t.chosenProcessorId = (t.chosenProcessorId % VM_Scheduler.numProcessors) + 1; 
    return VM_Scheduler.processors[t.chosenProcessorId];
  }

  //--------------------------//
  // Native Virtual Processor //
  //--------------------------//


  // definitions for VP status for implementation of jni
  public static final int UNASSIGNED_VP_STATUS    = 0;  
  public static final int IN_JAVA                 = 1;
  public static final int IN_NATIVE               = 2;
  public static final int BLOCKED_IN_NATIVE       = 3;
  public static final int IN_SIGWAIT              = 4;
  public static final int BLOCKED_IN_SIGWAIT      = 5;

  static int generateNativeProcessorId () throws VM_PragmaInterruptible {
    int r;
    synchronized (nativeProcessorCountLock) {
      r = ++numberNativeProcessors;
    }
    return -r;
  }


  /**
   *  For JNI createJVM and attachCurrentThread: create a VM_Processor 
   * structure to represent a pthread that has been created externally 
   * It will have:
   *   -the normal idle queue with a VM_NativeIdleThread
   * It will not have:
   *   -a newly created pthread as in the normal case
   * The reference to this VM_Processor will be held in two places:
   *   -as nativeAffinity in the Java thread created by the JNI call
   *   -as an entry in the attachedProcessors array for use by GC
   *
   */
  static VM_Processor createNativeProcessorForExistingOSThread(VM_Thread withThisThread) 
    throws VM_PragmaInterruptible {

      VM_Processor newProcessor = new VM_Processor(generateNativeProcessorId(), NATIVE);
      // create idle thread for this native processor, running in attached mode
      VM_Thread t = new VM_NativeIdleThread(newProcessor, true);
      t.start(newProcessor.idleQueue);

      // Make the current thread the active thread for this native VP
      newProcessor.activeThread = withThisThread;
      newProcessor.activeThreadStackLimit = withThisThread.stackLimit;

      // Because the start up thread will not be executing to 
      // initialize itself as in the
      // normal case, we have set the isInitialized flag for the processor here
      newProcessor.isInitialized = true;

      // register this VP for GC purpose
      if (registerAttachedProcessor(newProcessor) != 0)
        return newProcessor;
      else
        return null;                // out of space to hold this new VP
    }

  static int registerAttachedProcessor(VM_Processor newProcessor) throws VM_PragmaInterruptible {

    if (numberAttachedProcessors == 100) {
      // VM.sysWrite("VM_Processor.registerAttachedProcessor: no more room\n");
      return 0;
    }

    // entry 0 is kept empty
    for (int i=1; i<attachedProcessors.length; i++) {
      if (attachedProcessors[i]==null) {
        attachedProcessors[i] = newProcessor;
        numberAttachedProcessors ++;
        // VM.sysWrite("VM_Processor.registerAttachedProcessor: ");
        // VM.sysWrite(i); VM.sysWrite("\n");
        return i;
      }
    }
    // VM.sysWrite("VM_Processor.registerAttachedProcessor: no more room\n");
    return 0;
  }

  static int unregisterAttachedProcessor(VM_Processor pr) throws VM_PragmaInterruptible {
    // entry 0 is kept empty
    for (int i=1; i<attachedProcessors.length; i++) {
      if (attachedProcessors[i]!=pr) {
        attachedProcessors[i] = null;
        numberAttachedProcessors --;
        return i;
      }
    }
    return 0;
  }


  // create a native processor for default implementation of jni
  //
  static VM_Processor createNativeProcessor () throws VM_PragmaInterruptible {

    //-#if RVM_FOR_IA32

    // NOT YET IMPLEMENTED !!!
    VM.sysWrite("VM_Processor createNativeProcessor NOT YET IMPLEMENTED for IA32\n");
    VM.sysExit(666);

    return null;

    //-#else

    // create native processor object without id - set later
    VM_Processor newProcessor = new VM_Processor(0, NATIVE);

    VM.disableGC();
    // add to native Processors array-  note: GC will see it now
    synchronized (nativeProcessorCountLock) {  
      int processId = generateNativeProcessorId();
      newProcessor.id = processId;  
      nativeProcessors[-processId] = newProcessor;
      if (debug_native) {
        VM_Scheduler.trace( "VM_Processor", 
                            "created native processor", processId);
      }
    }
    VM.enableGC();

    // create idle thread for processor - with affinity to native processor.
    // ?? will this affinity be a problem when idle thread temporarily migrates
    // ?? to a blue processor ??
    VM_Thread t = new VM_NativeIdleThread(newProcessor);
    t.start(newProcessor.transferQueue);

    // create VM_Thread for virtual cpu to execute
    //
    VM_Thread target = new VM_StartupThread(VM_Interface.newStack(STACK_SIZE_NORMAL>>2));

    // create virtual cpu and wait for execution to enter target's code/stack.
    // this is done with gc disabled to ensure that garbage 
    // collector doesn't move
    // code or stack before the C startoff function has a chance
    // to transfer control into vm image.

    VM.disableGC();

    //-#if RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
    //-#else
    // Need to set startuplock here
    VM.sysInitializeStartupLocks(1);
    //-#endif
    newProcessor.activeThread = target;
    newProcessor.activeThreadStackLimit = target.stackLimit;
    target.registerThread(); // let scheduler know that thread is active.
    VM.sysVirtualProcessorCreate(VM_Magic.getTocPointer(),
                                 VM_Magic.objectAsAddress(newProcessor),
                                 target.contextRegisters.gprs[VM.THREAD_ID_REGISTER],
                                 target.contextRegisters.getInnermostFramePointer());
    while (!newProcessor.isInitialized)
      VM.sysVirtualProcessorYield();
    VM.enableGC();

    if (debug_native) {
      VM_Scheduler.trace("VM_Processor", "started native processor", 
                         newProcessor.id);
      VM_Scheduler.trace("VM_Processor", "native processor pthread_id", 
                         newProcessor.pthread_id);
    }

    return newProcessor;

    //-#endif

  } // createNativeProcessor

  //----------------------------------//
  // System call interception support //
  //----------------------------------//

  //-#if RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS
  //-#else
  /**
   * Called during thread startup to stash the ID of the
   * {@link VM_Processor} in its pthread's thread-specific storage,
   * which will allow us to access the VM_Processor from
   * arbitrary native code.  This is enabled IFF we are intercepting
   * blocking system calls.
   */
  void stashProcessorInPthread() {
    // Store ID of the VM_Processor in thread-specific storage,
    // so we can access it later on from aribitrary native code.
/*
    VM.sysWrite("stashProcessorInPthread: my address = " +
      Integer.toHexString(VM_Magic.objectAsAddress(this).toInt()) + "\n");
*/
    VM.sysCall1(VM_BootRecord.the_boot_record.sysStashVmProcessorIdInPthreadIP,
      this.id);
  }
  //-#endif

  //---------------------//
  // Garbage Collection  //
  //---------------------//

  /**
   * sets the VP status to BLOCKED_IN_NATIVE if it is currently IN_NATIVE (ie C)
   * returns true if BLOCKED_IN_NATIVE
   */ 
  public boolean lockInCIfInC () {
    int newState, oldState;
    boolean result = true;
    do {
      oldState = VM_Magic.prepare(VM_Magic.addressAsObject(vpStatusAddress), 0);
      if (VM.VerifyAssertions) VM._assert(oldState != BLOCKED_IN_NATIVE) ;
      if (oldState != IN_NATIVE) {
        if (VM.VerifyAssertions) 
          VM._assert((oldState==IN_JAVA)||(oldState==IN_SIGWAIT)) ;
        result = false;
        break;
      }
      newState = BLOCKED_IN_NATIVE;
    } while (!(VM_Magic.attempt(VM_Magic.addressAsObject(vpStatusAddress), 
                                0, oldState, newState)));
    return result;
  }

  // sets the VP state to BLOCKED_IN_SIGWAIT if it is currently IN_SIGWAIT
  // returns true if locked in BLOCKED_IN_SIGWAIT
  //
  public boolean blockInWaitIfInWait () {
    int oldState;
    boolean result = true;
    do {
      oldState = VM_Magic.prepare(VM_Magic.addressAsObject(vpStatusAddress), 0);
      if (VM.VerifyAssertions) VM._assert(oldState != BLOCKED_IN_SIGWAIT) ;
      if (oldState != IN_SIGWAIT) {
        if (VM.VerifyAssertions) VM._assert(oldState==IN_JAVA);
        result = false;
        break;
      }
    } while (!(VM_Magic.attempt(VM_Magic.addressAsObject(vpStatusAddress), 
                                0, oldState, BLOCKED_IN_SIGWAIT)));
    return result;
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
  public VM_Thread        activeThread;    

  /**
   * cached activeThread.stackLimit;
   * removes 1 load from stackoverflow sequence.
   */
  public VM_Address activeThreadStackLimit;

  //-#if RVM_FOR_IA32
  // On powerpc, these values are in dedicated registers,
  // we don't have registers to burn on IA32, so we indirect
  // through the PR register to get them instead.
  /**
   * Base pointer of JTOC (VM_Statics.slots)
   */
  Object jtoc;
  /**
   * Thread id of VM_Thread currently executing on the processor
   */
  public int    threadId;
  /**
   * FP for current frame
   */
  VM_Address  framePointer;        
  /**
   * "hidden parameter" for interface invocation thru the IMT
   */
  int    hiddenSignatureId;   
  /**
   * "hidden parameter" from ArrayIndexOutOfBounds trap to C trap handler
   */
  int    arrayIndexTrapParam; 
  //-#endif

  //-#if RVM_WITH_JMTK
  //-#if RVM_WITH_JMTK_INLINE_PLAN
  //-#else
  final public Plan mmPlan = new Plan();
  //-#endif
  //-#endif

  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  // Chunk 1 -- see VM_Chunk.java
  // By convention, chunk1 is used for 'normal' allocation 
  public VM_Address startChunk1;
  public VM_Address currentChunk1;
  public VM_Address endChunk1;
  public VM_ContiguousHeap backingHeapChunk1;

  // Chunk 2 -- see VM_Chunk.java
  // By convention, chunk2 is used for copying objects during collection.
  public VM_Address startChunk2;
  public VM_Address currentChunk2;
  public VM_Address endChunk2;
  public VM_ContiguousHeap backingHeapChunk2;

  // For fast path of segmented list allocation -- see VM_SegmentedListFastPath.java
  // Can either be used for 'normal' allocation in markSeep collector or
  // copring objects during collection in the hybrid collector.
  public VM_SizeControl[] sizes;
  public VM_SizeControl[] GC_INDEX_ARRAY;
  public VM_SegregatedListHeap backingSLHeap;

  // Writebuffer for generational collectors
  // contains a "remembered set" of old objects with modified object references.
  //            ---+---+---+---+---+---+                    ---+---+---+---+---+---+
  //   initial:    |   |   |   |   |lnk|             later:    |obj|obj|   |   |lnk|
  //            ---+---+---+---+---+---+                    ---+---+---+---+---+---+
  //            ^top            ^max                                ^top    ^max
  //
  // See also VM_WriteBuffer.java and VM_WriteBarrier.java
  public int[]       modifiedOldObjects;          // the buffer
  public VM_Address  modifiedOldObjectsTop;       // address of most recently filled slot
  public VM_Address  modifiedOldObjectsMax;       // address of last available slot in buffer
  //-#endif

  // More GC fields
  //
  public int    large_live;		// count live objects during gc
  public int    small_live;		// count live objects during gc
  public long   totalBytesAllocated;	// used for instrumentation in allocators
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
  public VM_Thread        previousThread;  

  /**
   * threads to be added to ready queue
   */
  public VM_GlobalThreadQueue transferQueue; 
  public VM_ProcessorLock     transferMutex; // guard for above

  /**
   * threads waiting for a timeslice in which to run
   */
  VM_ThreadQueue   readyQueue;    
  /**
   * Threads waiting for a subprocess to exit.
   */
  VM_ThreadProcessWaitQueue processWaitQueue;

  // public VM_ThreadQueue   readyQueue;    
    
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
  public VM_ThreadQueue   idleQueue;     

  // The following are conditionalized by "if (VM.VerifyAssertions)"
  //
  /**
   * number of processor locks currently held (for._assertion checking)
   */
  public int              lockCount;     

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


  // Type of Virtual Processor
  //
  int    processorMode;

  // processor status fields are in a (large & unmoving!) array of status words
  public static final int VP_STATUS_SIZE = 8000;
  public static final int VP_STATUS_STRIDE = 101;

  public static int    lastVPStatusIndex = 0;
  public static int[]  vpStatus = new int[VP_STATUS_SIZE];  // must be in pinned memory !!                 

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
   * index of this processor's status word in vpStatus array
   */
  public int   vpStatusIndex;            
  /**
   * address of this processors status word in vpStatus array
   */
  public VM_Address vpStatusAddress;          

  /**
   * pthread_id (AIX's) for use by signal to wakeup
   * sleeping idle thread (signal accomplished by pthread_kill call)
   * 
   * CRA, Maria
   */
  public int  	  pthread_id;

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

  // Scratch area for use by VM_Magic.getTime()
  //
  private double   scratchSeconds;
  private double   scratchNanoseconds;

  public void dumpProcessorState() throws VM_PragmaInterruptible {
    VM_Scheduler.writeString("Processor "); 
    VM_Scheduler.writeDecimal(id);
    if (this == VM_Processor.getCurrentProcessor()) VM_Scheduler.writeString(" (me)");
    VM_Scheduler.writeString(" running thread");
    if (activeThread != null) activeThread.dump();
    else VM_Scheduler.writeString(" NULL Active Thread");
    VM_Scheduler.writeString("\n");
    VM_Scheduler.writeString(" system thread id ");
    VM_Scheduler.writeDecimal(pthread_id);
    VM_Scheduler.writeString("\n");
    VM_Scheduler.writeString(" transferQueue:");
    if (transferQueue!=null) transferQueue.dump();
    VM_Scheduler.writeString(" readyQueue:");
    if (readyQueue!=null) readyQueue.dump();
    VM_Scheduler.writeString(" ioQueue:");
    if (ioQueue!=null) ioQueue.dump();
    VM_Scheduler.writeString(" processWaitQueue:");
    if (processWaitQueue!=null) processWaitQueue.dump();
    VM_Scheduler.writeString(" idleQueue:");
    if (idleQueue!=null) idleQueue.dump();
    if ( processorMode == RVM) VM_Scheduler.writeString(" mode: RVM\n");
    else if ( processorMode == NATIVE) VM_Scheduler.writeString(" mode: NATIVE\n");
    else if ( processorMode == NATIVEDAEMON) VM_Scheduler.writeString(" mode: NATIVEDAEMON\n");
    VM_Scheduler.writeString(" status: "); 
    int status = vpStatus[vpStatusIndex];
    if (status ==  IN_NATIVE) VM_Scheduler.writeString("IN_NATIVE\n");
    if (status ==  IN_JAVA) VM_Scheduler.writeString("IN_JAVA\n");
    if (status ==  BLOCKED_IN_NATIVE) VM_Scheduler.writeString("BLOCKED_IN_NATIVE\n");
    if (status ==  IN_SIGWAIT) VM_Scheduler.writeString("IN_SIGWAIT\n");
    if (status ==  BLOCKED_IN_SIGWAIT)  VM_Scheduler.writeString("BLOCKED_IN_SIGWAIT\n");
    VM_Scheduler.writeString(" threadSwitchRequested: ");
    VM_Scheduler.writeDecimal(threadSwitchRequested); 
    VM_Scheduler.writeString("\n");
    //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
    VM_Scheduler.writeString("Chunk1: "); 
    VM_Scheduler.writeHex(startChunk1.toInt()); VM_Scheduler.writeString(" < ");
    VM_Scheduler.writeHex(currentChunk1.toInt()); VM_Scheduler.writeString(" < ");
    VM_Scheduler.writeHex(endChunk1.toInt()); VM_Scheduler.writeString("\n");
    VM_Scheduler.writeString("Chunk2: "); 
    VM_Scheduler.writeHex(startChunk2.toInt()); VM_Scheduler.writeString(" < ");
    VM_Scheduler.writeHex(currentChunk2.toInt()); VM_Scheduler.writeString(" < ");
    VM_Scheduler.writeHex(endChunk2.toInt()); VM_Scheduler.writeString("\n");
    //-#endif
  }


  //-#if RVM_FOR_POWERPC
  /* flag indicating this processor need synchronization.
   */
  public boolean needsSync = false;
  //-#endif
}
