/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Low priority thread to run when there's nothing else to do.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
class VM_IdleThread extends VM_Thread {

  // time in seconds to spin 
  //-#if RVM_WITH_LOUSY_LOAD_BALANCING
  static final double SPIN_TIME = (VM.BuildforConcurentGC ? 0.001 : 0);
  //-#else
  static final double SPIN_TIME = 0.001;
  //-#endif

  VM_IdleThread(VM_Processor processorAffinity) {
    makeDaemon(true);
    super.isIdleThread = true;
    super.processorAffinity = processorAffinity;
  }

  public String
  toString() // overrides VM_Thread
     {
     return "VM_IdleThread";
     }

  public void run() { // overrides VM_Thread
    while (true) {
      
      if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logIdleEvent();

      if (VM_Scheduler.terminated) VM_Thread.terminate();
      
      // flag -  the main will will either yield to the idle queue, and most
      //         likely loop back, or
      //	 yield the time slice to other processors.
      //  idleQueueYield means yield to the idle queue

      VM_Processor myProcessor = VM_Processor.getCurrentProcessor();

      if (!VM.BuildForDedicatedNativeProcessors && myProcessor.processorMode != VM_Processor.NATIVEDAEMON) {
        // Awaken threads whose sleep time has expired.
        //
        if ( VM_Processor.idleProcessor == null      // nobody else has asked for work
	     && !availableWork(myProcessor)
             && VM_Scheduler.numProcessors > 1
             && ( !VM.BuildForConcurrentGC
	 	  ||  (VM_Processor.getCurrentProcessor().id != VM_Scheduler.numProcessors) ) ) { 
            // Tell load balancer that this processor needs work.
	    // VM_Scheduler.trace("VM_IdleThread", "asking for work");
	    VM_Processor.idleProcessor = VM_Processor.getCurrentProcessor();
	  }
      }

      for (double t = VM_Time.now() + SPIN_TIME; VM_Time.now() < t; ) 
        if (availableWork(myProcessor)) break;
      
      if (availableWork(myProcessor))	VM_Thread.yield(VM_Processor.getCurrentProcessor().idleQueue);
      else				VM.sysVirtualProcessorYield();
    }
  }

  /*
   * @returns true, if the processor has a runnable thread
   */
  private static boolean availableWork ( VM_Processor p ) {

    if (!p.readyQueue.isEmpty())	return true;
    VM_Magic.isync();
    if (!p.transferQueue.isEmpty())	return true;
    if (p.ioQueue.isReady())		return true;

    if (VM_Scheduler.wakeupQueue.isReady()) {
            VM_Scheduler.wakeupMutex.lock();
            VM_Thread t = VM_Scheduler.wakeupQueue.dequeue();
            VM_Scheduler.wakeupMutex.unlock();
            if (t != null) {
        	t.schedule();
		return true;
            }
    }

    return false;
  }
}
