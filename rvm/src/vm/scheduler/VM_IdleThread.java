/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Low priority thread to run when there's nothing else to do.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
class VM_IdleThread extends VM_Thread {

  /**
   * Attempt rudimentary load balancing.  If a virtual processor
   * has no work it asks for some then spins for time waiting for
   * an runnable thread to arrive.  If none does, or if this variable
   * is false, the remaining time-slice is returned to the operating
   * system.
   *
   * @see "preprocessor directive RVM_WITHOUT_LOAD_BALANCING"
   */
//-#if RVM_WITHOUT_LOAD_BALANCING
  static final boolean loadBalancing = false;
//-#else
  static final boolean loadBalancing = !VM.BuildForSingleVirtualProcessor;
//-#endif

  /**
   * Time to wait for work if loadBalancing is set.
   */
  static final double SPIN_TIME = (loadBalancing? 0.001 : 0.0);

  /**
   * A thread to run if there is no other work for a virtual processor.
   */
  VM_IdleThread(VM_Processor processorAffinity) {
    makeDaemon(true);
    super.isIdleThread = true;
    super.processorAffinity = processorAffinity;
  }

  public String toString() { // overrides VM_Thread
    return "VM_IdleThread";
  }

  public void run() { // overrides VM_Thread
    VM_Processor myProcessor = VM_Processor.getCurrentProcessor();
    if (VM.VerifyAssertions) VM._assert(myProcessor.processorMode != VM_Processor.NATIVEDAEMON);
   main: while (true) {
      if (VM_Scheduler.terminated) VM_Thread.terminate();
      double t=VM_Time.now()+SPIN_TIME;

      if (VM_Scheduler.debugRequested) {
	System.err.println("debug requested in idle thread");
	VM_Scheduler.debugRequested = false;
      }
      
      do {
	VM_Processor.idleProcessor = myProcessor;
	if (availableWork(myProcessor)) {
	  VM_Thread.yield(VM_Processor.getCurrentProcessor().idleQueue);
	  continue main;
	}
      } while (VM_Time.now()<t);
      
      VM.sysVirtualProcessorYield();
   }
  }

  /*
   * @return true, if their appears to be a runnable thread for the processor to execute
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
