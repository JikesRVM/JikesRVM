/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * This thread executes in a distinct daemon thread: its
 * functions are as follows:
 * 
 * <p> its normal state is waiting for a (to be defined) signal
 * 
 * <p> if all RVM threads are "stuck" in C (unlikely but
 * not impossible) it is available to field the timeslicer
 * interrupt.
 *
 * <p> On timeslice end, timer interrupt handler checks each
 * RVM processor to see if it is "stuck in C", i.e., 
 * has not yielded within VM_STUCK_TICKS timeslices.
 * 
 * <p> if t.i.h. is executing on the daemon processor it raises
 * the SIGCONT signal; else the executing thread sends that 
 * signal to the daemon processor.
 *
 * <p> on receiving the SIGCONT signal, the run method loops 
 * through the RVM processors;  it should have been 
 * signalled only if at least one RVM processor is 
 * stuck in C
 * <pre>
 * if it is stuck in C 
 *   mark the associated vp "stuck in C"
 *   if there is an available pthread/(red) vp on the available queue 
 *     choose it;
 *   else get a new pthread; build a new red vp
 *   mark the red vp "stuck in C"
 *   associate the new pthread with the previous vp
 *     the stuck Java thread becomes current on the new rvp
 *     an appropriate idle thread becomes current on the
 *     previous vp - which thread will yield to its idle
 *     queue, triggering schedule in the new pthread. 
 * </pre>
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */ 
class VM_NativeDaemonThread extends VM_Thread {

  static final boolean DEBUG = false;
  static final boolean trace = false;
	static int switch_count = 0;

  boolean inSysWait;

  VM_NativeDaemonThread(VM_Processor processorAffinity) {
    makeDaemon(true);
    super.isNativeDaemonThread = true;
    super.processorAffinity = processorAffinity;
    if (trace) VM_Scheduler.trace(" In constructor method of NDT ", "  ");
  }
  public String
    toString () {
      return "VM_NativeDaemonThread";
    }


  public void run () { // overrides VM_Thread

    if (trace) VM_Scheduler.trace(" Entering run method of NDT ", "  ");
    VM_Processor myProcessor = VM_ProcessorLocalState.getCurrentProcessor();
    int lockoutAddr = VM_Magic.objectAsAddress( VM_BootRecord.the_boot_record) + 
                                                VM_Entrypoints.lockoutProcessorOffset;
    int i, transferCount=0, stuckCount=0;

    //  make sure Opt compiler does not compile this method
    //  references stored in registers by the opt compiler will not be relocated by GC
    VM_Magic.pragmaNoOptCompile();

    // save current frame pointer in threads JNIEnv, and set flag recognized by GC
    // which will cause this (NativeIdleThread) thread to have its stack scanned
    // starting at this frame
    //
    VM_Thread.getCurrentThread().jniEnv.JNITopJavaFP = VM_Magic.getFramePointer();

    // remember status word index for executing processor. It will remain valid
    // even if GC moves the processor object
    //
    int processorStatusIndex = VM_Processor.getCurrentProcessor().vpStatusIndex;

    while (true) {
      if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logIdleEvent();

      if (trace) VM_Scheduler.trace("NDT:","calling SigWait - transferred",transferCount);

      // Save current frame pointer in threads JNIEnv, and set flag recognized by GC
      // which will cause this (NativeIdleThread) thread to have its stack scanned
      // starting at this frame, if it is in a sigwait syscall during a collection.
      //
      VM_Thread.getCurrentThread().jniEnv.JNITopJavaFP = VM_Magic.getFramePointer();

      inSysWait = true;  // temporary...GC code looks for this flag in native idle threads

      int TOC = 0;
      //-#if RVM_FOR_POWERPC
      TOC = VM_BootRecord.the_boot_record.sysTOC;
      //-#endif
      VM_Magic.sysCallSigWait(VM_BootRecord.the_boot_record.sysPthreadSigWaitIP,
			      TOC,
		              myProcessor.vpStatusAddress,
			      VM_Processor.IN_SIGWAIT,
			      VM_Thread.getCurrentThread().contextRegisters);

      inSysWait = false;
      VM_Processor.vpStatus[processorStatusIndex] = VM_Processor.IN_JAVA;

      // GC might have happened and my processor object moved
      //
      VM_ProcessorLocalState.setCurrentProcessor(myProcessor);

      if (trace) VM_Scheduler.trace("NDT:","woke up from SigWait");

      if (VM_Handshake.queryLockoutLock() != 0) {
	// go back to sigwait, probably collided with GC
	transferCount = -1;   // to indicate what happened in above trace call
	continue;
      }

      // GET HERE FROM A SIGNAL SENT FROM cSignalHandler in libvm.C -
      // iff at least one RVM vp has been found "stuck in C"
      //
      // Count RVM processors that are stuck, and preallocate sufficient
      // native processors, so that transfers can be done without allocation.

      stuckCount = 0;   // counts threads observed stuck
      transferCount = 0;    // counts threads actually transferred
      for (i = VM_Scheduler.PRIMORDIAL_PROCESSOR_ID; i <= VM_Scheduler.numProcessors; i++) {
	VM_Processor vp = VM_Scheduler.processors[i];
	// WARNING: -2 in next instruction must match MISSES in libvm.C
	if (vp.threadSwitchRequested < -2) {
	  if (VM_Processor.vpStatus[vp.vpStatusIndex] == VM_Processor.IN_NATIVE) {
	    stuckCount++;
	    if (trace || (DEBUG && (vp.threadSwitchRequested*-1)%100 == 0)) {
	      VM_Scheduler.trace("\n  NDT:","threadSwitchRequested =",
				 vp.threadSwitchRequested);
	      vp.dumpProcessorState();
	    }
	  }
	}
      }

      if (trace) VM_Scheduler.trace("NDT:","stuck processor count =",stuckCount);

      if (stuckCount == 0) continue;  // false alarm, no processors stuck

      // pre allocate required number of native processors, if necessary
      // NOTE: resulting allocations may trigger GC, in which case, this
      // daemon thread, being IN_JAVA (not IN_SIGWAIT) will participate
      //
      createNativeProcessors(stuckCount);

      // acquire lockoutlock to prevent GC, and other major restructuring
      // like AttachVM etc., during the transfer of blocked threads to
      // native processors. Yield until available.

      VM_Handshake.acquireLockoutLock( 0x0DDDDDDD, false /*yield*/ );

      // find the processor(s) stuck in C and move the blocked thread(s) 
      // (pthread and java thread) to native processors, and take the pthread
      // from the native processor to continue execution on the previously
      // blocked RVM processor.

      for (i = VM_Scheduler.PRIMORDIAL_PROCESSOR_ID; i <= VM_Scheduler.numProcessors; i++) {
	VM_Processor vp = VM_Scheduler.processors[i];
	// WARNING: -2 in next instruction must match MISSES in libvm.C
	if (vp.threadSwitchRequested < -2) {
	  boolean result = vp.lockInCIfInC();
	  if (result) {
	    transfer(vp);
	    transferCount++;
	    // quit after transferring up to the previously observed stuckCount
	    // because we may not have sufficient available NativeProcessors
	    // and we do not want to allocate while holding the lockoutLock
	    if (transferCount == stuckCount) break;
	  }
	}
      }

      VM_Handshake.releaseLockoutLock( 0x0DDDDDDD );

    }  // while(true)
    
  }	//run method

  /**
   * this method is called to move a virtual processor currently running
   * a Java thread "stuck  in C", minus the thread, to a new pthread,
   * and leave the stuck thread on the current pthread, with a "skeleton" vp
   * (or alternately, move a new pthread to the current vp, minus the stuck
   * thread, and move a new skeleton vp to the current pthread)
   */
   void transfer (VM_Processor stuckProcessor) {

     if (trace) VM_Scheduler.trace("NDT","Entering transfer for processor",stuckProcessor.id);
		 switch_count++;
     VM_Scheduler.nativeProcessorMutex.lock();
     VM_Processor nativeProcessor = VM_Scheduler.nativeProcessorQueue.dequeue();
     VM_Scheduler.nativeProcessorMutex.unlock();
     if (nativeProcessor == null) {
       if (trace) VM.sysWrite("NDT: transfer: dequeue returned null\n");
       VM.sysFail("No Native Processors For Blocked Threads");
     }

     // stuck Processor is now blocked in C. Swap p_threads with native Processor,
     // and also swap status words of the two processors.
     // 
     switchPThread(stuckProcessor, nativeProcessor);

     // active java thread of stuck Processor should now be the native processors
     // NativeIdleThread, IN_SIGWAIT, waiting for a signal.
     if (VM.VerifyAssertions) {
       VM.assert(VM_Processor.vpStatus[stuckProcessor.vpStatusIndex] == VM_Processor.IN_SIGWAIT);
       VM.assert(VM_Processor.vpStatus[nativeProcessor.vpStatusIndex] == VM_Processor.BLOCKED_IN_NATIVE);
     }

     // now the native idle thread is "active" on the blue/stuck vp, and
     // waiting for a signal.  Signal its p_thread now, upon awakening it will
     // become the new p_thread for the stuck vp.  The java nativeIdleThread
     // will yield back to its native processor.
     // 
     if (trace) {
      VM_Scheduler.trace("NDT","signalling NIT on processor",stuckProcessor.id);
      VM_Scheduler.trace("NDT","with pthread_id =",stuckProcessor.pthread_id);
     }
     VM.sysCall1(VM_BootRecord.the_boot_record.sysPthreadSignalIP, stuckProcessor.pthread_id);

     // wait for the native processor status to become UNBLOCKED before returning.
     // if we return with it blocked, and GC immediately initiates, GC will find
     // the native processor in an unexpected BLOCKED_IN_NATIVE state
     //
     int loopCount = 1;
     while (VM_Processor.vpStatus[nativeProcessor.vpStatusIndex] ==
	    VM_Processor.BLOCKED_IN_NATIVE) {
       // just spin - we are running on a separate pthread
       if (VM.VerifyAssertions && (loopCount++%100000==0))
	 VM_Scheduler.trace("NDT","waiting for native VP to unblock, count =",loopCount);
     }
	
   }

  /**
   * this method switches the active threads of the input vps and
   * the pthread_ids. To make this work, must set vp pointers
   * in the "stuck" thread's stack and JNI_Env object to the
   * new vps address.  For this to work properly, cooperation
   * with the idle thread in the new vp is required.
   */
  void switchPThread (VM_Processor stuck_vp, VM_Processor native_vp) {
    if (trace) {
      VM_Scheduler.trace("NDT","Entering switchPThread");
      VM_Scheduler.trace("NDT","switchPThread - stuck_vp =",stuck_vp.id);
      stuck_vp.dumpProcessorState();
      VM_Scheduler.trace("NDT","switchPThread - native_vp =",native_vp.id);
      native_vp.dumpProcessorState();
    }

    // give the new (native) processor the status word of the old (stuck) processor
    // briefly, both processors have the "BLOCKED_IN_NATIVE" status word, until
    // the stuck thread is switched to the new processor.
    //
    int tempStatusAddress = native_vp.vpStatusAddress;
    int tempStatusIndex   = native_vp.vpStatusIndex;
    native_vp.vpStatusAddress = stuck_vp.vpStatusAddress;
    native_vp.vpStatusIndex   = stuck_vp.vpStatusIndex;

    // sync these changes - before changing processor of stuck thread
    // to ensure stuck thread continues to see BLOCKED_IN_NATIVE
    //
    VM_Magic.sync();

    // switch saved Processor refs in stuck threads JNIEnvironment & java to C 
    // glue stack frame to point to the new (native) processor
    //
    VM_Thread stuckThread = stuck_vp.activeThread;
    VM_JNIEnvironment stuckEnv = stuckThread.getJNIEnv();
    stuckEnv.savedPRreg = native_vp;
    int topJavaFP = stuckEnv.JNITopJavaFP;
//-#if RVM_FOR_IA32
    VM_Magic.setMemoryWord(topJavaFP + VM_JNICompiler.JNI_PR_OFFSET, VM_Magic.objectAsAddress(native_vp));
//-#else 
    int callerFP  = VM_Magic.getMemoryWord(topJavaFP);
    VM_Magic.setMemoryWord(callerFP - JNI_PR_OFFSET, VM_Magic.objectAsAddress(native_vp));
//-#endif 

    // active thread of native vp should be its NativeIdleThread
    if (VM.VerifyAssertions) VM.assert( native_vp.activeThread.isNativeIdleThread == true );

    // switch active threads on the two processors
    VM_NativeIdleThread nativeIdleThread = (VM_NativeIdleThread) native_vp.activeThread;
    native_vp.activeThread  = stuckThread;
    stuck_vp.activeThread  = nativeIdleThread;
/// new line below
    stuck_vp.threadSwitchRequested = 0; // reset

    int temp = native_vp.pthread_id;
    native_vp.pthread_id = stuck_vp.pthread_id; 
    stuck_vp.pthread_id = temp; 
//-#if RVM_FOR_IA32
    temp = native_vp.threadId;
    native_vp.threadId = stuck_vp.threadId; 
    stuck_vp.threadId = temp; 
//-#endif 

    // set return affinity of stuck thread to original (blue/old) processor
    stuckThread.returnAffinity = stuck_vp;

    // set field in nativeIdleThread to tell it which processor it has migrated to
    // it will use this to update its processor reg after being signalled
    nativeIdleThread.blockedProcessor = stuck_vp;

    // sync again, to ensure stuck thread sees its new processor, before
    // switching the old processors status word to that of the native
    // processor (with status = IN_SIGWAIT)
    VM_Magic.sync();

    // now complete the swap of status words
    stuck_vp.vpStatusAddress = tempStatusAddress;
    stuck_vp.vpStatusIndex   = tempStatusIndex;

    if (trace) {
      VM_Scheduler.trace("NDT","Leaving switchPThread");
      VM_Scheduler.trace("NDT","switchPThread - stuck_vp =",stuck_vp.id);
      stuck_vp.dumpProcessorState();
      VM_Scheduler.trace("NDT","switchPThread - native_vp =",native_vp.id);
      native_vp.dumpProcessorState();
    }

  }  // switchPThread
		

  /**
   * this method is called to create a new instance of VM_Processor
   * to control the pthread on which the stuck in C thread is running
   * and a new pthread to execute the rest of the workload of the 
   * existing blue processor
   */
  VM_Processor OLD_getNewNative () {
    // create a new virtual processor
    //
    if (trace) VM_Scheduler.trace("NDT","entering getNewNative");
    VM_Processor processor = VM_Processor.createNativeProcessor();
    
    // wait for created processor to show up on the native processor queue
    // (and get to IN_SIGWAIT state)
    while ( VM_Scheduler.nativeProcessorQueue.length() == 0 )
      VM.sysVirtualProcessorYield();
    
    // dequeue the new processor and return it
    //
    VM_Scheduler.nativeProcessorMutex.lock();
    processor = VM_Scheduler.nativeProcessorQueue.dequeue();
    VM_Scheduler.nativeProcessorMutex.unlock();
    if (trace) VM_Scheduler.trace("NDT","returning native processor",processor.id);
    return processor;
  }

  void createNativeProcessors (int needed) {

    int needToCreate = needed - VM_Scheduler.nativeProcessorQueue.length();

    if (needToCreate <= 0) return;


    // reset startup locks count
    //-#if RVM_FOR_SINGLE_VIRTUAL_PROCESSOR
    //-#else
//  VM.sysInitializeStartupLocks(needToCreate);
    //-#endif

    while (needToCreate > 0) {
      VM_Processor processor = VM_Processor.createNativeProcessor();
      if (trace) VM_Scheduler.trace("NDT","created Native VP with ID =",processor.id);
      needToCreate--;
    }

    // wait for created processors to show up on the native processor queue
    // (and get to IN_SIGWAIT state)
    while ( VM_Scheduler.nativeProcessorQueue.length() < needed )
      VM.sysVirtualProcessorYield();

    return;
  }  // createNativeProcessors
  
} // class


