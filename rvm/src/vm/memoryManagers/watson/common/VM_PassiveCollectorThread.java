/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * A collector thread which does not participate in GC, useful for
 * <code>VM_Processor</code>s which should not participate in GC.
 *
 * @author Manu Sridharan
 * @author Tony Cocchi
 * @author Stephen Smith
 */
class VM_PassiveCollectorThread extends VM_Thread implements VM_Uninterruptible {
  
  private final static boolean trace = false; // emit trace messages?
  
  // Run a collector thread (one per VM_Processor).
  //
  public void
    run() {  //- overrides VM_Thread
    
    // myProcessor local variable will be relocated by GC if Processor object
    // is moved, and is used to reset Processor register after sigWait (after GC)
    VM_Processor myProcessor = VM_ProcessorLocalState.getCurrentProcessor();
    
    //  make sure Opt compiler does not compile this method
    //  references stored in registers by the opt compiler will not 
    //  be relocated by GC
    VM_Magic.pragmaNoOptCompile();
    
    // save current frame pointer in threads JNIEnv
    // passive collector threads will have their stacks scanned
    // starting at this frame
    // ...this should not be needed, with use of sysCallSigWait  08/23/01 SES
    VM_Thread.getCurrentThread().jniEnv.JNITopJavaFP = VM_Magic.getFramePointer();

    // remember status word index for executing processor. It will remain valid
    // even if GC moves the processor object
    int processorStatusIndex = VM_Processor.getCurrentProcessor().vpStatusIndex;
    
    
    while (true)
      {
	// suspend this thread: it will resume when scheduled by VM_Handshake.initiateCollection()
        VM_Scheduler.passiveCollectorMutex.lock();
	VM_Thread.getCurrentThread().yield(VM_Scheduler.passiveCollectorQueue, VM_Scheduler.passiveCollectorMutex);
	
	myProcessor.disableThreadSwitching(); // block mutators
	
	if (trace) {
	  VM_Scheduler.trace("VM_PassiveCollectorThread", "waking up - setting vpStatus -> IN_SIGWAIT");
	  VM_Scheduler.trace("VM_PassiveCollectorThread", "waking up - about to sigWait");
	}

	/***  old code - now replaced with use of sysCallSigWait - removed 08/23/01 SES
	VM_Thread.getCurrentThread().contextRegisters.setInnermost();
      
	// CHANGE FOLLOWING TO USE MAGIC DIRECTLY - to avoid a method call
	// ie another frame & prolog & epilog code that may use PROCESSOR_REG

	// OS wait for a signal from VM_CollectorThread (on a RVM processor).
        VM.sysCall2(VM_BootRecord.the_boot_record.sysPthreadSigWaitIP,
		  myProcessor.vpStatusAddress, VM_Processor.IN_SIGWAIT);
		  
	***/

	int TOC = 0;
	//-#if RVM_FOR_POWERPC
	TOC = VM_BootRecord.the_boot_record.sysTOC;
	//-#endif
	VM_Magic.sysCallSigWait(VM_BootRecord.the_boot_record.sysPthreadSigWaitIP,
				TOC,
				myProcessor.vpStatusAddress,
				VM_Processor.IN_SIGWAIT,
				VM_Thread.getCurrentThread().contextRegisters);

        // received signal and no longer in SIGWAIT
        // set status appropriately
	VM_Processor.vpStatus[processorStatusIndex] = VM_Processor.IN_JAVA;
        
	// GC has completed while this thread was in sigwait, 
        // in which case the local variable
	// myProcessor has been relocated by GC, 
        // so reset the PR reg using that variable
        VM_ProcessorLocalState.setCurrentProcessor(myProcessor);
	
	myProcessor.enableThreadSwitching();  // resume normal scheduling
	
      }  // end of while(true) loop
    
  }  // run
  
  
  // Make a native collector thread that will not participate in gc.
  // Taken:    stack to run on
  //           processor to run on
  // Returned: native collector
  //
  static VM_PassiveCollectorThread 
    createPassiveCollectorThread(int[] stack, VM_Processor processorAffinity) {
    return new VM_PassiveCollectorThread(stack, processorAffinity);
  }
  
  
  private 
    VM_PassiveCollectorThread(int[] stack,  VM_Processor processorAffinity) {
    super(stack);
    makeDaemon(true); // this is redundant, but harmless
    // not an instance of VM_CollectorThread even though it is
    // a GC thread
    //this.isGCThread        = true;
    this.isPassiveCollectorThread = true;
    this.processorAffinity = processorAffinity;
  }
}
