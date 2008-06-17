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
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.mm.mmtk.SynchronizedCounter;
import static org.jikesrvm.runtime.SysCall.sysCall;
import org.jikesrvm.scheduler.Processor;
import org.jikesrvm.scheduler.Scheduler;
import org.jikesrvm.scheduler.greenthreads.GreenProcessor;
import org.jikesrvm.scheduler.greenthreads.GreenScheduler;
import org.jikesrvm.scheduler.greenthreads.GreenThread;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A synchronization barrier used to synchronize collector threads,
 * and the Processors they are running on, during parallel collections.
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

  final SynchronizedCounter initReached = new SynchronizedCounter();
  boolean initGoAhead = false;

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

    Magic.isync(); // so subsequent instructions won't see stale values

    // XXX This should be changed to return ordinal of current rendezvous rather than the one at the beginning
    return Magic.threadAsCollectorThread(Scheduler.getCurrentThread()).getGCOrdinal();
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

    int myProcessorId = Processor.getCurrentProcessorId();
    CollectorThread th = Magic.threadAsCollectorThread(Scheduler.getCurrentThread());
    int myNumber = th.getGCOrdinal();

    if (verbose > 0) {
      VM.sysWriteln("GC Message: SynchronizationBarrier.startupRendezvous: proc ",
                    myProcessorId,
                    " ordinal ",
                    myNumber);
    }

    if (myNumber > 1) {
      initReached.increment();
      if (verbose > 0) VM.sysWriteln("GC Message: startupRendezvous  has incremented for ", myNumber);
      Magic.sync();
      while (!initGoAhead) waitABit(5);
      Magic.sync();
      if (verbose > 0) VM.sysWriteln("GC Message: startupRendezvous  ack received by ", myNumber);
      barrier.arrive(8888);
      if (verbose > 0) VM.sysWriteln("GC Message: startupRendezvous  leaving as ", myNumber);
      return;
    }

    // wait for threads to show up while also ensuring that none of them
    // disappear.

    initReached.increment();
    int numParticipating=GreenScheduler.numProcessors;
    while (initReached.peek()<GreenScheduler.numProcessors) {
      waitABit(5);
      for (int i = 1; i <= GreenScheduler.numProcessors; i++) {
        if (GreenScheduler.getProcessor(i).lockInCIfInC()) { // can't be true for self
          if (verbose > 0) VM.sysWriteln("GC Message: excluding processor ", i);
          removeProcessor(i);
          initReached.increment();
          numParticipating--;
        }
      }
    }

    if (verbose > 0) VM.sysWriteln("GC Message: everyone has arrived");

    // we blocked out some threads that got stuck in C, and all other threads
    // are now waiting for me to give them the go-ahead.

    Magic.sync();
    initGoAhead=true;
    Magic.sync();

    // now we set the target for the Barrier and perform an 'arrive', as a
    // silly hack to make it easy to reset our counters.

    if (verbose > 0) VM.sysWriteln("GC Message: waiting for barrier arrival to allow reset");

    barrier.setTarget(numParticipating);
    barrier.arrive(8888);

    initReached.reset();
    initGoAhead=false;

    Magic.sync();   // update main memory so other processors will see it in "while" loop
    Magic.isync();  // so subsequent instructions won't see stale values
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
    Magic.sync();      // make other threads/processors see the update
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
    if (GreenScheduler.numProcessors < numRealProcessors) {
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

    GreenProcessor vp = GreenScheduler.getProcessor(id);

    GreenThread ct=null;

    // get processor's collector thread off its transfer queue, waiting if
    // necessary for it to show up
    int spinCnt=0;
    for (;;) {
      boolean done=false;
      vp.collectorThreadMutex.lock("removing a processor from gc");
      ct = vp.collectorThread;
      if (ct!=null) {
        if (verbose>0) VM.sysWriteln("setting collectorThread to null in SB.removeProcessor for ",id);
        vp.collectorThread = null;
        done=true;
      } else {
        if (spinCnt++==100) {
          if (verbose>0) VM.sysWriteln("it's taking a while in SB.removeProcessor for ",id);
        }
      }
      vp.collectorThreadMutex.unlock();
      if (done) break;
      waitABit(5);
    }

    // put it back on the global collector thread queue
    GreenScheduler.collectorMutex.lock("collector mutex for processor removal");
    GreenScheduler.collectorQueue.enqueue(ct);
    GreenScheduler.collectorMutex.unlock();

    // set VPs CollectorThread ordinal number negative to indicate not participating
    CollectorThread.collectorThreads[id].setGCOrdinal(-1);

    Magic.sync();      // make other threads/processors see the update

  }  // removeProcessor

}
