/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.memoryManagers.mmInterface;

import org.mmtk.vm.Lock;
import org.mmtk.vm.VM_Interface;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.VM;
import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

import com.ibm.JikesRVM.VM_BootRecord;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_Synchronization;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Thread;

/**
 * VM_Handshake handles mutator requests to initiate a collection, and
 * wait for a collection to complete.  It implements the process of
 * suspending all mutator threads executing in Java and starting all
 * the GC threads (VM_CollectorThreads) for the processors that will
 * be participating in a collection.  This may not be all processors,
 * if we exclude those executing in native code.
 *
 * Because the threading strategy within RVM is currently under
 * revision, the logic here is also changing and somewhat "messy".
 *
 * @see VM_CollectorThread
 *
 * @author Derek Lieber
 * @author Bowen Alpern
 * @author Stephen Smith
 * @modified Perry Cheng
 *
 * @version $Revision$
 * @date $Date$
 */
public class VM_Handshake {
  
  /***********************************************************************
   *
   * Class variables
   */
  public static int verbose = 0;
  static final int LOCKOUT_GC_WORD = 0x0CCCCCCC;
  
  /***********************************************************************
   *
   * Instance variables
   */
  private Lock lock = new Lock("handshake");
  protected boolean requestFlag;
  protected boolean completionFlag;
  public int gcTrigger;  // reason for this GC

  public VM_Handshake () {
    reset();
  }
  
  /**
   * Called by mutators to request a garbage collection and wait for
   * it to complete.
   *
   * Waiting is actually just yielding the processor to schedule the
   * collector thread, which will disable further thread switching on
   * the processor until it has completed the collection.
   */
  public void requestAndAwaitCompletion(int why) throws InterruptiblePragma {
    if (request()) {
      gcTrigger = why;
      if (verbose >= 1) VM.sysWriteln("GC Message: VM_Handshake.requestAndAwaitCompletion - yielding");
      /* allow a gc thread to run */
      VM_Thread.getCurrentThread().yield();
      complete();
      if (verbose >= 1) VM.sysWriteln("GC Message: VM_Handshake.requestAndAwaitCompletion - mutator running");
    }
  }
  
  /**
   * Called by mutators to request an asynchronous garbage collection.
   * After initiating a GC (if one is not already initiated), the
   * caller continues until it yields to the GC.  It may thus make
   * this call at an otherwise unsafe point.
   */
  public void requestAndContinue() throws UninterruptiblePragma {
    request();
  }

  public void reset() throws UninterruptiblePragma {
    gcTrigger = VM_Interface.UNKNOWN_GC_TRIGGER;
    requestFlag = false;
    completionFlag = false;
  }

  /**
   * Initiates a garbage collection.  Called from requestAndAwaitCompletion
   * by the first mutator thread to request a collection using the
   * current VM_Handshake object.
   *
   * The sequence of events that start a collection is initiated by the
   * calling mutator, and it then yields, waiting for the collection
   * to complete.
   *
   * While mutators are executing, all the GC threads (VM_CollectorThreads)
   * reside on a single system queue, VM_Scheduler.collectorQueue.  This
   * method determines which processors will participate in the collection,
   * dequeues the GC threads associated with those processors, and
   * schedules them for executing on their respective processors.
   * (Most of the time, all processors and thus all GC threads participate,
   * but this is changing as the RVM thread startegy changes.)
   *
   * The collection actually starts when all participating GC threads
   * arrive at the first rendezvous in VM_CollectorThreads run method,
   * and suspend thread switching on their processors.
   *
   * While collection is in progress, mutators are not explicitly waiting
   * for the collection. They reside in the thread dispatch queues of their
   * processors, until the collector threads re-enable thread switching.
   */
  private void initiateCollection() throws UninterruptiblePragma {

    /* check that scheduler initialization is complete */
    if (!VM_Scheduler.allProcessorsInitialized) {
      VM.sysWrite("GC required before system fully initialized");
      VM.sysWriteln("Specify larger than default heapsize on command line");
      VM_Scheduler.dumpStack();
      VM.shutdown(VM.exitStatusMiscTrouble);
    }

    /* wait for preceding GC to complete */
    if (verbose >= 2) {
      VM.sysWrite("GC Message: VM_Handshake.initiateCollection before waiting");
      VM_Scheduler.collectorQueue.dump();
    }
    int maxCollectorThreads = waitForPrecedingGC();

    /* Acquire global lockout field inside the boot record.  Will be
     * released when gc completes. */
    VM_CollectorThread.gcThreadRunning = true;

    /* reset counter for collector threads arriving to participate in
     * the collection */
    VM_CollectorThread.participantCount[0] = 0;
    
    /* reset rendezvous counters to 0, the decision about which
     * collector threads will participate has moved to the run method
     * of CollectorThread */
    VM_CollectorThread.gcBarrier.resetRendezvous();

    /* Deque and schedule collector threads on ALL RVM Processors.
     */
    if (verbose >= 1) 
      VM.sysWriteln("GC Message: VM_Handshake.initiateCollection: scheduling collector threads");
    VM_Scheduler.collectorMutex.lock();
    if (VM_Scheduler.collectorQueue.length() != maxCollectorThreads) 
      VM.sysWriteln("GC Error: Expected ", maxCollectorThreads, 
                    " GC threads.   Found ", 
                    VM_Scheduler.collectorQueue.length());
    while (VM_Scheduler.collectorQueue.length() > 0) {
      VM_Thread t = VM_Scheduler.collectorQueue.dequeue();
      t.scheduleHighPriority();
      VM_Processor p = t.processorAffinity;
      p.threadSwitchRequested = -1; // set thread switch req condition in VP
    }
    VM_Scheduler.collectorMutex.unlock();
  }

  /**
   * Wait for all GC threads to complete previous collection cycle.
   *
   * @return The number of GC threads.
   */
  private int waitForPrecedingGC() throws UninterruptiblePragma {
    /*
     * Get the number of GC threads.  Include NativeDaemonProcessor
     * collector thread in the count.  If it exists, check for null to
     * allow builds without a NativeDaemon (see VM_Scheduler)
     */
    int maxCollectorThreads = VM_Scheduler.numProcessors;
    
    /* Wait for all gc threads to finish preceeding collection cycle */
    if (verbose >= 1) {
        VM.sysWrite("GC Message: VM_Handshake.initiateCollection ");
        VM.sysWriteln("checking if previous collection is finished");
    }
    int count = 0;
    while (true) {
      VM_Scheduler.collectorMutex.lock();
      int len = VM_Scheduler.collectorQueue.length();
      if (count++ == 100000) {
        VM.sysWriteln("GC Warning: WAITED LONG TIME FOR PRECEEDING GC TO FINISH");
        VM.sysWriteln("GC Warning:          len = ", len);
        VM.sysWriteln("GC Warning:    maxCollTh = ", maxCollectorThreads);
        // VM_Scheduler.collectorQueue.dump();
      }
      VM_Scheduler.collectorMutex.unlock();
      if (len < maxCollectorThreads) {
        if (verbose >= 1) VM.sysWrite("GC Message: VM_Handshake.initiateCollection waiting for previous collection to finish");
        lock.release();   // release lock so other threads can make progress
        VM_Thread.getCurrentThread().yield();
        lock.acquire();   // acquire lock to make progress
      } else 
        break;
    }
    return maxCollectorThreads;
  }

  private void complete() throws UninterruptiblePragma {
    for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
        VM_Scheduler.processors[i].unblockIfBlockedInC();
    }
  }

  /**
   * Called by mutators to request a garbage collection.  If the
   * completionFlag is already set, return false.  Else, if the
   * requestFlag is not yet set (ie this is the first mutator to
   * request this collection) then initiate the collection sequence
   *
   * @return true if the completion flag is not already set.
   */
  private boolean request() throws UninterruptiblePragma {
    lock.acquire();
    if (completionFlag) {
      if (verbose >= 1)
        VM.sysWriteln("GC Message: mutator: already completed");
      lock.release();
      return false;
    }
    if (requestFlag) {
      if (verbose >= 1)
        VM.sysWriteln("GC Message: mutator: already in progress");
    } else {
      // first mutator initiates collection by making all gc threads
      // runnable at high priority
      if (verbose >= 1) VM.sysWriteln("GC Message: VM_Handshake - mutator: initiating collection");
      requestFlag = true;
      initiateCollection();
    }
    lock.release();  
    return true;
  }

  
  /**
   * Set the completion flag that indicates the collection has
   * completed.  Called by a collector thread after the collection has
   * completed.  It currently does not do a "notify" on waiting
   * mutator threads, since they are in VM_Processor thread queues,
   * waiting for the collector thread to re-enable thread switching.
   *
   * @see VM_CollectorThread
   */
  void notifyCompletion() throws UninterruptiblePragma {
    lock.acquire();
    if (verbose >= 1)
      VM.sysWriteln("GC Message: VM_Handshake.notifyCompletion");
    complete();
    completionFlag = true;
    lock.release();
  }
}


