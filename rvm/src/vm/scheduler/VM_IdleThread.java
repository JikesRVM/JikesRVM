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
      boolean rvmYield = false;
      if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logIdleEvent();
      VM_Processor myProcessor = VM_Processor.getCurrentProcessor(); // Tell load balancer that this processor needs work.
      if (VM_Scheduler.terminated) // the VM is terminating, exit idle thread
        VM_Thread.terminate();
      
      if (!VM.BuildForDedicatedNativeProcessors && myProcessor.processorMode != VM_Processor.NATIVEDAEMON) {
        if (myProcessor.readyQueue.isEmpty() &&      // no more local work to do
            myProcessor.transferQueue.isEmpty() &&   // no incoming work to do
            VM_Processor.idleProcessor == null)      // nobody else has asked for work
	  { 
	    // ask for somebody to put work onto our transfer queue
	    // VM_Scheduler.trace("VM_IdleThread", "asking for work");
	    if (! VM.BuildForConcurrentGC) {
	      VM_Processor.idleProcessor = VM_Processor.getCurrentProcessor();
	    } else if (VM_Scheduler.numProcessors > 1 && 
		       VM_Processor.getCurrentProcessor().id != VM_Scheduler.numProcessors) {
	      VM_Processor.idleProcessor = VM_Processor.getCurrentProcessor();
	    }
	  }
        
        // Awaken threads whose sleep time has expired.
        //
        if (VM_Scheduler.wakeupQueue.isReady()) {
            VM_Scheduler.wakeupMutex.lock();
            VM_Thread t = VM_Scheduler.wakeupQueue.dequeue();
            VM_Scheduler.wakeupMutex.unlock();
            if (t != null) {
        	rvmYield = true;
        	t.schedule();
            }
        }
      }

      if (VM.BuildForConcurrentGC) {
	// Temporary: prevent mutation buffer from being flooded by scheduler ops by spinning 
	final double SPIN_TIME = 0.001;		    // time in seconds to spin 
	for (double t = VM_Time.now() + SPIN_TIME; VM_Time.now() < t; ) {
	  if ((!myProcessor.readyQueue.isEmpty()) || myProcessor.ioQueue.isReady() || (!myProcessor.transferQueue.isEmpty()))
	    break;
	}
      }

      if (!myProcessor.readyQueue.isEmpty()    || // no more local work to do
          !myProcessor.transferQueue.isEmpty() || // no work yet transferred form other virtual processors
	  !myProcessor.ioQueue.isEmpty())         // no work waiting on io is ready to process
	 rvmYield = true;

      // Put ourself back on idle queue.
      //
      if (rvmYield) 
	VM_Thread.yield(VM_Processor.getCurrentProcessor().idleQueue);
      else 
	VM.sysVirtualProcessorYield();
    }
  }
}
