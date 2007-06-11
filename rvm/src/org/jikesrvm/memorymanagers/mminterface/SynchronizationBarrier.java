/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.memorymanagers.mminterface;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.VM_Magic;
import static org.jikesrvm.runtime.VM_SysCall.sysCall;
import org.jikesrvm.scheduler.VM_Processor;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.VM_Thread;
import org.mmtk.utility.Barrier;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A synchronization barrier used to synchronize collector threads,
 * and the VM_Processors they are running on, during parallel collections.
 *
 * The core barrier functionality is implemented by a barrier object.
 * The code in this class is in charge of VM-related idiosyncrasies like
 * computing how many processors are participating in a particular collection.
 */
public final class SynchronizationBarrier {

  private static final int verbose = 0;

  // number of physical processors on running computer
  private int numRealProcessors;

  final Barrier barrier = new Barrier();

  /**
   * Constructor
   */
  public SynchronizationBarrier() {
    // initialize numRealProcessors to 1. Will be set to actual value later.
    // Using without resetting will cause waitABit() to yield instead of spinning
    numRealProcessors = 1;
  }

  /**
   * Wait for all other collectorThreads/processors to arrive at this barrier.
   */
  @Uninterruptible
  public int rendezvous(int where) {

    barrier.arrive(where);

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
  @Uninterruptible
  public void startupRendezvous() {

    int myProcessorId = VM_Processor.getCurrentProcessorId();
    VM_CollectorThread th = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    int myNumber = th.getGCOrdinal();

    if (verbose > 0) {
      VM.sysWriteln("GC Message: SynchronizationBarrier.startupRendezvous: proc ",
                    myProcessorId,
                    " ordinal ",
                    myNumber);
    }

    if (myNumber > 1) {   // non-designated guys just wait for designated guy to finish
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
      if (VM_Scheduler.processors[i].lockInCIfInC()) { // can't be true for self
        if (verbose > 0) VM.sysWriteln("GC Message: excluding processor ", i);
        removeProcessor(i);
      } else {
        numParticipating++;
      }
    }

    if (verbose > 0) {
      VM.sysWriteln("GC Message: startupRendezvous  numParticipating = ", numParticipating);
    }
    barrier.setTarget(numParticipating);
    barrier.arrive(8888);    // all setup now complete and we can proceed
    VM_Magic.sync();   // update main memory so other processors will see it in "while" loop
    VM_Magic.isync();  // so subsequent instructions won't see stale values
    if (verbose > 0) {
      VM.sysWriteln("GC Message: startupRendezvous  designated proc leaving");
    }

  }  // startupRendezvous

  /**
   * reset the rendezvous counters for all VPs to 0.
   * Also sets numRealProcessors to number of real CPUs.
   */
  @Uninterruptible
  public void resetRendezvous() {
    numRealProcessors = sysCall.sysNumProcessors();
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
  @Uninterruptible
  private int waitABit(int x) {
    int sum = 0;
    if (VM_Scheduler.numProcessors < numRealProcessors) {
      // spin for a while, keeping the operating system thread
      for (int i = 0; i < (x * 100); i++) {
        sum = sum + i;
      }
      return sum;
    } else {
      sysCall.sysVirtualProcessorYield();        // pthread yield
      return 0;
    }
  }

  /**
   * remove a processor from the rendezvous for the current collection.
   * The removed processor in considered a "non-participant" for the collection.
   *
   * @param id  processor id of processor to be removed.
   */
  @Uninterruptible
  private void removeProcessor(int id) {

    VM_Processor vp = VM_Scheduler.processors[id];

    // get processors collector thread off its transfer queue
    vp.transferMutex.lock();
    VM_Thread ct = vp.transferQueue.dequeueGCThread(null);
    vp.transferMutex.unlock();
    if (VM.VerifyAssertions) {
      VM._assert(ct != null && ct.isGCThread());
    }
    // put it back on the global collector thread queue
    VM_Scheduler.collectorMutex.lock();
    VM_Scheduler.collectorQueue.enqueue(ct);
    VM_Scheduler.collectorMutex.unlock();

    // set VPs CollectorThread ordinal number negative to indicate not participating
    VM_CollectorThread.collectorThreads[id].setGCOrdinal(-1);

    VM_Magic.sync();      // make other threads/processors see the update

  }  // removeProcessor

}
