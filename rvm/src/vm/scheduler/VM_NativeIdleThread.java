/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * @author Bowen Alpern
 * @author Derek Lieber
 */

/**
 * This thread is scheduled when there is no native work left on a 
 * native virtual processor.  Since we do not wish the underlying pthread
 * to interfere with the execution and scheduling of the other pthreads
 * we put the underlying pthread to wait with an AIX sigwait SVC.
 * <p>
 * When a thread blocked in native code is moved from a RVM Processor
 * to the native processor of this NativeIdleThread, it displaces the
 * NativeIdleThread as the activethread.  The RVM Processor becomes
 * the "returnAffinity" processor of the blocked thread.  The NativeIdleThread
 * is set to be the activeThread of that RVM processor, although
 * its processor register will still point back to the native processor.
 * The pthread_id's of the native and RVM processors are also swapped.
 * After exchanging the active threads & pthread_id's, the NativeIdleThread,
 * in sigWait is signaled. When it returns from sigwait, it resets it processor
 * register, and then yields itself back to its associated native processor,
 * leaving behind its osThread, to takeover execution of other Java threads
 * on that processor.
 * <p>
 * As part of this new "yield" the vp status of the native processor (previously
 * BLOCKED_IN_NATIVE is set to (unblocked) IN_NATIVE.  This will allow the 
 * "stuck in native" thread to attempt a return to Java.
 * <p>
 * When the blocked native thread attempts to reenter java, and finds itself
 * on a native processor, it yields itself back to its returnAffinity
 * processor, leaving behind its osThread.  The osThread, now executing in the
 * native processor, schedules the NativeIdleThread. After returning from yield
 * the NativeIdleThread places its native processor on the "available native
 * processor" queue (previously the DEAD_VP_QUEUE), and then reenters sigWait,
 * waiting for the cycle to start again. (now with a different osThread).
 */
class VM_NativeIdleThread extends VM_IdleThread {

  // when true identifies NativeIdleThreads for a native
  // processor created for AttachCurrentThread/CreateJavaVM
  private boolean forAttachJVM;  
    
  VM_NativeIdleThread (VM_Processor processorAffinity) {
    super ( processorAffinity );
    super.isNativeIdleThread = true;
    forAttachJVM = false;
    
    //-#if RVM_WITH_OSR
    super.isSystemThread = true;
    //-#endif
  }        

  VM_NativeIdleThread (VM_Processor processorAffinity, boolean asAttached) {
    super ( processorAffinity );
    super.isNativeIdleThread = true;
    forAttachJVM = asAttached;

    //-#if RVM_WITH_OSR
    super.isSystemThread = true;
    //-#endif
  }        

  public String toString() { // overrides VM_IdleThread
    return "VM_NativeIdleThread";
  }

  /**
   * run method for nativeIdleThread, run in one of two modes depending on the
   * type of processor where this thread is installed:
   *   -On a native processor that is intended for servicing other "stuck in C" processor
   *    this thread participates in the moving of works from the stuck processor.
   *   -On a native processor that is created for CreateJavaVM/AttachCurrentThread,
   *    this thread runs when the external pthread makes a JNI call and simply waits
   *    to yield when the call returns.
   */
  public void run() { // overrides VM_IdleThread
    if (forAttachJVM)
      run_ForAttachJVM();
    else
      run_ForNormalJava();
  }

  public void run_ForNormalJava() throws VM_PragmaNoOptCompile {
    // Make sure Opt compiler does not compile this method.  Although GC will scan
    // this run methods frame, fixing references, any references saved in register
    // save areas would not get reported, so we prevent OPT compilation.
    //

    // Save current frame pointer in threads JNIEnv, 
    // and set flag recognized by GC
    // which will cause this (NativeIdleThread) thread to have its stack scanned
    // starting at this frame, if it is in a sigwait syscall during a collection.
    //
    VM_Thread.getCurrentThread().jniEnv.JNITopJavaFP = VM_Magic.getFramePointer();

    // Get the Native Processor this NativeIdleThread is running on. 
    // It will always
    // be associated with the same Native Processor, although it will "visit" 
    // other RVM Processors.
    //
    VM_Processor myNativeProcessor =
      VM_ProcessorLocalState.getCurrentProcessor();

    while (true) {
      
      // At this point this Native Idle Thread should be executing on the
      // its associated NativeProcessor.  We either just entered
      // the run loop, or are starting again after the yield below.

      if (VM.VerifyAssertions)
	VM._assert( VM_Magic.objectAsAddress(VM_Processor.getCurrentProcessor()) ==
		   VM_Magic.objectAsAddress(myNativeProcessor) );

      // Get ID of osThread currently executing in the NativeProcessor, and this
      // NativeIdleThread, and remember it, it is ours for now!
      //
      int currentOSThreadId = myNativeProcessor.pthread_id;

      // Place Processor we are running on onto the AvailableNativeProcessorQueue
      //
      VM_Scheduler.nativeProcessorMutex.lock();
      VM_Scheduler.nativeProcessorQueue.enqueue(myNativeProcessor);
      VM_Scheduler.nativeProcessorMutex.unlock();

      // now suspend executing osThread in sigWait, and set status of this native
      // processor to "IN_SIGWAIT", a state expected by GC. (see VM_Handshake ??)
      //
      inSysWait = true;  // temporary...GC code looks for this flag in native idle threads

      VM_Address TOC = VM_Address.fromInt(0);
      //-#if RVM_FOR_POWERPC
      TOC = VM_BootRecord.the_boot_record.sysTOC;
      //-#endif
      VM_Magic.sysCallSigWait(VM_BootRecord.the_boot_record.sysPthreadSigWaitIP,
			      TOC,
		              myNativeProcessor.vpStatusAddress.toInt(),
			      VM_Processor.IN_SIGWAIT,
			      VM_Thread.getCurrentThread().contextRegisters);

      inSysWait = false;

      // Someone (watchdog daemon likely) has woken this 
      // NativeIdleThread up. Another Java thread,
      // blocked in native, should have displaced this thread 
      // as the active thread on 
      // the native processor. This idle thread should have 
      // been set to be the activeThread
      // of the processor, although the processor register 
      // (restored by AIX after the
      // sigWait) still points to the native processor.
      //

      VM_ProcessorLocalState.setCurrentProcessor(blockedProcessor);

      // change the status of the processor this thread is now running
      // on to IN_JAVA (from IN_SIGWAIT)
      //
      VM_Processor.vpStatus[blockedProcessor.vpStatusIndex] = VM_Processor.IN_JAVA;

      // Now executing as the active thread of that processor, yield back to my
      // associated NativeProcessor. This Idle Thread will go there to be reused later.
      // The osThread executing here will stay on the current processor, to take over
      // execution of Java threads on that processor.
      //
      // This "yield" to native processor will also unblock the native processor,
      // changing its vpStatus from BLOCKED_IN_NATIVE to IN_NATIVE, thus allowing
      // the "stuck in native" thread, previously moved to the native processor, to 
      // return from native, at which time it will find itself on a native processor
      // and transfer to a RVM processor (as in red-blue threading)
      //
      VM_Thread.yield(myNativeProcessor);

      // Waking up on the NativeProcessor, after the yield.
      //
      // The Java thread that was executing has reentered Java and transferred back to
      // its "returnAffinity" RVM Processor.  The osThread, left behind, has
      // scheduled and is executing this NativeIdleThread.
      //
      // We go to the top of the run loop, to make this <processor, idleThread, osThread>
      // triple available again, for some other Java thread found blocked in Native

    }  // while(true)

  }  // end of run

  public void run_ForAttachJVM() throws VM_PragmaNoOptCompile { // similar to run() for Red/Blue threads
    // Make sure Opt compiler does not compile this method.  Although GC will scan
    // this run methods frame, fixing references, any references saved in register
    // save areas would not get reported, so we prevent OPT compilation.
    //

    VM_Processor myNativeProcessor = VM_ProcessorLocalState.getCurrentProcessor();

    // save current frame pointer in threads JNIEnv, and 
    // set flag recognized by GC
    // which will cause this (NativeIdleThread) thread to have its stack scanned
    // starting at this frame
    //
    // no more - now sysCallSigWait sets ip & fp 
    // in context regs to start scanStack 
    // in this frame (if in sigwait at time of GC)
    //VM_Thread.getCurrentThread().jniEnv.JNITopJavaFP = 
    //VM_Magic.getFramePointer();

    while (true) {
      if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logIdleEvent();

      inSysWait = true;  // temporary...GC code looks for this flag in native idle threads

      VM_Address TOC = VM_Address.fromInt(0);
      //-#if RVM_FOR_POWERPC
      TOC = VM_BootRecord.the_boot_record.sysTOC;
      //-#endif
      VM_Magic.sysCallSigWait(VM_BootRecord.the_boot_record.sysPthreadSigWaitIP,
			      TOC,
		              myNativeProcessor.vpStatusAddress.toInt(),
			      VM_Processor.IN_SIGWAIT,
			      VM_Thread.getCurrentThread().contextRegisters);

      inSysWait = false;

      // if GC occurred while this thread was in sigwait, 
      // in which case the local variable
      // myProcessor has been relocated by GC, 
      // so reset the PR reg using that variable
      VM_ProcessorLocalState.setCurrentProcessor(myNativeProcessor);

      // Change the status of this native processor back to Java now that the thread is woken up
      VM_Processor.vpStatus[myNativeProcessor.vpStatusIndex] = VM_Processor.IN_JAVA;

      // There should be work in the transfer queue:  the Java thread returning to its C caller
      if (myNativeProcessor.transferQueue.atomicIsEmpty(myNativeProcessor.transferMutex)) {
	VM_Scheduler.trace("VM_NativeIdleThread:","after sigWait Transfer Queue Empty: myProcessor.id =",
			   myNativeProcessor.id);
	VM_Scheduler.trace("VM_NativeIdleThread:","myProcessor->dumpProcessorState...");
	myNativeProcessor.dumpProcessorState();
	VM._assert(false);
      } 

      // after the above wait is satisfied ; there is work on the transfer queue- so do a yield
      VM_Thread.yield(myNativeProcessor.idleQueue); // Put ourself back on idle queue.

    }

  }

  // blocked processor whose pthread & java thread have
  // been moved to a NativeProcessor & which now needs a new pthread
  VM_Processor  blockedProcessor;

  boolean inSysWait;  // this field is tested by GC

}
