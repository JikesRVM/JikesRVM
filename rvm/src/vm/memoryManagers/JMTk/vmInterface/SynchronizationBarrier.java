/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.mmInterface;

import org.mmtk.utility.Barrier;

import org.vmmagic.pragma.*;

import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_SysCall;
import com.ibm.JikesRVM.VM_BootRecord;

/**
 * A synchronization barrier used to synchronize collector threads,
 * and the VM_Processors they are running on, during parallel collections.
 *
 * The core barrier functionality is implemented by a barrier object.
 * The code in this class is in charge of VM-related idiosyncrasies like
 * computing how many processors are participating in a particular collection.
 *
 * @author   Derek Lieber
 * @author   Perry Cheng - Rewrite major portions
 * @modified Steve Smith
 */
public final class SynchronizationBarrier {

  private static final int verbose = 0;

  // maximum processor id for rendezvous
  private int maxProcessorId;

  // number of physical processors on running computer 
  private int   numRealProcessors; 

  Barrier barrier = new Barrier();

  /**
   * Constructor
   */
  public SynchronizationBarrier () throws UninterruptiblePragma {
    // initialize numRealProcessors to 1. Will be set to actual value later.
    // Using without resetting will cause waitABit() to yield instead of spinning
    numRealProcessors = 1;
  }

  /**
   * Wait for all other collectorThreads/processors to arrive at this barrier.
   */
  public int rendezvous (int where) throws UninterruptiblePragma {

    int myOrder = barrier.arrive(where);

    VM_Magic.isync(); // so subsequent instructions won't see stale values

    // XXX This should be changed to return ordinal of current rendezvous rather than the one at the beginning
    return VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread()).getGCOrdinal();
  }

  /**
   * First rendezvous for a collection, called by all CollectorThreads that arrive
   * to participate in a collection.  Thread with gcOrdinal==1 is responsible for
   * detecting RVM processors stuck in Native C, blocking them in Native, and making
   * them non-participants in the collection (by setting their counters to -1).
   * Other arriving collector threads just wait until all have either arrived or
   * been declared non-participating.
   */
  public void startupRendezvous () throws UninterruptiblePragma {

    int myProcessorId = VM_Processor.getCurrentProcessorId();
    VM_CollectorThread th = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    int myNumber = th.getGCOrdinal();

    if (verbose > 0)
      VM.sysWriteln("GC Message: SynchronizationBarrier.startupRendezvous: proc ", myProcessorId, " ordinal ", myNumber);

    if ( myNumber > 1 ) {   // non-designated guys just wait for designated guy to finish
      barrier.arrive(8888); // wait for designated guy to do his job
      VM_Magic.isync();     // so subsequent instructions won't see stale values
      if (verbose > 0) VM.sysWriteln("GC Message: startupRendezvous  leaving as ", myNumber);
      return;               // leave barrier
    }

    // Thread with gcOrdinal==1 must detect processors whose active threads are
    // stuck in Native C, block them there, and make them non-participants
    //
    waitABit(5);          // give missing threads a chance to show up
    int numParticipating = 0;
    for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
      if ( VM_Scheduler.processors[i].lockInCIfInC() ) { // can't be true for self
          if (verbose > 0) VM.sysWriteln("GC Message: excluding processor ", i);
          removeProcessor(i);
      }
      else
        numParticipating++;
    }

    maxProcessorId = VM_Scheduler.numProcessors;

    if (verbose > 0) 
        VM.sysWriteln("GC Message: startupRendezvous  numParticipating = ", numParticipating);
    barrier.setTarget(numParticipating);
    barrier.arrive(8888);    // all setup now complete and we can proceed
    VM_Magic.sync();   // update main memory so other processors will see it in "while" loop
    VM_Magic.isync();  // so subsequent instructions won't see stale values
    if (verbose > 0) 
        VM.sysWriteln("GC Message: startupRendezvous  designated proc leaving");

  }  // startupRendezvous

  /**
   * reset the rendezvous counters for all VPs to 0.
   * Also sets numRealProcessors to number of real CPUs.
   */
  public void resetRendezvous () throws UninterruptiblePragma {

    if (!VM.singleVirtualProcessor) {
      // Set number of Real processors on the running computer. This will allow
      // waitABit() to spin when running with fewer VM_Procssors than real processors
      numRealProcessors = VM_SysCall.sysNumProcessors();
    }
    barrier.clearTarget();
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
  private int waitABit ( int x ) throws UninterruptiblePragma {
    int sum = 0;
    if (VM_Scheduler.numProcessors < numRealProcessors) {
      // spin for a while, keeping the operating system thread
      for ( int i = 0; i < (x*100); i++)
        sum = sum + i;
      return sum;
    } else {
      VM_SysCall.sysVirtualProcessorYield();        // pthread yield 
      return 0;
    }
  }


  /**
   * remove a processor from the rendezvous for the current collection.
   * The removed processor in considered a "non-participant" for the collection.
   *
   * @param id  processor id of processor to be removed.
   */
  private void removeProcessor( int id ) throws UninterruptiblePragma {

    VM_Processor vp = VM_Scheduler.processors[id];

    // get processors collector thread off its transfer queue
    vp.transferMutex.lock();
    VM_Thread ct = vp.transferQueue.dequeueGCThread(null);
    vp.transferMutex.unlock();
    if (VM.VerifyAssertions) 
      VM._assert(ct != null && ct.isGCThread == true);

    // put it back on the global collector thread queue
    VM_Scheduler.collectorMutex.lock();
    VM_Scheduler.collectorQueue.enqueue(ct);
    VM_Scheduler.collectorMutex.unlock();

    // set VPs CollectorThread ordinal number negative to indicate not participating
    VM_CollectorThread.collectorThreads[id].setGCOrdinal(-1);

    VM_Magic.sync();      // make other threads/processors see the update

  }  // removeProcessor

}
