/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * Multiplex execution of large number of VM_Threads on small 
 * number of o/s kernel threads.
 * @author Bowen Alpern 
 * @author Derek Lieber
 */
final class VM_Processor implements VM_Uninterruptible,  VM_Constants, VM_GCConstants {

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

  // fields to track native processors - processors created for java threads 
  // found blocked in native code
  //
  static int            numberNativeProcessors   = 0;
  static Object         nativeProcessorCountLock = new Object();
  static VM_Processor[] nativeProcessors         = new VM_Processor[100];

  // fields to track attached processors - processors created for user
  // pthreads that "enter" the VM via attachJVM.
  //
  static int            numberAttachedProcessors   = 0;
  static VM_Processor[] attachedProcessors         = new VM_Processor[100];

  private static final boolean trace = false; 

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
    this.idleQueue         = new VM_ThreadQueue(VM_EventLogger.IDLE_QUEUE);
    this.lastLockIndex     = -1;
    this.isInSelect        = false;
    this.processorMode     = processorType;

    //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS (alternate implementation of jni)
    //-#else                                    (default implementation of jni)
    lastVPStatusIndex = (lastVPStatusIndex + VP_STATUS_STRIDE) % VP_STATUS_SIZE;
    this.vpStatusIndex = lastVPStatusIndex;
    this.vpStatusAddress = VM_Magic.objectAsAddress(vpStatus) + (this.vpStatusIndex << 2);
    if (VM.VerifyAssertions) VM.assert(vpStatus[this.vpStatusIndex] == UNASSIGNED_VP_STATUS);
    vpStatus[this.vpStatusIndex] = IN_JAVA;
    //-#endif

    if (VM.BuildForDeterministicThreadSwitching) { // where we set THREAD_SWITCH_BIT every N method calls
      this.deterministicThreadSwitchCount = VM.deterministicThreadSwitchInterval;
    }

    VM_Collector.setupProcessor(this);
  }

  /**
   * Is it ok to switch to a new VM_Thread in this processor?
   */ 
  boolean threadSwitchingEnabled () {
   VM_Magic.pragmaInline();
   return threadSwitchingEnabledCount == 1;
  }

  /**
   * Enable thread switching in this processor.
   */ 
  void enableThreadSwitching () {
    ++threadSwitchingEnabledCount;
    if (VM.VerifyAssertions) 
      VM.assert(threadSwitchingEnabledCount <= 1);
    if (VM.VerifyAssertions && 
        VM_Collector.gcInProgress() && !VM.BuildForConcurrentGC)
      VM.assert(threadSwitchingEnabledCount <1 || getCurrentProcessorId()==0);
    if (threadSwitchingEnabled() && threadSwitchPending) { 
      // re-enable a deferred thread switch
      threadSwitchRequested = -1;
      threadSwitchPending   = false;
    }
  }

  /**
   * Disable thread switching in this processor.
   */ 
  void disableThreadSwitching () {
    VM_Magic.pragmaInline();
    --threadSwitchingEnabledCount;
  }

  /**
   * Get processor that's being used to run the current java thread.
   */
  static VM_Processor getCurrentProcessor() {
    VM_Magic.pragmaInline();
    return VM_ProcessorLocalState.getCurrentProcessor();
  }
  
  /**
   * Get id of processor that's being used to run the current java thread.
   */ 
  static int getCurrentProcessorId () {
    VM_Magic.pragmaInline();
    return getCurrentProcessor().id;
  }

  /**
   * Become next "ready" thread.
   * Note: This method is ONLY intended for use by VM_Thread.
   */ 
  void dispatch () {
    if (VM.VerifyAssertions) VM.assert(lockCount == 0);// no processor locks should be held across a thread switch
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
    activeThreadStackLimit = newThread.stackLimit;
    //-#if RVM_FOR_IA32
    threadId       = newThread.getLockingId();
    //-#endif

    if (idleProcessor != null && !readyQueue.isEmpty() 
        && getCurrentProcessor().processorMode != NATIVEDAEMON) { 
      // if we've got too much work, transfer some of it to another 
      // processor that has nothing to do
      VM_Thread t = readyQueue.dequeue();
      if (trace) VM_Scheduler.trace("VM_Processor", "dispatch: offload ", t.getIndex());
      scheduleThread(t);
    }

    if (VM.BuildForConcurrentGC) newThread.stackBufferNeedScan = true;

    if (VM.BuildForCpuMonitoring) {
      double now = VM_Time.now();
      // primordial thread: ignore first time slice
      if (previousThread.cpuStartTime == -1) ; 
      else previousThread.cpuTotalTime += now - previousThread.cpuStartTime;
      previousThread.cpuStartTime = 0;    // this thread has stopped running
      newThread.cpuStartTime = now;  // this thread has started running
    }
    
    // (sets "previousThread.beingDispatched = false")
    VM_Magic.threadSwitch(previousThread, newThread.contextRegisters);
  }

  /**
   * Find a thread that can be run by this processor and remove it 
   * from its queue.
   */ 
  private VM_Thread getRunnableThread() {
    VM_Magic.pragmaInline();

int loopcheck = 0;
    for (int i=transferQueue.length(); 0<i; i--) {
      transferMutex.lock();
      VM_Thread t = transferQueue.dequeue();
      transferMutex.unlock();
      if (t.isGCThread){
	if (trace) VM_Scheduler.trace("VM_Processor", "getRunnableThread: collector thread", t.getIndex());
	return t;
      } else if (t.beingDispatched && t != VM_Thread.getCurrentThread()) { // thread's stack in use by some OTHER dispatcher
	if (trace) VM_Scheduler.trace("VM_Processor", "getRunnableThread: stack in use", t.getIndex());
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
		if (VM.VerifyAssertions) VM.assert (t.isNativeIdleThread);
	}
      } else {
	if (trace) VM_Scheduler.trace("VM_Processor", "getRunnableThread: transfer to readyQueue", t.getIndex());
	readyQueue.enqueue(t);
      }
    }

    if (!readyQueue.isEmpty()) {
      VM_Thread t = readyQueue.dequeue();
      if (trace) VM_Scheduler.trace("VM_Processor", "getRunnableThread: readyQueue", t.getIndex());
      if (VM.VerifyAssertions) VM.assert(t.beingDispatched == false || t == VM_Thread.getCurrentThread()); // local queue: no other dispatcher should be running on thread's stack
      return t;
    }

    if (ioQueue.isReady()) {
      VM_Thread t = ioQueue.dequeue();
      if (trace) VM_Scheduler.trace("VM_Processor", "getRunnableThread: ioQueue", t.getIndex());
      if (VM.VerifyAssertions) VM.assert(t.beingDispatched == false || t == VM_Thread.getCurrentThread()); // local queue: no other dispatcher should be running on thread's stack
      return t;
    }

    if (!idleQueue.isEmpty()) {
      VM_Thread t = idleQueue.dequeue();
      if (trace) VM_Scheduler.trace("VM_Processor", "getRunnableThread: idleQueue", t.getIndex());
      if (VM.VerifyAssertions) VM.assert(t.beingDispatched == false || t == VM_Thread.getCurrentThread()); // local queue: no other dispatcher should be running on thread's stack
      return t;
    }

    VM.assert(VM.NOT_REACHED); // should never get here (the idle thread should always be: running, on the idleQueue, or (maybe) on the transferQueue)
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
  void scheduleThread (VM_Thread t) {
    // if thread wants to stay on specified processor, put it there
    if (t.processorAffinity != null) {
      if (trace) VM_Scheduler.trace("VM_Processor.scheduleThread", "outgoing to specific processor:", t.getIndex());
      t.processorAffinity.transferThread(t);
      return;
    }

    // concurrent memory manager currently does not move threads between processors
    if (VM.BuildForConcurrentGC) { 
      if (trace) VM_Scheduler.trace("VM_Processor.scheduleThread", " staying on same processor");
      getCurrentProcessor().transferThread(t);
      return;
    }

    // if t is the last runnable thread on this processor, don't move it
    if (t == VM_Thread.getCurrentThread() && readyQueue.isEmpty() && transferQueue.isEmpty()) {
      if (trace) VM_Scheduler.trace("VM_Processor.scheduleThread",  "staying on same processor:", t.getIndex());
      getCurrentProcessor().transferThread(t);
      return;
    }

    // if a processor is idle, transfer t to it
    VM_Processor idle = idleProcessor;
    if (idle != null) {
      idleProcessor = null;
      if (trace) VM_Scheduler.trace("VM_Processor.scheduleThread", "outgoing to idle processor:", t.getIndex());
      idle.transferThread(t);
      return;
    }

    // otherwise distribute threads round robin
    if (trace) VM_Scheduler.trace("VM_Processor.scheduleThread",  "outgoing to round-robin processor:", t.getIndex());
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


//-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS (alternate implementation of jni)
  // Java to native code transitions, in vpState (bit definitions)
  static final int VP_IN_C_MASK   = 1;         // 1 = C; 0 = in VM (java)
  static final int VP_BLOCKED     = 2;         // 1 = Blocked in Java or C; 0 = not blocked 
  static final int GC_IN_PROGRESS = 4;         // 1 = GC running; 0 = not
//-#else (default implementation of jni)
  // definitions for VP status for default implementation of jni
  static final int UNASSIGNED_VP_STATUS    = 0;  
  static final int IN_JAVA                 = 1;
  static final int IN_NATIVE               = 2;
  static final int BLOCKED_IN_NATIVE       = 3;
  static final int IN_SIGWAIT              = 4;
  static final int BLOCKED_IN_SIGWAIT      = 5;
//-#endif

  static int generateNativeProcessorId () {
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
  static VM_Processor createNativeProcessorForExistingOSThread
    (VM_Thread withThisThread) {

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

  static int registerAttachedProcessor(VM_Processor newProcessor) {

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

  static int unregisterAttachedProcessor(VM_Processor pr) {
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


//-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
  // alternate implementation of jni

  static VM_Processor createNativeProcessor () {


    int processId = generateNativeProcessorId();
    VM_Processor newProcessor = new VM_Processor(processId, NATIVE);

    // create idle thread for processor
    VM_Thread t = new VM_NativeIdleThread(newProcessor);

    // There is a race between the native pthread which will be started shortly
    // and will run and intilize its idle thread, and our pthread/thread which
    // will shortly enqueus the current thread on the native processor transfer
    // queue.  If we win the race, the the native idle thread will not intialize
    // properly.  To avoid this we enqueue the idle thread on the transfer queue
    // of the native processor now, before the native pthread starts.
    //
    ////newProcessor.idleQueue.enqueue(t);
    t.start(newProcessor.transferQueue);

    // create VM_Thread for virtual cpu to execute
    //
    VM_Thread target = new VM_StartupThread(VM_RuntimeStructures.newStack(STACK_SIZE_NORMAL));
    // create virtual cpu and wait for execution to enter target's code/stack.
    // this is done with gc disabled to ensure that garbage 
    // collector doesn't move
    // code or stack before the C startoff function has a chance
    // to transfer control into vm image.
    //
    if (trace) 
      VM_Scheduler.trace("VM_Processor", 
                         "starting native virtual cpu pthread ");

    // Acquire global GC lockout field (at fixed address in the boot record).
    // This field will be released by this thread when it runs on the native 
    // processor and transfers control to the native code.
    // get the GC lockout lock .. yield if someone else has it
    while (true) {
      int lockoutVal = VM_Magic.prepare(VM_BootRecord.the_boot_record, 
					VM_Entrypoints.lockoutProcessorOffset);
      if ( lockoutVal == 0){
	lockoutId   = 
	if(VM_Magic.attempt(VM_BootRecord.the_boot_record, 
			    VM_Entrypoints.lockoutProcessorOffset,
			    VM_Magic.objectAsAddress
                            (VM_ProcessorLocalState.getCurrentProcessor()),
			    0, lockoutId))
          break;
      }else VM_Thread.yield();
    }


    newProcessor.activeThread = target;
    newProcessor.activeThreadStackLimit = target.stackLimit;
    target.registerThread(); // let scheduler know that thread is active.
    //-#if RVM_FOR_POWERPC
    VM.sysVirtualProcessorCreate(VM_Magic.getTocPointer(),
                                 VM_Magic.objectAsAddress(newProcessor),
                                 target.contextRegisters.gprs[VM.THREAD_ID_REGISTER],
                                 target.contextRegisters.gprs[VM.FRAME_POINTER]);
    //-#endif
    //-#if RVM_FOR_IA32
    VM.sysVirtualProcessorCreate(VM_Magic.getTocPointer(),
                                 VM_Magic.objectAsAddress(newProcessor),
                                 0, 
                                 target.contextRegisters.gprs[VM.FRAME_POINTER]);
    //-#endif
    while (!newProcessor.isInitialized)
      VM.sysVirtualProcessorYield();
    ///    VM.enableGC();

    if (trace) 
      VM_Scheduler.trace("VM_Processor", "started native virtual cpu pthread ");

    return newProcessor;
  } 

  /***********
  static VM_Processor getRVMProcessor () {

    // does current thread have a processor affinity
    //
    VM_Processor p = VM_Thread.getCurrentThread().processorAffinity;
    if ( p != null) return p;

    // if a processor is idle, put thread there
    //
    p = idleProcessor;
    if ( p != null) {
      idleProcessor = null;
      return p;
    }

    // otherwise round robin
    //
    return getCurrentProcessor().chooseNextProcessor();
    
   **************/

//-#else
  // default implementation of jni

  // create a native processor for default implementation of jni
  //
  static VM_Processor createNativeProcessor () {

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
    VM_Thread target = new VM_StartupThread(VM_RuntimeStructures.newStack(STACK_SIZE_NORMAL));

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
				 target.contextRegisters.gprs[VM.FRAME_POINTER]);
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

//-#endif


  //---------------------//
  // Garbage Collection  //
  //---------------------//


//-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
// alternate implementation of jni

  /**
   * sets the VP state to indicate that VP is part of GC that is starting
   *  returns the previous value of VPState
   */ 
  public int  setGCState () {
    int newState, oldState;
    do{
      oldState = VM_Magic.prepare(this, VM_Entrypoints.vpStateOffset);
      if (VM.VerifyAssertions) VM.assert((oldState & GC_IN_PROGRESS) == 0) ;
      newState = oldState | GC_IN_PROGRESS;
    }while (! (VM_Magic.attempt(this, VM_Entrypoints.vpStateOffset, 
                                oldState, newState)));
    return oldState;
  }

  /**
   * resets the VP state to indicate that GC is complete
   *  returns the previous value of VPState
   */ 
  public int  resetGCState () {
    int newState, oldState;
    do{
      oldState = VM_Magic.prepare(this, VM_Entrypoints.vpStateOffset);
      if (VM.VerifyAssertions) 
        VM.assert((oldState & GC_IN_PROGRESS) ==  GC_IN_PROGRESS) ;
      newState = oldState & (~GC_IN_PROGRESS);
    }while (! (VM_Magic.attempt(this, VM_Entrypoints.vpStateOffset, 
                                oldState, newState)));
    return oldState;
  }

//-#else
// default implementation of jni

  /**
   * sets the VP status to BLOCKED_IN_NATIVE if it is currently IN_NATIVE (ie C)
   * returns true if BLOCKED_IN_NATIVE
   */ 
  public boolean lockInCIfInC () {
    int newState, oldState;
    boolean result = true;
    do {
      oldState = VM_Magic.prepare(VM_Magic.addressAsObject(vpStatusAddress), 0);
      if (VM.VerifyAssertions) VM.assert(oldState != BLOCKED_IN_NATIVE) ;
      if (oldState != IN_NATIVE) {
        if (VM.VerifyAssertions) 
          VM.assert((oldState==IN_JAVA)||(oldState==IN_SIGWAIT)) ;
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
      if (VM.VerifyAssertions) VM.assert(oldState != BLOCKED_IN_SIGWAIT) ;
      if (oldState != IN_SIGWAIT) {
        if (VM.VerifyAssertions) VM.assert(oldState==IN_JAVA);
        result = false;
        break;
      }
    } while (!(VM_Magic.attempt(VM_Magic.addressAsObject(vpStatusAddress), 
                                0, oldState, BLOCKED_IN_SIGWAIT)));
    return result;
  }

//-#endif

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
  int threadSwitchRequested;
  
  /**
   * thread currently running on this processor
   */
  VM_Thread        activeThread;    

  /**
   * cached activeThread.stackLimit;
   * removes 1 load from stackoverflow sequence.
   */
  int activeThreadStackLimit;

//-#if RVM_FOR_IA32
  // to free up (nonvolatile or) scratch registers
  Object jtoc;
  int    threadId;
  /**
   * FP for current frame (opt compiler wants FP register) 
   * TODO warn GC about this guy
   */
  int    framePointer;        
  /**
   * "hidden parameter" for interface invocation thru the IMT
   */
  int    hiddenSignatureId;   
  /**
   * "hidden parameter" from ArrayIndexOutOfBounds trap to C trap handler
   */
  int    arrayIndexTrapParam; 
//-#endif

//-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  // An array of VM_SizeControl: used by noncopying memory managers;
  // by making one such array per processor, allocations can be performed
  // in parallel without locking, except when a new block is needed.
  // 
  VM_SizeControl[] sizes;
  VM_SizeControl[] GC_INDEX_ARRAY;

  // Writebuffer for generational collectors
  // contains a "remembered set" of old objects with modified object references.
  //            ---+---+---+---+---+---+                    ---+---+---+---+---+---+
  //   initial:    |   |   |   |   |lnk|             later:    |obj|obj|   |   |lnk|
  //            ---+---+---+---+---+---+                    ---+---+---+---+---+---+
  //            ^top            ^max                                ^top    ^max
  //
  int[]  modifiedOldObjects;          // the buffer
  int    modifiedOldObjectsTop;       // address of most recently filled slot
  int    modifiedOldObjectsMax;       // address of last available slot in buffer

  // pointers for allocation from Processor local memory "chunks"
  //
  /**
   * current position within current allocation buffer
   */
  int    localCurrentAddress;  
  /**
   * end (1 byte beyond) of current allocation buffer
   */
  int    localEndAddress;      

  // pointers for allocation from processor local "chunks" or "ToSpace" memory,
  // for copying live objects during GC.
  //
  /**
   * current position within current "chunk"
   */
  int    localMatureCurrentAddress;   
  /**
   * end (1 byte beyond) of current "chunk"
   */
  int    localMatureEndAddress;       
//-#endif

  /*
   * END FREQUENTLY ACCESSED INSTANCE FIELDS
   */
  
  /**
   * Identity of this processor.
   * Note: 1. VM_Scheduler.processors[id] == this processor
   *      2. id must be non-zero because it is used in 
   *      VM_ProcessorLock ownership tests
   */ 
  int id;

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
  VM_Thread        previousThread;  

  /**
   * threads to be added to ready queue
   */
  VM_GlobalThreadQueue transferQueue; 
  VM_ProcessorLock     transferMutex; // guard for above

  /**
   * threads waiting for a timeslice in which to run
   */
  VM_ThreadQueue   readyQueue;    
  /**
   * threads waiting for i/o
   */
  VM_ThreadIOQueue ioQueue;       
  /**
   * thread to run when nothing else to do
   */
  VM_ThreadQueue   idleQueue;     

  // The following are conditionalized by "if (VM.VerifyAssertions)"
  //
  /**
   * number of processor locks currently held (for assertion checking)
   */
  int              lockCount;     

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
  VM_Processor next; 

  //--------------------//
  // Start of GC stuff. //
  //--------------------//

//-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  // Reference Counting Collector additions 
  int    incDecBuffer;        // the buffer
  int    incDecBufferTop;     // address of most recently filled slot in buffer
  int    incDecBufferMax;     // address of last available slot in buffer
  int    localEpoch;

  // misc. fields - probably should verify if still in use
  //
  int    large_live;		// count live objects during gc
  int	 small_live;		// count live objects during gc
  long   totalBytesAllocated;	// used for instrumentation in allocators
  long   totalObjectsAllocated; // used for instrumentation in allocators
//-#endif
//-#if RVM_WITH_GCTk

  // steve - add your stuff here - such as...
  GCTk_Collector collector;
  ADDRESS writeBuffer0;
  ADDRESS writeBuffer1;

  ADDRESS remset[];

  // Allocation bump pointers (per processor)
  ADDRESS allocBump0;
  ADDRESS allocBump1;
  ADDRESS allocBump2;
  ADDRESS allocBump3;
  ADDRESS allocBump4;
  ADDRESS allocBump5;
  ADDRESS allocBump6;
  ADDRESS allocBump7;

  // Per-allocator
  ADDRESS allocSync0;
  ADDRESS allocSync1;
  ADDRESS allocSync2;
  ADDRESS allocSync3;
  ADDRESS allocSync4;
  ADDRESS allocSync5;
  ADDRESS allocSync6;
  ADDRESS allocSync7;
//-#endif

  //--------------------//
  //  End of GC stuff.  //
  //--------------------//

  // Type of Virtual Processor
  //
  int    processorMode;

//-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
  // alternate implementation of jni
  /**
   * state field in each VM_Processor
   */
  int     vpState;                     
//-#else
  // default implementation of jni

  // processor status fields are in a (large & unmoving!) array of status words
  static final int VP_STATUS_SIZE = 8000;
  static final int VP_STATUS_STRIDE = 101;

  static int    lastVPStatusIndex = 0;
  static int[]  vpStatus = new int[VP_STATUS_SIZE];  // must be in pinned memory !!                 

  /**
   * index of this processor's status word in vpStatus array
   */
  int   vpStatusIndex;            
  /**
   * address of this processors status word in vpStatus array
   */
  int   vpStatusAddress;          
//-#endif

  /**
   * pthread_id (AIX's) for use by signal to wakeup
   * sleeping idle thread (signal accomplished by pthread_kill call)
   * 
   * CRA, Maria
   */
  int  	  pthread_id;

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

  void dumpProcessorState() {
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
    VM_Scheduler.writeString(" idleQueue:");
    if (idleQueue!=null) idleQueue.dump();
    if ( processorMode == RVM) VM_Scheduler.writeString(" mode: RVM\n");
    else if ( processorMode == NATIVE) VM_Scheduler.writeString(" mode: NATIVE\n");
    else if ( processorMode == NATIVEDAEMON) VM_Scheduler.writeString(" mode: NATIVEDAEMON\n");
//-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS (alternate implementation of jni)
//-#else (default implementation of jni)
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
//-#endif
  }
}
