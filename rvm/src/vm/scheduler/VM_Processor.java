/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;
import com.ibm.JikesRVM.memoryManagers.JMTk.Plan;

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
implements VM_Uninterruptible, VM_Constants {

  // Processor modes
  //
  static final int RVM = 0;
  static final int NATIVE   = 1;
  static final int NATIVEDAEMON   = 2;

  private static final boolean debug_native = false;

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
  // Keep HPM information for each Virtual Processor.
  public  VM_HardwarePerformanceMonitor hpm;
  //-#endif

  // How many times timer interrupt has occurred since last thread switch
  public  int interruptQuantumCounter = 0;


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
    this.processorMode     = processorType;

    this.vpStatusAddress = VM_Magic.objectAsAddress(this).add(VM_Entrypoints.vpStatusField.getOffset());
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
    if (VM.VerifyAssertions && MM_Interface.gcInProgress()) 
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
        idleProcessor != null && !readyQueue.isEmpty() 
        && getCurrentProcessor().processorMode != NATIVEDAEMON) { 
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

    //-#if RVM_FOR_IA32
    threadId       = newThread.getLockingId();
    //-#endif
    activeThreadStackLimit = newThread.stackLimit; // Delay this to last possible moment so we can sysWrite
    VM_Magic.threadSwitch(previousThread, newThread.contextRegisters);
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
        if (VM.TraceThreadScheduling > 1) VM_Scheduler.trace("VM_Processor", "getRunnableThread: collector thread", t.getIndex());
        return t;
      } else if (t.beingDispatched && t != VM_Thread.getCurrentThread()) { // thread's stack in use by some OTHER dispatcher
        if (VM.TraceThreadScheduling > 1) VM_Scheduler.trace("VM_Processor", "getRunnableThread: stack in use", t.getIndex());
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
          if (VM.VerifyAssertions) VM._assert (t.isNativeIdleThread,"VM_Processor.getRunnableThread() assert t.isNativeIdleThread");
        }
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

  //--------------------------//
  // Native Virtual Processor //
  //--------------------------//


  // definitions for VP status for implementation of jni
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
    VM.sysExit(VM.exitStatusUnsupportedInternalOp);

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
    VM_Thread target = new VM_StartupThread(MM_Interface.newStack(STACK_SIZE_NORMAL>>VM_SizeConstants.LOG_BYTES_IN_ADDRESS));

    // create virtual cpu and wait for execution to enter target's code/stack.
    // this is done with gc disabled to ensure that garbage 
    // collector doesn't move
    // code or stack before the C startoff function has a chance
    // to transfer control into vm image.

    VM.disableGC();

    //-#if !RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
    // Need to set startuplock here
    VM_SysCall.sysInitializeStartupLocks(1);
    //-#endif
    newProcessor.activeThread = target;
    newProcessor.activeThreadStackLimit = target.stackLimit;
    target.registerThread(); // let scheduler know that thread is active.
    // NOTE: it is critical that we acquire the tocPointer explicitly
    //       before we start the SysCall sequence. This prevents 
    //       the opt compiler from generating code that passes the AIX 
    //       sys toc instead of the RVM jtoc. --dave
    VM_Address toc = VM_Magic.getTocPointer();
    VM_SysCall.sysVirtualProcessorCreate(toc,
					 VM_Magic.objectAsAddress(newProcessor),
					 target.contextRegisters.gprs.get(VM.THREAD_ID_REGISTER).toAddress(),
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

  //-#if !RVM_WITHOUT_INTERCEPT_BLOCKING_SYSTEM_CALLS
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
    VM_SysCall.sysStashVmProcessorIdInPthread(this.id);
  }
  //-#endif

  //---------------------//
  // Garbage Collection  //
  //---------------------//

  public boolean unblockIfBlockedInC () {
    int newState, oldState;
    boolean result = true;
    do {
      oldState = VM_Magic.prepareInt(VM_Magic.addressAsObject(vpStatusAddress), 0);
      if (oldState != BLOCKED_IN_NATIVE) {
        result = false;
        break;
      }
      newState = IN_NATIVE;
    } while (!(VM_Magic.attemptInt(VM_Magic.addressAsObject(vpStatusAddress), 
                                0, oldState, newState)));
    return result;
  }

  /**
   * sets the VP status to BLOCKED_IN_NATIVE if it is currently IN_NATIVE (ie C)
   * returns true if BLOCKED_IN_NATIVE
   */ 
  public boolean lockInCIfInC () {
    int oldState;
    do {
      oldState = VM_Magic.prepareInt(VM_Magic.addressAsObject(vpStatusAddress), 0);
      if (VM.VerifyAssertions) VM._assert(oldState != BLOCKED_IN_NATIVE) ;
      if (oldState != IN_NATIVE) {
        if (VM.VerifyAssertions) 
          VM._assert((oldState==IN_JAVA)||(oldState==IN_SIGWAIT)) ;
        return false;
      }
    } while (!(VM_Magic.attemptInt(VM_Magic.addressAsObject(vpStatusAddress), 
                                0, oldState, BLOCKED_IN_NATIVE)));
    return true;
  }

  // sets the VP state to BLOCKED_IN_SIGWAIT if it is currently IN_SIGWAIT
  // returns true if locked in BLOCKED_IN_SIGWAIT
  //
  public boolean blockInWaitIfInWait () {
    int oldState;
    boolean result = true;
    do {
      oldState = VM_Magic.prepareInt(VM_Magic.addressAsObject(vpStatusAddress), 0);
      if (VM.VerifyAssertions) VM._assert(oldState != BLOCKED_IN_SIGWAIT) ;
      if (oldState != IN_SIGWAIT) {
        if (VM.VerifyAssertions) VM._assert(oldState==IN_JAVA);
        result = false;
        break;
      }
    } while (!(VM_Magic.attemptInt(VM_Magic.addressAsObject(vpStatusAddress), 
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

  //-#if !RVM_WITH_JMTK_INLINE_PLAN
  public final Plan mmPlan = new Plan();
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
   * Always one of IN_JAVA, IN_NATIVE, BLOCKED_IN_NATIVE,
   * IN_SIGWAIT or BLOCKED_IN_SIGWAIT.
   */
  public int vpStatus;

  /**
   * address of this.vpStatus
   * @deprecated  Working on getting rid of this, but it is going to take 
   *              several stages. --dave
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

  // Scratch area for use for gpr <=> fpr transfers by 
  // PPC baseline compiler
  private double scratchStorage;

  public void dumpProcessorState() throws VM_PragmaInterruptible {
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
    if ( processorMode == RVM) VM.sysWrite(" mode: RVM\n");
    else if ( processorMode == NATIVE) VM.sysWrite(" mode: NATIVE\n");
    else if ( processorMode == NATIVEDAEMON) VM.sysWrite(" mode: NATIVEDAEMON\n");
    VM.sysWrite(" status: "); 
    int status = vpStatus;
    if (status ==  IN_NATIVE) VM.sysWrite("IN_NATIVE\n");
    if (status ==  IN_JAVA) VM.sysWrite("IN_JAVA\n");
    if (status ==  BLOCKED_IN_NATIVE) VM.sysWrite("BLOCKED_IN_NATIVE\n");
    if (status ==  IN_SIGWAIT) VM.sysWrite("IN_SIGWAIT\n");
    if (status ==  BLOCKED_IN_SIGWAIT)  VM.sysWrite("BLOCKED_IN_SIGWAIT\n");
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
