/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Manages scheduling of collector threads for the reference counting
 * collector (currently under development).
 * <p>
 * Extends VM_Handshake to override initiateCollection & requestAndAwaitCompletion.
 * <p>
 * NOT INCLUDED IN ANY CURRENT BUILDS
 *
 * @author David Bacon
 * @author Stephen Smith
 */
class VM_RCHandshake extends VM_Handshake implements VM_Uninterruptible
{
    private static final boolean trace = false;
    //    private boolean requestFlag;
    //    private boolean completionFlag;
   
    private void
    initiateCollection()
    {
	if (VM.VerifyAssertions && !VM.BuildForConcurrentGC) VM.assert(VM.NOT_REACHED);

	VM_Scheduler.collectorMutex.lock();

	// no need for atomic update since this thread is the only one that can reach here
	VM_Scheduler.globalEpoch++;
	if (VM_Scheduler.globalEpoch > VM_Scheduler.EPOCH_MAX) {
	    VM_Scheduler.globalEpoch = VM_Scheduler.globalEpoch % VM_RCBuffers.MAX_INCDECBUFFER_COUNT;
	}
	
	// collector threads in the collector queue should always be ordered by processor id
	while (VM_Scheduler.collectorQueue.isEmpty()) {
	    VM_Scheduler.collectorMutex.unlock();
	    if (trace) VM_Scheduler.trace("VM_RCHandshake", "mutator: waiting for previous collection to finish");
	    VM.sysWrite("\n\n*|*|*|* COLLECTOR QUEUE EMPTY *********\n\n");
	    VM_Thread.getCurrentThread().yield();
	    VM_Scheduler.collectorMutex.lock();
	}

	// start a new collection cycle: wake up the collector thread on the first processor
	VM_Scheduler.collectorQueue.dequeue(VM_Scheduler.PRIMORDIAL_PROCESSOR_ID).scheduleHighPriority();

	VM_Scheduler.collectorMutex.unlock();
    }

    // Mutator: request a gc cycle and wait for it to complete.
    //
    void
    requestAndAwaitCompletion()
    {
	synchronized (this)
	    {
		VM_Allocator.gc_collect_now = false;
		if (this.completionFlag)
		    {
			if (trace) VM_Scheduler.trace("VM_RCHandshake", "mutator: no need for collection (already completed)");
			return;
		    }
		
		if (this.requestFlag)
		    {
			if (trace) VM_Scheduler.trace("VM_RCHandshake", "mutator: no need to initiate collection (request already in progress)");
			return;
		    }
		
		if (VM_Allocator.gcInProgress)
		    {
			if (trace) VM_Scheduler.trace("VM_RCHandshake", "mutator: no need to initiate collection (gc already in progress)");
			return;
		    }
		
		if (trace) VM_Scheduler.trace("VM_RCHandshake", "mutator: initiating collection");
		if (VM_CollectorThread.MEASURE_RENDEZVOUS_TIMES) VM_CollectorThread.gcBarrier.rendezvousStartTime = VM_Time.now();
		this.requestFlag = true;
		VM_Allocator.gcInProgress = true;
		initiateCollection();
	    }
	
	// allow a gc thread to run
	//
	if (VM_Processor.getCurrentProcessor().id == VM_Scheduler.PRIMORDIAL_PROCESSOR_ID)
	    {
		VM_Thread.getCurrentThread().yield();
	    }
	else 
	    {
		VM.sysVirtualProcessorYield();
	    }
    }

    // notifyCompletion() is unmodified from superclass VM_Handshake

}
