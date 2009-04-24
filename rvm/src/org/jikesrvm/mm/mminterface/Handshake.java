/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.mm.mminterface;

import org.jikesrvm.VM;
import org.jikesrvm.mm.mmtk.Collection;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.scheduler.Monitor;
import org.vmmagic.pragma.Unpreemptible;
import org.mmtk.plan.Plan;

/**
 * Handshake handles mutator requests to initiate a collection, and
 * wait for a collection to complete.  It implements the process of
 * suspending all mutator threads executing in Java and starting all
 * the GC threads (CollectorThreads) for the processors that will
 * be participating in a collection.  This may not be all processors,
 * if we exclude those executing in native code.
 *
 * Because the threading strategy within RVM is currently under
 * revision, the logic here is also changing and somewhat "messy".
 *
 * @see CollectorThread
 */
public class Handshake {

  /***********************************************************************
   *
   * Class variables
   */
  public static final int verbose = 0;

  /***********************************************************************
   *
   * Instance variables
   */
  private Monitor lock;
  protected boolean requestFlag;
  public int gcTrigger;  // reason for this GC
  private int collectorThreadsParked;

  public Handshake() {
    reset();
  }
  public void boot() {
    lock = new Monitor();
  }

  /**
   * Call this if you know that a GC request has already been made and you'd like
   * to wait on that GC to finish - presumably because you're trying to allocate
   * and cannot reasonably do so before GC is done.  Note, there CANNOT be a
   * GC safe point between when you realize that there is already a GC request and
   * when you call this method!
   */
  @Unpreemptible
  public void waitForGCToFinish() {
    if (verbose >= 1) VM.sysWriteln("GC Message: Handshake.requestAndAwaitCompletion - yielding");
    /* allow a gc thread to run */
    RVMThread t=RVMThread.getCurrentThread();
    t.assertAcceptableStates(RVMThread.IN_JAVA,
                             RVMThread.IN_JAVA_TO_BLOCK);
    RVMThread.observeExecStatusAtSTW(t.getExecStatus());
    t.block(RVMThread.gcBlockAdapter);
    if (verbose >= 1) VM.sysWriteln("GC Message: Handshake.requestAndAwaitCompletion - mutator running");
  }

  /**
   * Called by mutators to request a garbage collection and wait for
   * it to complete.
   *
   * Waiting is actually just yielding the processor to schedule the
   * collector thread, which will disable further thread switching on
   * the processor until it has completed the collection.
   */
  @Unpreemptible
  public void requestAndAwaitCompletion(int why) {
    request(why);
    waitForGCToFinish();
  }

  /**
   * Called by mutators to request an asynchronous garbage collection.
   * After initiating a GC (if one is not already initiated), the
   * caller continues until it yields to the GC.  It may thus make
   * this call at an otherwise unsafe point.
   */
  @Unpreemptible("Change state of thread possibly context switching if generating exception")
  public void requestAndContinue(int why) {
    request(why);
  }

  @Unpreemptible
  public void reset() {
    if (lock!=null) {
      lock.lockNoHandshake();
    }
    gcTrigger = Collection.UNKNOWN_GC_TRIGGER;
    requestFlag = false;
    if (lock!=null) {
      lock.unlock();
    }
  }
  @Unpreemptible
  void parkCollectorThread() {
    lock.lockNoHandshake();
    collectorThreadsParked++;
    lock.broadcast();
    if (verbose>=1) VM.sysWriteln("GC Thread #",RVMThread.getCurrentThreadSlot()," parked.");
    while (!requestFlag) {
      if (verbose>=1) VM.sysWriteln("GC Thread #",RVMThread.getCurrentThreadSlot()," waiting for request.");
      lock.waitWithHandshake();
    }
    if (verbose>=1) VM.sysWriteln("GC Thread #",RVMThread.getCurrentThreadSlot()," got request, unparking.");
    collectorThreadsParked--;
    lock.unlock();
  }

  /**
   * Called by mutators to request a garbage collection.
   *
   * @return true if the completion flag is not already set.
   */
  @Unpreemptible("Becoming another thread interrupts the current thread, avoid preemption in the process")
  private boolean request(int why) {
    if (verbose>=1) VM.sysWriteln("Thread #",RVMThread.getCurrentThreadSlot()," is trying to make a GC request");
    lock.lockNoHandshake();
    if (verbose>=1) VM.sysWriteln("Thread #",RVMThread.getCurrentThreadSlot()," acquired the lock for making a GC request");
    if (why > gcTrigger) gcTrigger = why;
    if (requestFlag) {
      if (verbose >= 1) {
        VM.sysWriteln("GC Message: mutator: already in progress");
      }
    } else {
      // first mutator initiates collection by making all gc threads
      // runnable at high priority
      if (verbose >= 1) VM.sysWriteln("GC Message: Handshake - mutator: initiating collection");

      if (!RVMThread.threadingInitialized) {
        VM.sysWrite("GC required before system fully initialized");
        VM.sysWriteln("Specify larger than default heapsize on command line");
        RVMThread.dumpStack();
        VM.shutdown(VM.EXIT_STATUS_MISC_TROUBLE);
      }

      requestFlag = true;
      Plan.setCollectionTriggered();
      lock.broadcast();
    }
    lock.unlock();
    return true;
  }
}

