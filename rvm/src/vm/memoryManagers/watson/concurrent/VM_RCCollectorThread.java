/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * extends VM_CollectorThread to override the run method
 *
 * @see VM_RCHandshake
 *
 * @author David Bacon
 * @author Stephen Smith
 */ 
class VM_RCCollectorThread extends VM_CollectorThread
  implements VM_Uninterruptible, VM_GCConstants {

  private final static boolean trace = false; // emit trace messages?

  final static boolean GC_ALL_TOGETHER = true;    // For RefcountGC, do all processing on last thread?
  final static boolean TIME_PAUSES  = true;       // For RefcountGC, collect data on pause times
  final static boolean PRINT_PAUSES = false;      // For RefcountGC, print as we go?
  
  static double pauseTimeTotal;
  static double pauseTimeMax;
  static double collectTimeTotal;
  static double collectTimeMax;
  
  static int pauseCount;
  static int collectCount;
  
  /**
   * Run method for reference counting collector thread (one per VM_Processor).
   * Enters an infinite loop, waiting for collections to be requested,
   * performing those collections, and then waiting again.
   */
  public void run() {

    //  make sure Opt compiler does not compile this method
    //  references stored in registers by the opt compiler will not be relocated by GC
    VM_Magic.pragmaNoOptCompile();
    
    while (true)
      {
	// suspend this thread: it will resume when scheduled by VM_Handshake.initiateCollection()
	//
	VM_Scheduler.collectorMutex.lock();
	VM_Thread.getCurrentThread().yield(VM_Scheduler.collectorQueue, VM_Scheduler.collectorMutex);
	
	// When we wake up here, we have been tapped on the shoulder to perform the next collection
	
	VM_Processor p = VM_Processor.getCurrentProcessor();
	p.disableThreadSwitching(); // block mutators
	
	double startTime;
	if (TIME_PAUSES) startTime = VM_Time.now();
	
	p.localEpoch = VM_Scheduler.globalEpoch;
	
	if (VM.VerifyAssertions) VM.assert(isActive);
	
	if (trace) VM_Scheduler.trace("VM_RCCollectorThread", "starting collection");
	
	VM_Allocator.collect();     // PERFORM GARBAGE COLLECTION TASKS
	
	if (trace) VM_Scheduler.trace("VM_RCCollectorThread", "finished collection");
	
	if (TIME_PAUSES) {
	  double pauseTime = VM_Time.now() - startTime;
	  if (VM_Scheduler.numProcessors > 1 && p.id != VM_Scheduler.numProcessors) {
	    pauseTimeTotal += pauseTime;
	    if (pauseTime > pauseTimeMax) pauseTimeMax = pauseTime;
	    pauseCount++;
	  }
	  else {
	    collectTimeTotal += pauseTime;
	    if (pauseTime > collectTimeMax) collectTimeMax = pauseTime;
	    collectCount++;
	  }
	  if (PRINT_PAUSES) {
	    VM.sysWrite("||||  Pause time for CPU ");  VM.sysWrite(p.id, false);
	    VM.sysWrite(" epoch ");  		         VM.sysWrite(p.localEpoch, false);
	    VM.sysWrite(": ");  			 VM.sysWrite((int)(pauseTime*1000000.0), false);
	    VM.sysWrite(" usec\n");
	  }
	}
	
	// wake up the collector thread on next processor (if there is any)
	// and yield so that mutator can run on this processor
	//
	if (p.id == VM_Scheduler.numProcessors)   // this proc is the last proc
	  {
	    collectionCount += 1;
	    while (!VM_Scheduler.gcWaitQueue.isEmpty()) {
	      VM_Thread t = VM_Scheduler.gcWaitQueue.dequeue();
	      if (t.processorAffinity != null)
		t.processorAffinity.scheduleThread(t); // should this be transferThread???
	      else {
		t.dump();
		VM.assert(false);
	      }
	    }
	    collect.notifyCompletion();        // notify mutators waiting on previous handshake object
	    collect = new VM_RCHandshake();    // replace handshake with new one for next collection
	    VM_Allocator.gcInProgress = false;
	    if (trace) VM_Scheduler.trace("VM_RCCollectorThread", "ending last gc ");
	  }
	else 
	  {
	    VM_Scheduler.collectorMutex.lock();
	    VM_Scheduler.processors[p.id + 1].scheduleThread(VM_Scheduler.collectorQueue.dequeue(p.id + 1));
	    VM_Scheduler.collectorMutex.unlock();
	    if (trace) VM_Scheduler.trace("VM_RCCollectorThread", "woke up the next proc");
	  }
	
	VM_Processor.getCurrentProcessor().enableThreadSwitching();  // resume normal scheduling
      }
    
  }  // run
  
  // RCGC Statistics
  
  static void printRCStatistics (int freed) {
    VM.sysWrite("Collections:  ");  VM.sysWrite(collectCount, false);
    VM.sysWrite("\nTotal Collection Time: ");  printUsecs(collectTimeTotal);
    VM.sysWrite("\nMax Collection Time:   ");  printUsecs(collectTimeMax);
    VM.sysWrite("\nAvg Collection Time:   ");  printUsecs(collectTimeTotal/collectCount);
    VM.sysWrite("\nFree/Object:           ");  printUsecs(collectTimeTotal/((double) freed));
    VM.sysWrite("\nPauses: ");               VM.sysWrite(pauseCount, false);
    VM.sysWrite("\nTotal Pause Time:      ");       printUsecs(pauseTimeTotal);
    VM.sysWrite("\nMax Pause Time:        ");       printUsecs(pauseTimeMax);
    VM.sysWrite("\nAvg Pause Time:        ");       printUsecs(pauseTimeTotal/pauseCount);
    VM.sysWrite("\n");
    
    if (VM.VerifyAssertions) 
      VM.sysWrite("VerifyAssertions ON!  Timings will be affected\n"); 
  }

  private static void printUsecs(double t) {
    VM.sysWrite((int)(t*1000000.0), false);
    VM.sysWrite(" usecs");
  }
  
  // buffers used for reference counting
  int[] incDecBuffers;
  int[] incDecBuffersTops;
  int[] incDecBuffersMaxs;
  
  VM_RCCollectorThread(int[] stack, boolean isActive, VM_Processor processorAffinity) {
    super(stack, isActive, processorAffinity);

    incDecBuffers     = new int[VM_RCBuffers.MAX_INCDECBUFFER_COUNT];
    incDecBuffersTops = new int[VM_RCBuffers.MAX_INCDECBUFFER_COUNT];
    incDecBuffersMaxs = new int[VM_RCBuffers.MAX_INCDECBUFFER_COUNT];
  }
  

}
