/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * VM_Handshake handles mutator requests to initiate a collection, 
 * and wait for a collection to complete.  It implements the process
 * of suspending all mutator threads executing in Java and starting
 * all the GC threads (VM_CollectorThreads) for the processors that
 * will be participating in a collection.  This may not be all 
 * processors, if we exclude those executing in native code.
 *
 * Because the threading strategy within RVM is currently
 * under revision, the logic here is also changing and somewhat "messy".
 *
 * @see VM_CollectorThread
 *
 * @author Derek Lieber
 * @author Bowen Alpern
 * @author Stephen Smith
 */
class VM_Handshake implements VM_Uninterruptible {
  
  private static final boolean trace = false;
  private static final boolean debug_native = false;   // temporary debugging of new threads
  
  static final int LOCKOUT_GC_WORD = 0x0CCCCCCC;
  
  protected boolean requestFlag;
  protected boolean completionFlag;
  
  /**
   * Initiates a garbage collection.  Called from requestAndAwaitCompletion
   * by the first mutator thread to request a collection using the
   * current VM_Handshake object.
   *
   * The sequence of events that start a collection is initiated by the
   * calling mutator, and it then yields, waiting for the collection
   * to complete.
   *
   * While mutators are executing, all the GC threads (VM_CollectorThreads)
   * reside on a single system queue, VM_Scheduler.collectorQueue.  This
   * method determines which processors will participate in the collection,
   * dequeues the GC threads associated with those processors, and
   * schedules them for executing on their respective processors.
   * (Most of the time, all processors and thus all GC threads participate,
   * but this is changing as the RVM thread startegy changes.)
   *
   * The collection actually starts when all participating GC threads
   * arrive at the first rendezvous in VM_CollectorThreads run method,
   * and suspend thread switching on their processors.
   *
   * While collection is in progress, mutators are not explicitly waiting
   * for the collection. They reside in the thread dispatch queues of their
   * processors, until the collector threads re-enable thread switching.
   */
  private void initiateCollection() {

    int maxCollectorThreads;

    // check that scheduler initialization is complete
    //	
    if (!VM_Scheduler.allProcessorsInitialized) {
      VM.sysWrite(" Garbage collection required before system fully initialized\n");
      VM.sysWrite(" Specify larger than default heapsize on command line\n");
      VM_Scheduler.dumpStack();
      VM.shutdown(-1);
    }

    if (debug_native) {
      //      VM_Scheduler.trace("VM_Handshake:initiateCollection","dumping machine...");
      //      VM_Scheduler.dumpVirtualMachine();
      VM_Scheduler.trace("\nVM_Handshake:initiateCollection","collectorQueue:");
      VM_Scheduler.writeString("before waiting:"); VM_Scheduler.collectorQueue.dump();
    }

    // wait for all gc threads to finish preceeding collection cycle

    //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
    // all RVM Processors participate in all collections
    // there is no NativeDaemon Processor
    maxCollectorThreads = VM_Scheduler.numProcessors;

    //-#else
    // include NativeDaemonProcessor collector thread in the count - if it exists
    // check for null to allow builds without a NativeDaemon (see VM_Scheduler)
    if ( !VM.BuildForSingleVirtualProcessor && VM_Scheduler.processors[VM_Scheduler.nativeDPndx] != null )
      maxCollectorThreads = VM_Scheduler.numProcessors + 1;  
    else
      maxCollectorThreads = VM_Scheduler.numProcessors;
    //-#endif

    VM_Scheduler.collectorMutex.lock();
    while (VM_Scheduler.collectorQueue.length() < maxCollectorThreads) {
      VM_Scheduler.collectorMutex.unlock();
      if (trace) VM_Scheduler.trace("VM_Handshake", "mutator: waiting for previous collection to finish");
      VM_Thread.getCurrentThread().yield();
      VM_Scheduler.collectorMutex.lock();
    }
    VM_Scheduler.collectorMutex.unlock();

    if (debug_native) {
      VM_Scheduler.writeString("after waiting:"); VM_Scheduler.collectorQueue.dump();
    }
    
    // Acquire global lockout field (at fixed address in the boot record).
    // This field will be released when gc completes
    while (true) {
      int lockoutVal = VM_Magic.prepare(VM_BootRecord.the_boot_record,
					VM_Entrypoints.lockoutProcessorField.offset);
      if ( lockoutVal == 0) {
	if(VM_Magic.attempt(VM_BootRecord.the_boot_record,
			    VM_Entrypoints.lockoutProcessorField.offset,
			    0, LOCKOUT_GC_WORD))
	  break;
      }
      else {
	if (debug_native) VM_Scheduler.traceHex("VM_Handshake:initiateCollection",
					     "lockoutLock contains",lockoutVal);
      }
    }
    
    // reset counter for collector threads arriving to participate in the collection
    VM_CollectorThread.participantCount[0] = 0;

    // reset rendezvous counters to 0, the decision about which collector threads
    // will participate has moved to the run method of CollectorThread
    //
    VM_CollectorThread.gcBarrier.resetRendezvous();

    //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
    // this is done elsewhere with this version of jni support
    //-#else
    // scan all the native Virtual Processors and attempt to block those executing
    // native C, to prevent returning to java during collection.  the first collector
    // thread will wait for those not blocked to reach the SIGWAIT state.
    // (see VM_CollectorThread.run)
    //
    for (int i = 1; i <= VM_Processor.numberNativeProcessors; i++) {
      if (VM.VerifyAssertions) VM.assert(VM_Processor.nativeProcessors[i] != null);
      VM_Processor.nativeProcessors[i].lockInCIfInC();
      if (trace) {
        int newStatus =  VM_Processor.vpStatus[VM_Processor.nativeProcessors[i].vpStatusIndex];
        VM_Scheduler.trace("VM_Handshake.initiateCollection:", "Native Processor", i);
        VM_Scheduler.trace("                                ", "new vpStatus    ", newStatus);
      }
    }
    //-#endif

    // Dequeue and schedule collector threads on ALL RVM Processors,
    // including those running system daemon threads (ex. NativeDaemonProcessor)
    //
    VM_Scheduler.collectorMutex.lock();
    while (VM_Scheduler.collectorQueue.length() > 0) {
      VM_Thread t = VM_Scheduler.collectorQueue.dequeue();
      t.scheduleHighPriority();
      VM_Processor p = t.processorAffinity;
      // set thread switch requested condition in VP
      p.threadSwitchRequested = -1;
    }
    VM_Scheduler.collectorMutex.unlock();

    
  }   // initiateCollection
  
  /**
   * Called by mutators to request a garbage collection and wait
   * for it to complete.
   * If the completionFlag is already set, return immediately.
   * Else, if the requestFlag is not yet set (ie this is the
   * first mutator to request this collection) then initiate
   * the collection sequence & then wait for it to complete.
   * Else (it has already started) just wait for it to complete.
   *
   * Waiting is actually just yielding the processor to schedule
   * the collector thread, which will disable further thread
   * switching on the processor until it has completed the
   * collection.
   */
  void
    requestAndAwaitCompletion() {
    synchronized (this) {
      if (completionFlag) {
	if (trace) VM_Scheduler.trace("VM_Handshake", "mutator: already completed");
	return;
      }
      if (requestFlag) {
	if (trace) VM_Scheduler.trace("VM_Handshake", "mutator: already in progress");
      }
      else {
	// first mutator initiates collection by making all gc threads runnable at high priority
	if (trace) VM_Scheduler.trace("VM_Handshake", "mutator: initiating collection");
	VM_CollectorThread.gcBarrier.rendezvousStartTime = VM_Time.now();
	requestFlag = true;
	initiateCollection();
      }
    }  // end of synchronized block
    
    // allow a gc thread to run
    VM_Thread.getCurrentThread().yield();
    
    if (trace) VM_Scheduler.trace("VM_Handshake", "mutator: running");
  }
  
  /**
   * Set the completion flag that indicates the collection has completed.
   * Called by a collector thread after the collection has completed.
   * It currently does not do a "notify" on waiting mutator threads,
   * since they are in VM_Processor thread queues, waiting
   * for the collector thread to re-enable thread switching.
   *
   * @see VM_CollectorThread
   */
  synchronized void
    notifyCompletion() {
    if (trace) VM_Scheduler.trace("VM_Handshake", "collector: completed");
    //    if (debug_native) VM_Scheduler.dumpVirtualMachine();
    completionFlag = true;
  }

  /**
   * Acquire LockoutLock.  Accepts a value to store into the lockoutlock
   * word when it becomes available (value == 0). If not available when
   * called, a passed flag indicates either spinning or yielding until
   * the word becomes available.
   *
   * @param value    Value to store into lockoutlock word
   * @param spinwait flag to cause spinning (if true) or yielding
   */
  static void
  acquireLockoutLock( int value, boolean spinwait )
  {
    if (spinwait) {
      while (true) {
	int lockoutVal = VM_Magic.prepare(VM_BootRecord.the_boot_record,
					  VM_Entrypoints.lockoutProcessorField.offset);
	if ( lockoutVal == 0) {
	  if(VM_Magic.attempt(VM_BootRecord.the_boot_record,
			      VM_Entrypoints.lockoutProcessorField.offset,
			      0, value))
	    break;
	}
      }
      return;
    }

    // else - no spinwait:
    // yield until lockout word is available (0), then attempt to set

    while (true) {
      int lockoutVal = VM_Magic.prepare(VM_BootRecord.the_boot_record,
					VM_Entrypoints.lockoutProcessorField.offset);
      if ( lockoutVal != 0) {
	if (debug_native) VM_Scheduler.trace("Handshake:acquireLockOutLock",
					  "yielding: lockoutVal =",lockoutVal);
	VM_Thread.yield();
	continue;
      }
      else {
	if(VM_Magic.attempt(VM_BootRecord.the_boot_record,
			    VM_Entrypoints.lockoutProcessorField.offset,
			    0, value))
	  break;
      }
    }
  }  // acquireLockoutLock

  /**
   * Release the LockoutLock by setting the lockoutlock word to 0.
   * The expected current value that should be in the word can be
   * passed in, and is verified if not 0.
   *
   * @param value    Value that should currently be in the lockoutlock word
   */
  static void
  releaseLockoutLock( int value )
  {
    while (true) {
      int lockoutVal = VM_Magic.prepare(VM_BootRecord.the_boot_record,
					VM_Entrypoints.lockoutProcessorField.offset);
      // check that current value is as expected
      if (VM.VerifyAssertions && (value!=0)) VM.assert( lockoutVal == value );
      // OK, reset to zero
      if(VM_Magic.attempt(VM_BootRecord.the_boot_record,
			  VM_Entrypoints.lockoutProcessorField.offset,
			  lockoutVal, 0))
	break;
    }
  }  // releaseLockoutLock

  /**
   * Return the current contents of the lockoutLock word
   *
   * @return  current lockoutLock word
   */
  static int
  queryLockoutLock()
  {
    int lockoutVal;
    while (true) {
      lockoutVal = VM_Magic.prepare(VM_BootRecord.the_boot_record,
				    VM_Entrypoints.lockoutProcessorField.offset);
      if (VM_Magic.attempt(VM_BootRecord.the_boot_record,
			   VM_Entrypoints.lockoutProcessorField.offset,
			   lockoutVal, lockoutVal))
	break;
    }
    return lockoutVal;

  }  // queryLockoutLock

}


