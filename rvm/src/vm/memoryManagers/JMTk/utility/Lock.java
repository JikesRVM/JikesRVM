/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_Synchronization;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_PragmaInline;

/*
 * Simple locks with deadlock detection.
 *
 * @author Perry Cheng
 */
public class Lock implements VM_Uninterruptible {

  // Internal class fields
  private static int lockFieldOffset = VM_Entrypoints.lockField.getOffset();
  private static int threadFieldOffset = VM_Entrypoints.lockThreadField.getOffset();
  private static int startFieldOffset = VM_Entrypoints.lockStartField.getOffset();
  private static int UNLOCKED = 0;
  private static int LOCKED = 1;
  private static double SLOW_THRESHOLD = 0.2; // seconds
  private static double TIME_OUT = 2.0; // seconds

  // Debugging
  private static final boolean REPORT_SLOW = true;
  private static int TIMEOUT_CHECK_FREQ = 1000; 
  public static int verbose = 0; // show who is acquiring and releasing the locks
  private static int lockCount = 0;

  // Instance fields
  private String name;        // logical name of lock
  private int id;             // lock id (based on a non-resetting counter)
  private int lock;           // state of lock
  private VM_Thread thread;   // if locked, who locked it?
  private double start;       // if locked, when was it locked?
  private int where = -1;     // how far along has the lock owner progressed?

  public Lock(String str) { 
    lock = UNLOCKED; 
    name = str;
    id = lockCount++;
  }

  // Try to acquire a lock and spin-wait until acquired.
  // (1) The isync at the end is important to prevent hardware instruction re-ordering
  //       from floating instruction below the acquire above the point of acquisition.
  // (2) A deadlock is presumed to have occurred if the number of retries exceeds MAX_RETRY.
  // (3) When a lock is acquired, the time of acquistion and the identity of acquirer is recorded.
  //
  public void acquire() {
    int retryCountdown = TIMEOUT_CHECK_FREQ;
    double localStart = 0.0; // Avoid getting time unnecessarily
    double lastSlowReport = 0.0;
    while (!VM_Synchronization.tryCompareAndSwap(this, lockFieldOffset, UNLOCKED, LOCKED)) {
      if (localStart == 0.0) 
	lastSlowReport = localStart = VM_Time.now();
      retryCountdown--;
      if (retryCountdown == 0) {
	retryCountdown = TIMEOUT_CHECK_FREQ;
	double now = VM_Time.now();
	double lastReportDuration = now - lastSlowReport;
	double waitTime = now - localStart;
	if (lastReportDuration > SLOW_THRESHOLD) {
	    lastSlowReport = now;
	    VM.sysWrite("GC Warning: possible slow or deadlock - failed to acquire lock ", id);
	    VM.sysWrite(" (", name);
	    VM.sysWrite(")  after ", 1000.0 * waitTime);
	    VM.sysWriteln(" ms");
	    VM_Thread t = thread;
	    if (t == null) 
		VM.sysWriteln("GC Warning: Locking thread unknown");
	    else {
		VM.sysWrite("GC Warning:  Locking thread: "); t.dump(1); 
		VM.sysWriteln(" at position ", where);
	    }
	}
	if (waitTime > TIME_OUT) {
	    VM.sysWrite("GC Warning: Locked out thread: "); 
	    VM_Thread.getCurrentThread().dump(1); 
	    VM_Scheduler.dumpStack();
	    VM.sysFail("Deadlock or someone holding on to lock for too long");
	}
      }
    }

    if (REPORT_SLOW) {
      setStart(VM_Time.now());
      setThread(VM_Thread.getCurrentThread());
    }

    if (verbose > 1) {
      VM.sysWrite("Thread ");
      thread.dump();
      VM.sysWrite(" acquired lock ", id);
      VM.sysWriteln(" ", name);
    }
    VM_Magic.isync();
  }

  public void check (int w) {
    if (!REPORT_SLOW) return;
    if (VM.VerifyAssertions) VM._assert(VM_Thread.getCurrentThread() == thread);
    double diff = (REPORT_SLOW) ? VM_Time.now() - start : 0.0;
    boolean show = (verbose > 1) || (diff > SLOW_THRESHOLD);
    if (show) {
      VM.sysWrite("GC Warning: Thread ");
      thread.dump();
      VM.sysWrite(" reached point ", w);
      VM.sysWrite(" while holding lock ", id);
      VM.sysWrite(" ", name);
      VM.sysWrite(" at ", 1000.0 * diff);
      VM.sysWriteln(" ms");
    }
    where = w;
  }

  // Release the lock with an atomic instruction.
  // (1) The sync is needed to flush changes made while the lock is held and also prevent 
  //        instructions floating into the critical section.
  // (2) When verbose, the amount of time the lock is ehld is printed.
  //
  public void release() {
    if (VM.VerifyAssertions) VM._assert(lock == LOCKED);
    double diff = (REPORT_SLOW) ? VM_Time.now() - start : 0.0;
    boolean show = (verbose > 1) || (diff > SLOW_THRESHOLD);
    if (show) {
      VM.sysWrite("GC Warning: Thread ");
      thread.dump();
      VM.sysWrite(" released lock ", id);
      VM.sysWrite(" ", name);
      VM.sysWrite(" after ");
      VM.sysWrite(1000.0 * diff);
      VM.sysWriteln(" ms");
    }

    if (REPORT_SLOW) {
      setStart(0.0);
      setThread(null);
      where = -1;
    }
    VM_Magic.sync();
    boolean success = VM_Synchronization.tryCompareAndSwap(this, lockFieldOffset, LOCKED, UNLOCKED); // guarantees flushing
    if (VM.VerifyAssertions) VM._assert(success);
  }

  // want to avoid generating a putfield so as to avoid write barrier recursion
  private final void setStart(double start) throws VM_PragmaInline {
    VM_Magic.setDoubleAtOffset(this, startFieldOffset, start);
  }
  private final void setThread(VM_Thread thread) throws VM_PragmaInline {
    VM_Magic.setObjectAtOffset(this, threadFieldOffset, (Object) thread);
  }

}
