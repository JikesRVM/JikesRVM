/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * VM_Handshake handles mutator requests to initiate a collection, 
 * and wait for a collection to complete.  It implements the process
 * of suspending all mutator threads executing in Java and starting
 * all the GC threads (VM_CollectorThreads) for the processors that
 * will be participating in a collection.  This may not be all 
 * processors, if we exclude those executing in native code.
 *
 * Because the threading strategy within RVM is currently
 * under revision, the logic here is also changing and somewhat "messy".
 *
 * @see VM_CollectorThread
 *
 * @author Derek Lieber
 * @author Bowen Alpern
 * @author Stephen Smith
 */

package com.ibm.JikesRVM.memoryManagers.vmInterface;

import com.ibm.JikesRVM.memoryManagers.JMTk.Lock;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_BootRecord;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_Synchronization;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Thread;

public class VM_Handshake {
  
  public static int verbose = 0;
  
  static final int LOCKOUT_GC_WORD = 0x0CCCCCCC;
  
  protected boolean requestFlag;
  protected boolean completionFlag;
  private Lock lock = new Lock("handshake");
  
  public void reset() {
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
  private void initiateCollection() throws VM_PragmaUninterruptible {

    // check that scheduler initialization is complete
    //	
    if (!VM_Scheduler.allProcessorsInitialized) {
      VM.sysWriteln("Garbage collection required before system fully initialized");
      VM.sysWriteln("Specify larger than default heapsize on command line");
      VM_Scheduler.dumpStack();
      VM.shutdown(-1);
    }

    if (verbose >= 3) {
	VM_Scheduler.trace("VM_Handshake:initiateCollection","dumping machine...");
	VM_Scheduler.dumpVirtualMachine();
    }
    if (verbose >= 2) {
      VM_Scheduler.trace("VM_Handshake:initiateCollection","collectorQueue:");
      VM_Scheduler.writeString("before waiting:"); VM_Scheduler.collectorQueue.dump();
    }

    // Get the number of GC threads.  Include NativeDaemonProcessor collector thread in the count.
    //    If it exists, check for null to allow builds without a NativeDaemon (see VM_Scheduler)
    //
    int maxCollectorThreads = VM_Scheduler.numProcessors;
    if (!VM.BuildForSingleVirtualProcessor && VM_Scheduler.processors[VM_Scheduler.nativeDPndx] != null )
      maxCollectorThreads++;

    // Wait for all gc threads to finish preceeding collection cycle
    //
    if (verbose >= 2) VM.sysWriteln("VM_Handshake.initiateCollection checking if previous collection to finish");
    while (true) {
      VM_Scheduler.collectorMutex.lock();
      int len = VM_Scheduler.collectorQueue.length();
      VM_Scheduler.collectorMutex.unlock();
      if (len < maxCollectorThreads) {
	  if (verbose >= 1) VM.sysWriteln("VM_Handshake.initiateCollection waiting for previous collection to finish");
	  lock.release();	// release lock so other threads can make progress
	  VM_Thread.getCurrentThread().yield();
	  lock.acquire();	// acquire lock to make progress
      }
      else 
	  break;
    }

    // Acquire global lockout field inside the boot record.  Will be released when gc completes.
    // 
    if (verbose >= 2) VM.sysWriteln("VM_Handshake.initiateCollection acquiring lockout field");
    int lockoutFieldOffset =  VM_Entrypoints.lockoutProcessorField.getOffset();
    while (true) {
      int lockoutVal = VM_Magic.prepare(VM_BootRecord.the_boot_record, lockoutFieldOffset);
      if ( lockoutVal == 0) {
	if (VM_Magic.attempt(VM_BootRecord.the_boot_record, lockoutFieldOffset, 
			     0, LOCKOUT_GC_WORD))
	  break;
      }
      else {
	if (verbose >= 2) 
	    VM.sysWrite("VM_Handshake.initiateCollection: lockoutLock contains ", lockoutVal);
      }
    }
    if (verbose >= 2) VM.sysWriteln("VM_Handshake.initiateCollection acquired lockout field");

    // reset counter for collector threads arriving to participate in the collection
    VM_CollectorThread.participantCount[0] = 0;

    // reset rendezvous counters to 0, the decision about which collector threads
    // will participate has moved to the run method of CollectorThread
    //
    VM_CollectorThread.gcBarrier.resetRendezvous();

    // scan all the native Virtual Processors and attempt to block those executing
    // native C, to prevent returning to java during collection.  the first collector
    // thread will wait for those not blocked to reach the SIGWAIT state.
    // (see VM_CollectorThread.run)
    //
    for (int i = 1; i <= VM_Processor.numberNativeProcessors; i++) {
      if (VM.VerifyAssertions) VM._assert(VM_Processor.nativeProcessors[i] != null);
      VM_Processor.nativeProcessors[i].lockInCIfInC();
      if (verbose >= 2) {
        int newStatus =  VM_Processor.vpStatus[VM_Processor.nativeProcessors[i].vpStatusIndex];
        VM.sysWrite("VM_Handshake.initiateCollection:  Native Processor ", i, " newStatus = ", newStatus);
      }
    }

    // Dequeue and schedule collector threads on ALL RVM Processors,
    // including those running system daemon threads (ex. NativeDaemonProcessor)
    //
    if (verbose >= 2) VM.sysWriteln("VM_Handshake.initiateCollection: scheduling collector threads");
    VM_Scheduler.collectorMutex.lock();
    while (VM_Scheduler.collectorQueue.length() > 0) {
      VM_Thread t = VM_Scheduler.collectorQueue.dequeue();
      t.scheduleHighPriority();
      VM_Processor p = t.processorAffinity;
      p.threadSwitchRequested = -1;       // set thread switch requested condition in VP
    }
    VM_Scheduler.collectorMutex.unlock();
    
  }   // initiateCollection

  
  /**
   * Called by mutators to request a garbage collection.
   * If the completionFlag is already set, return false.
   * Else, if the requestFlag is not yet set (ie this is the
   * first mutator to request this collection) then initiate
   * the collection sequence
   *
   * @return true if the completion flag is nto already set.
   */
  private boolean request() throws VM_PragmaUninterruptible {
    lock.acquire();
    if (completionFlag) {
      if (verbose >= 2)
	VM_Scheduler.trace("VM_Handshake", "mutator: already completed");
      lock.release();
      return false;
    }
    if (requestFlag) {
      if (verbose >= 2)
	VM_Scheduler.trace("VM_Handshake", "mutator: already in progress");
    } else {
      // first mutator initiates collection by making all gc threads
      // runnable at high priority
      if (verbose >= 2)
	VM_Scheduler.trace("VM_Handshake", "mutator: initiating collection");
      VM_CollectorThread.gcBarrier.rendezvousStartTime = VM_Time.now();
      requestFlag = true;
      initiateCollection();
    }
    lock.release();  
    return true;
  }

  /**
   * Called by mutators to request a garbage collection and wait
   * for it to complete.
   *
   * Waiting is actually just yielding the processor to schedule
   * the collector thread, which will disable further thread
   * switching on the processor until it has completed the
   * collection.
   */
  public void requestAndAwaitCompletion() throws VM_PragmaInterruptible {
    if (request()) {
      if (verbose >= 2) 
	VM_Scheduler.trace("VM_Handshake", "mutator: yielding to GC");
      // allow a gc thread to run
      VM_Thread.getCurrentThread().yield();
      if (verbose >= 2)
	VM_Scheduler.trace("VM_Handshake", "mutator: running");
    }
  }
  

  /**
   * Called by mutators to request an asynchronous garbage collection.
   * After initiating a GC (if one is not already initiated), the
   * caller continues until it yields to the GC.  It may thus make
   * this call at an otherwise unsafe point.
   */
  public void requestAndContinue() throws VM_PragmaUninterruptible {
    if (request()) {
      if (verbose >= 2) 
	VM_Scheduler.trace("VM_Handshake", "mutator: yielding to GC");
      // allow a gc thread to run
      VM_Thread.getCurrentThread().yield();
      if (verbose >= 2)
	VM_Scheduler.trace("VM_Handshake", "mutator: running");
    }
  }
  
  /**
   * Set the completion flag that indicates the collection has completed.
   * Called by a collector thread after the collection has completed.
   * It currently does not do a "notify" on waiting mutator threads,
   * since they are in VM_Processor thread queues, waiting
   * for the collector thread to re-enable thread switching.
   *
   * @see VM_CollectorThread
   */
  void notifyCompletion() throws VM_PragmaInterruptible {
    lock.acquire();
    if (verbose >= 2) VM_Scheduler.trace("VM_Handshake", "collector: completed");
    if (verbose >= 3) VM_Scheduler.dumpVirtualMachine();
    completionFlag = true;
    lock.release();
  }

  /**
   * Acquire LockoutLock.  Accepts a value to store into the lockoutlock
   * word when it becomes available (value == 0). If not available when
   * called, a passed flag indicates either spinning or yielding until
   * the word becomes available.
   *
   * @param value    Value to store into lockoutlock word
   * @param spinwait flag to cause spinning (if true) or yielding
   */
  public static void acquireLockoutLock(int value, boolean spinwait) throws VM_PragmaUninterruptible {
    if (spinwait) {
      while (true) {
	int lockoutVal = VM_Magic.prepare(VM_BootRecord.the_boot_record,
					  VM_Entrypoints.lockoutProcessorField.getOffset());
	if (lockoutVal == 0) {
	  if (VM_Magic.attempt(VM_BootRecord.the_boot_record,
			       VM_Entrypoints.lockoutProcessorField.getOffset(),
			       0, value))
	    break;
	}
      }
      return;
    }

    // else - no spinwait:
    // yield until lockout word is available (0), then attempt to set

    while (true) {
      int lockoutVal = VM_Magic.prepare(VM_BootRecord.the_boot_record,
					VM_Entrypoints.lockoutProcessorField.getOffset());
      if (lockoutVal != 0) {
	if (verbose >= 2) VM_Scheduler.trace("Handshake:acquireLockOutLock",
					  "yielding: lockoutVal =",lockoutVal);
	VM_Thread.yield();
	continue;
      } else {
	if (VM_Magic.attempt(VM_BootRecord.the_boot_record,
			    VM_Entrypoints.lockoutProcessorField.getOffset(),
			    0, value))
	  break;
      }
    }
  }


  /**
   * Release the LockoutLock by setting the lockoutlock word to 0.
   * The expected current value that should be in the word can be
   * passed in, and is verified if not 0.
   *
   * @param value    Value that should currently be in the lockoutlock word
   */
  public static void releaseLockoutLock(int value) throws VM_PragmaUninterruptible {
    while (true) {
      int lockoutVal = VM_Magic.prepare(VM_BootRecord.the_boot_record,
					VM_Entrypoints.lockoutProcessorField.getOffset());
      // check that current value is as expected
      if (VM.VerifyAssertions && (value!=0)) VM._assert( lockoutVal == value );
      // OK, reset to zero
      if(VM_Magic.attempt(VM_BootRecord.the_boot_record,
			  VM_Entrypoints.lockoutProcessorField.getOffset(),
			  lockoutVal, 0))
	break;
    }
  }


  /**
   * Return the current contents of the lockoutLock word
   *
   * @return  current lockoutLock word
   */
  public static int queryLockoutLock() throws VM_PragmaUninterruptible {
    while (true) {
      int lockoutVal = VM_Magic.prepare(VM_BootRecord.the_boot_record,
				    VM_Entrypoints.lockoutProcessorField.getOffset());
      if (VM_Magic.attempt(VM_BootRecord.the_boot_record,
			   VM_Entrypoints.lockoutProcessorField.getOffset(),
			   lockoutVal, lockoutVal))
	  return lockoutVal;
    }
  }

}


