/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * A synchronization barrier used to synchronize collector threads,
 * and the VM_Processors they are running on, during parallel collections.
 *
 * @author   Derek Lieber
 * @modified Steve Smith
 */
final class VM_GCSynchronizationBarrier implements VM_Uninterruptible {

  private static final boolean trace = false;  // emit trace messages? (all rendezvous)
  private static final boolean trace_startup = false;  // emit debugging messages? (startupRendezvous)
  private static final boolean trace_unusual = false;  // emit debugging messages? 

  /** maximum processor id for rendezvous, sometimes includes the native daemon processor */
  private int   maxProcessorId;

  /** number of physical processors on running computer */
  private int   numRealProcessors;

  /** number of times i-th processor has entered barrier */
  private int[] entryCounts;

  /**
   * Constructor
   */
  VM_GCSynchronizationBarrier () {
    // initialize numRealProcessors to 1. Will be set to actual value later.
    // Using without resetting will cause waitABit() to yield instead of spinning
    numRealProcessors = 1;
    entryCounts = new int[1 + VM_Scheduler.MAX_PROCESSORS]; // 0-th slot unused
  }

  /**
   * Wait for all other collectorThreads/processors to arrive at this barrier.
   */
  void rendezvous () {
    int myProcessorId = VM_Processor.getCurrentProcessorId();
    int myCount       = entryCounts[myProcessorId] + 1;

    // enter barrier
    //
    if (trace) VM_Scheduler.trace("VM_ProcessorSynchronizationBarrier", "rendezvous: enter ", myCount);
    entryCounts[myProcessorId] = myCount;
    VM_Magic.sync(); // update main memory so other processors will see it in "while" loop, below

    // wait for other processors to catch up.
    //
    for (int i = 1; i <= maxProcessorId; ++i) {
      if ( entryCounts[i] < 0 )  
	continue;               // skip non participating VP
      if (i == myProcessorId)
	continue;
      while (entryCounts[i] < myCount) {
	// yield virtual processor's time slice (more polite to o/s than spinning)
	//
	// VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
	// VM.sysCall0(bootRecord.sysVirtualProcessorYieldIP);
	//
	// ...put original spinning code back in, in place of above, since this
	// is only being used by parallel GC threads, running on fewer that all
	// available processors.
	//
	waitABit(1);
      }
    }

    VM_Magic.isync(); // so subsequent instructions won't see stale values

    // leave barrier
    //
    if (trace) VM_Scheduler.trace("VM_ProcessorSynchronizationBarrier", "rendezvous: leave ", myCount);
  }

  /**
   * Coments are for default implementation of jni (not the alternative implemenation)
   * <p>
   * First rendezvous for a collection, called by all CollectorThreads that arrive
   * to participate in a collection.  Thread with gcOrdinal==1 is responsible for
   * detecting RVM processors stuck in Native C, blocking them in Native, and making
   * them non-participants in the collection (by setting their counters to -1).
   * Other arriving collector threads just wait until all have either arrived or
   * been declared non-participating.
   */
  void  startupRendezvous () {
    int myProcessorId = VM_Processor.getCurrentProcessorId();
    int myNumber = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread()).getGCOrdinal();
    int numExcluded = 0;  // number of RVM VPs NOT participating

    if (VM.VerifyAssertions) {
      if (entryCounts[myProcessorId]!=0) {
	VM_Scheduler.trace("startupRendezvous:", "on entry entryCount =",entryCounts[myProcessorId]);
	VM.assert(entryCounts[myProcessorId]==0);
      }
    }
    if ( myNumber > 1 ) {
      // enter barrier
      //
      entryCounts[myProcessorId] = 1;
      VM_Magic.sync(); // update main memory so other processors will see it in "while" loop, below

      // wait for "normal" other processors to show up, or be made non-participating
      // ie. for their counts to go to 1 or -1 respectfully
      //
      for (int i = 1; i <= VM_Scheduler.numProcessors; ++i) {
	while (entryCounts[i] == 0)
	  waitABit(1);
      }

      //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
      // alternate implementation of jni
      //-#else
      // default implementation of jni:
      // now also wait for native daemon processor to be excluded (done so by
      // ordinal=1 thread in code below) or to arrive.
      while (entryCounts[VM_Scheduler.nativeDPndx] == 0)
	waitABit(1);
      //-#endif

      VM_Magic.isync();   // so subsequent instructions won't see stale values
      if (trace_startup) VM_Scheduler.trace("startupRendezvous:", "leaving - my count =",
				    entryCounts[myProcessorId]);
      return;             // leave barrier
    }

    // Thread with gcOrdinal==1 must detect processors whose active threads are
    // stuck in Native C, block them there, and make them non-participants
    //
    for (int i = 1; i <= VM_Scheduler.numProcessors; ++i) {
      if ( i == myProcessorId ) continue;  // my count not yet set
      while (entryCounts[i] == 0) {
	waitABit(1);     // give missing thread a chance to show up

	//-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
	// alternate implementation of jni
	//-#else
	// default implementation of jni:
	// if missing thread/processor is in native, block it there,
	// and set count to -1 to indicate a non-participant for this GC
	//
	if ( VM_Scheduler.processors[i].lockInCIfInC() ) {
	  numExcluded++;
	  if (trace_unusual) VM_Scheduler.trace("startupRendezvous:","TAKING OUT processor",i);
	  removeProcessor( i );
	}
	//-#endif
      }
      if (trace_startup) {
	if (entryCounts[i] == 1)
	  VM_Scheduler.trace("startupRendezvous:","processor INCLUDED - id =",i);
	else
	  VM_Scheduler.trace("startupRendezvous:","processor EXCLUDED - id =",i);
      }
    }

    //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
    // alternate implementation of jni:
    // no NativeDaemonProcessor, rendezvous loops are just over normal VM_Processors
    maxProcessorId = VM_Scheduler.numProcessors;
    //-#else
    // default implementation of jni
    if (trace_startup) VM_Scheduler.trace("startupRendezvous","numExcluded =",numExcluded);
    if ( !VM.BuildForSingleVirtualProcessor && VM_Scheduler.processors[VM_Scheduler.nativeDPndx]!=null) {
      // If it is the NativeProcessor (its collector thread) that is executing here
      // just fall thru to end, to set our entryCount, sync & return. Else, decide
      // what to do with the NativeProcessor
      if ( myProcessorId == VM_Scheduler.nativeDPndx ) {
	if (trace_startup) VM_Scheduler.trace("startupRendezvous:","NativeProcessor has ordinal 1, will participate");
      }
      else {
	if (trace_startup) VM_Scheduler.trace("startupRendezvous:","checking NativeDaemonProcessor");
	int loopCount = 1;
	VM_Processor ndvp = VM_Scheduler.processors[VM_Scheduler.nativeDPndx];
	while (entryCounts[VM_Scheduler.nativeDPndx] == 0) {
	  if ( VM_Scheduler.processors[VM_Scheduler.nativeDPndx].blockInWaitIfInWait() ) {
	    numExcluded++;
	    if (trace_startup)
	      VM_Scheduler.trace("startupRendezvous:","TAKING OUT NATIVE DAEMON PROCESSOR");
	    removeProcessor( VM_Scheduler.nativeDPndx );
	  }
	  waitABit(1);     // give missing thread a chance to show up

	  if (trace_startup) {
	    loopCount++;
	    if (loopCount%100 == 0)
	      VM_Scheduler.trace("startupRendezvous:","in native processor loop - loopCount =",loopCount);
	  }
	}

	// native processor is now either Participating or blocked in sigwait
	
	if (trace_unusual || trace_startup) {
	  if ( entryCounts[VM_Scheduler.nativeDPndx] == 1 )
	    VM_Scheduler.trace("startupRendezvous:","NativeDaemonProcessor IN");
	  else 
	    VM_Scheduler.trace("startupRendezvous:","NativeDaemonProcessor OUT");
	  VM_Scheduler.trace("startupRendezvous:","native processor status =",
			     VM_Processor.vpStatus[ndvp.vpStatusIndex]);
	}
      }
      // include NativeDaemonProcessor in set of processors to rendezvous
      maxProcessorId = VM_Scheduler.numProcessors + 1;
    }  // !VM.BuildForSingleVirtualProcessor

    else {
      // compiled ForSingleProcessor: there is no nativeDaemonProcessor 
      if (trace_startup) VM_Scheduler.trace("startupRendezvous:", "forcing native daemon count to -1");
      entryCounts[VM_Scheduler.nativeDPndx] = -1; 
      maxProcessorId = VM_Scheduler.numProcessors;
    }

    //-#endif

    // after deciding whos in and whos out, ordinal 1 thread enters barrier by
    // setting its count to 1 (maybe this can be done earlier so others can
    // get through the count loop quicker)
    //
    entryCounts[myProcessorId] = 1;
    VM_Magic.sync();   // update main memory so other processors will see it in "while" loop
    VM_Magic.isync();  // so subsequent instructions won't see stale values
    if (trace_startup) VM_Scheduler.trace("startupRendezvous:", "leaving - my count =",
				  entryCounts[myProcessorId]);
    return;            // leave barrier
  }  // startupRendezvous

  /**
   * reset the rendezvous counters for all VPs to 0.
   * Also sets numRealProcessors to number of real CPUs.
   */
  void resetRendezvous () {

    for ( int i = 1; i <= VM_Scheduler.numProcessors; i++)
      entryCounts[i] = 0;

    //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
    // alternate implementation of jni
    //-#else
    // default implementation of jni
    entryCounts[VM_Scheduler.nativeDPndx] = 0;
    //-#endif

    if ( ! VM.BuildForSingleVirtualProcessor) {
      // Set number of Real processors on the running computer. This will allow
      // waitABit() to spin when running with fewer VM_Procssors than real processors
      numRealProcessors = VM.sysCall0(VM_BootRecord.the_boot_record.sysNumProcessorsIP);
    }
    VM_Magic.sync();      // make other threads/processors see the update
  }

  /**
   * method to give a waiting thread/processor something do without interferring
   * with other waiting threads or those trying to enter the rendezvous.
   * Spins if running with fewer RVM "processors" than physical processors.
   * Yields (to Operating System) if running with more "processors" than
   * real processors.
   *
   * @param x amount to spin in some unknown units
   */
  private int waitABit ( int x ) {
    int sum = 0;
    if (VM_Scheduler.numProcessors < numRealProcessors) {
      // spin for a while, keeping the operating system thread
      for ( int i = 0; i < (x*100); i++)
	sum = sum + i;
      return sum;
    }
    else {
      // yield executing operating system thread back to the operating system
      VM.sysCall0(VM_BootRecord.the_boot_record.sysVirtualProcessorYieldIP);
      return 0;
    }
  }

  /**
   * remove a processor from the rendezvous for the current collection.
   * The removed processor in considered a "non-participant" for the collection.
   *
   * @param id  processor id of processor to be removed.
   */
  private void
  removeProcessor( int id ) {

    VM_Processor vp = VM_Scheduler.processors[id];

    // get processors collector thread off its transfer queue
    vp.transferMutex.lock();
    VM_Thread ct = vp.transferQueue.dequeueGCThread(null);
    vp.transferMutex.unlock();
    if (VM.VerifyAssertions) {
      if (ct==null) {
	VM_Scheduler.trace("removeProcessor", "no collector thread - dumping vp");
	vp.dumpProcessorState();
      }
      VM.assert(ct.isGCThread == true);
    }

    // put it back on the global collector thread queue
    VM_Scheduler.collectorMutex.lock();
    VM_Scheduler.collectorQueue.enqueue(ct);
    VM_Scheduler.collectorMutex.unlock();

    // now set the counts field for the native daemon processor to -1,
    // to indicate it is not participating, which will let the other
    // participants (in above loop) complete and leave the rendezvous.
    entryCounts[id] = -1;

    // set VPs CollectorThread ordinal number negative to indicate not participating
    VM_CollectorThread.collectorThreads[id].gcOrdinal = -1;

    VM_Magic.sync();      // make other threads/processors see the update

  }  // removeProcessor

}
