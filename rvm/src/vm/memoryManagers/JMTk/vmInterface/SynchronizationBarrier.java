/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.vmInterface;

import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * A synchronization barrier used to synchronize collector threads,
 * and the VM_Processors they are running on, during parallel collections.
 *
 * @author   Derek Lieber
 * @modified Steve Smith
 * @modified Perry Cheng
 */
public final class SynchronizationBarrier {

  private static final boolean trace         = false;  // emit trace messages? (all rendezvous)
  private static final boolean trace_startup = false;  // emit debugging messages? (startupRendezvous)
  private static final boolean trace_unusual = false;  // emit debugging messages? 

  /** maximum processor id for rendezvous, sometimes includes the native daemon processor */
  private int   maxProcessorId;

  /** number of physical processors on running computer */
  private int   numRealProcessors;

  /** number of times i-th processor has entered barrier */
  private int[] entryCounts;

  /** measure rendezvous times - outer index is processor id - inner index is which rendezvous point */
  public static double rendezvousStartTime;
  public static int rendezvousIn[][] = null;   
  public static int rendezvousOut[][] = null;
  public static int rendezvousCount[] = null;  // indicates which rendezvous a processor is at

  /**
   * Constructor
   */
  public SynchronizationBarrier () throws VM_PragmaUninterruptible {
    // initialize numRealProcessors to 1. Will be set to actual value later.
    // Using without resetting will cause waitABit() to yield instead of spinning
    numRealProcessors = 1;
    entryCounts     =  new int[1 + VM_Scheduler.MAX_PROCESSORS]; // 0-th slot unused
    // No one will probably do more than 15 rendezvous without a reset
    rendezvousIn    =  new int[1 + VM_Scheduler.MAX_PROCESSORS][15];
    rendezvousOut   =  new int[1 + VM_Scheduler.MAX_PROCESSORS][15];
    rendezvousCount =  new int[1 + VM_Scheduler.MAX_PROCESSORS];
  }

  /**
   * Wait for all other collectorThreads/processors to arrive at this barrier.
   */
  public int rendezvous () throws VM_PragmaUninterruptible {
    return rendezvous(false);
  }

  public int rendezvous (boolean time) throws VM_PragmaUninterruptible {

    double start = time ? VM_Time.now() : 0.0;
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
	// VM_Interface.lowYield();
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
    if (time) rendezvousRecord(start, VM_Time.now());

    // XXX This should be changed to return ordinal of current rendezvous rather than the one at the beginning
    return VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread()).getGCOrdinal();
  }

  public double rendezvousRecord(double start, double end) throws VM_PragmaUninterruptible {
    int myProcessorId = VM_Processor.getCurrentProcessorId();
    int which = rendezvousCount[myProcessorId]++;
    VM._assert(which < rendezvousIn[0].length);
    rendezvousIn[myProcessorId][which]  = (int)((start - rendezvousStartTime)*1000000);
    rendezvousOut[myProcessorId][which] = (int)((end - rendezvousStartTime)*1000000);
    return end - start;
  }

  public static void printRendezvousTimes() throws VM_PragmaUninterruptible {

    VM.sysWriteln("**** Rendezvous entrance & exit times (microsecs) **** ");
    for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
	VM.sysWrite("  Thread ", i, ": ");
      for (int j = 0; j < rendezvousCount[i]; j++) {
	  VM.sysWrite("   R", j, " in ", rendezvousIn[i][j]);
	  VM.sysWrite(" out ", rendezvousOut[i][j]);
      }
      VM.sysWriteln();
    }
    VM.sysWriteln();
  }

  /**
   * Comments are for default implementation of jni (not the alternative implemenation)
   * <p>
   * First rendezvous for a collection, called by all CollectorThreads that arrive
   * to participate in a collection.  Thread with gcOrdinal==1 is responsible for
   * detecting RVM processors stuck in Native C, blocking them in Native, and making
   * them non-participants in the collection (by setting their counters to -1).
   * Other arriving collector threads just wait until all have either arrived or
   * been declared non-participating.
   */
  public void startupRendezvous () throws VM_PragmaUninterruptible {

    int myProcessorId = VM_Processor.getCurrentProcessorId();
    VM_CollectorThread th = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    int myNumber = th.getGCOrdinal();
    int numExcluded = 0;  // number of RVM VPs NOT participating

    if (trace_startup) {
      VM.sysWrite("Proc ", myProcessorId);
      VM.sysWrite(" ordinal ", myNumber);
      VM.sysWrite(" pr = ", VM_Magic.objectAsAddress(VM_Processor.getCurrentProcessor()));
      VM.sysWrite(" th = ", VM_Magic.objectAsAddress(th));
      VM.sysWriteln(" entered startupRendezvous");
    }

    rendezvousCount[myProcessorId] = 0;  

    if (VM.VerifyAssertions) {
      if (entryCounts[myProcessorId]!=0) {
	VM_Scheduler.trace("startupRendezvous:", "on entry entryCount =",entryCounts[myProcessorId]);
	VM._assert(entryCounts[myProcessorId]==0);
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
	while (entryCounts[i] == 0) {
	  if (trace_startup) {
	    VM.sysWrite("Proc ", myProcessorId);
	    VM.sysWrite(" waiting for ", i);
	  }
	  waitABit(1);
	}
      }

      // now also wait for native daemon processor to be excluded (done so by
      // ordinal=1 thread in code below) or to arrive.
      while (entryCounts[VM_Scheduler.nativeDPndx] == 0) {
	  if (trace_startup) {
	    VM.sysWrite("Proc ", myProcessorId);
	    VM.sysWrite(" ordinal ", myNumber);
	    VM.sysWriteln(" waiting for NDP to arrive or be excluded");
	  }
	  waitABit(1);
      }

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

	// if missing thread/processor is in native, block it there,
	// and set count to -1 to indicate a non-participant for this GC
	//
	if ( VM_Scheduler.processors[i].lockInCIfInC() ) {
	  numExcluded++;
	  if (trace_unusual) VM_Scheduler.trace("startupRendezvous:","TAKING OUT processor",i);
	  removeProcessor( i );
	}
      }
      if (trace_startup) {
	if (entryCounts[i] == 1)
	  VM_Scheduler.trace("startupRendezvous:","processor INCLUDED - id =",i);
	else
	  VM_Scheduler.trace("startupRendezvous:","processor EXCLUDED - id =",i);
      }
    }

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
  public void resetRendezvous () throws VM_PragmaUninterruptible {

    for ( int i = 1; i <= VM_Scheduler.numProcessors; i++)
      entryCounts[i] = 0;

    entryCounts[VM_Scheduler.nativeDPndx] = 0;

    if ( ! VM.BuildForSingleVirtualProcessor) {
      // Set number of Real processors on the running computer. This will allow
      // waitABit() to spin when running with fewer VM_Procssors than real processors
      numRealProcessors = VM_Interface.numProcessors();
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
  private int waitABit ( int x ) throws VM_PragmaUninterruptible {
    int sum = 0;
    if (VM_Scheduler.numProcessors < numRealProcessors) {
      // spin for a while, keeping the operating system thread
      for ( int i = 0; i < (x*100); i++)
	sum = sum + i;
      return sum;
    }
    else {
      // yield executing operating system thread back to the operating system
      VM_Interface.lowYield();
      return 0;
    }
  }

  /**
   * remove a processor from the rendezvous for the current collection.
   * The removed processor in considered a "non-participant" for the collection.
   *
   * @param id  processor id of processor to be removed.
   */
  private void removeProcessor( int id ) throws VM_PragmaUninterruptible {

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
      VM._assert(ct.isGCThread == true);
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
    VM_CollectorThread.collectorThreads[id].setGCOrdinal(-1);

    VM_Magic.sync();      // make other threads/processors see the update

  }  // removeProcessor

}
