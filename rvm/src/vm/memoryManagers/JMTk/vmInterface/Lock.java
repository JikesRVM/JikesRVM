/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package org.mmtk.vm;

//import com.ibm.JikesRVM.memoryManagers.mmInterface.Constants;
//import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_Interface;

import com.ibm.JikesRVM.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_Synchronization;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Time;


/**
 * Simple, fair locks with deadlock detection.
 *
 * The implementation mimics a deli-counter and consists of two values: 
 * the ticket dispenser and the now-serving display, both initially zero.
 * Acquiring a lock involves grabbing a ticket number from the dispenser
 * using a fetchAndIncrement and waiting until the ticket number equals
 * the now-serving display.  On release, the now-serving display is
 * also fetchAndIncremented.
 * 
 * This implementation relies on there being less than 1<<32 waiters.
 * 
 * @author Perry Cheng
 * @version $Revision$
 * @date $Date$
 */
public class Lock implements Uninterruptible {

  // Internal class fields
  private static int dispenserFieldOffset = VM_Entrypoints.dispenserField.getOffset();
  private static int servingFieldOffset = VM_Entrypoints.servingField.getOffset();
  private static int threadFieldOffset = VM_Entrypoints.lockThreadField.getOffset();
  private static int startFieldOffset = VM_Entrypoints.lockStartField.getOffset();
  private static long SLOW_THRESHOLD = Long.MAX_VALUE; // set to a real value by fullyBooted
  private static long TIME_OUT = Long.MAX_VALUE;       // set to a real value by fullyBooted

  // Debugging
  private static final boolean REPORT_SLOW = true;
  private static int TIMEOUT_CHECK_FREQ = 1000; 
  public static int verbose = 0; // show who is acquiring and releasing the locks
  private static int lockCount = 0;

  // Core Instance fields
  private String name;        // logical name of lock
  private int id;             // lock id (based on a non-resetting counter)
  private int dispenser;      // ticket number of next customer
  private int serving;        // number of customer being served
  // Diagnosis Instance fields
  private VM_Thread thread;   // if locked, who locked it?
  private long start;         // if locked, when was it locked?
  private int where = -1;     // how far along has the lock owner progressed?
  private int[] servingHistory = new int[100];
  private int[] tidHistory = new int[100];
  private long[] startHistory = new long[100];
  private long[] endHistory = new long[100];

  public static void fullyBooted() {
    SLOW_THRESHOLD = VM_Time.millisToCycles(200); // cycles for .2 seconds
    TIME_OUT = 10 * SLOW_THRESHOLD; 
  }

  public Lock(String str) { 
    dispenser = serving = 0;
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

    int ticket = VM_Synchronization.fetchAndAdd(this, dispenserFieldOffset, 1);

    int retryCountdown = TIMEOUT_CHECK_FREQ;
    long localStart = 0; // Avoid getting time unnecessarily
    long lastSlowReport = 0;

    while (ticket != serving) {
      if (localStart == 0) lastSlowReport = localStart = VM_Time.cycles();
      if (--retryCountdown == 0) {
        retryCountdown = TIMEOUT_CHECK_FREQ;
        long now = VM_Time.cycles();
        long lastReportDuration = now - lastSlowReport;
        long waitTime = now - localStart;
        if (lastReportDuration > 
            SLOW_THRESHOLD + VM_Time.millisToCycles(200 * (VM_Thread.getCurrentThread().getIndex() % 5))) {
            lastSlowReport = now;
            VM.sysWrite("GC Warning: slow/deadlock - thread ");
            VM_Thread.getCurrentThread().dump(1);
            VM.sysWrite(" with ticket ", ticket);
            VM.sysWrite(" failed to acquire lock ",id);
            VM.sysWrite(" (",name);
            VM.sysWrite(") serving ", serving);
            VM.sysWriteln(" after ",VM_Time.cyclesToMillis(waitTime)," ms");
            VM_Thread t = thread;
            if (t == null) 
              VM.sysWriteln("GC Warning: Locking thread unknown");
            else {
              VM.sysWrite("GC Warning: Locking thread: "); t.dump(1); 
              VM.sysWriteln(" at position ",where);
            }
            VM.sysWriteln("GC Warning: my start = ", localStart);
	    // Print the last 10 entries preceding serving
            for (int i=(serving + 90) % 100; i != serving; i = (i+1)%100) {
	      if (VM.VerifyAssertions) VM._assert(i >= 0 && i < 100);
	      VM.sysWrite("GC Warning: ");
	      VM.sysWrite(i, ": index ", servingHistory[i]);
	      VM.sysWrite("   tid ", tidHistory[i]);
	      VM.sysWrite("    start = ", startHistory[i]);
	      VM.sysWrite("    end = ", endHistory[i]);
	      VM.sysWriteln("    start-myStart = ", VM_Time.cyclesToMillis(startHistory[i] - localStart));
            }
        }
        if (waitTime > TIME_OUT) {
            VM.sysWrite("GC Warning: Locked out thread: "); 
            VM_Thread.getCurrentThread().dump(1); 
            VM_Scheduler.dumpStack();
            Assert.fail("Deadlock or someone holding on to lock for too long");
        }
      }
    }

    if (REPORT_SLOW) { 
      servingHistory[serving % 100] = serving;
      tidHistory[serving % 100] = VM_Thread.getCurrentThread().getIndex();
      startHistory[serving % 100] = VM_Time.cycles();
      setLocker(VM_Time.cycles(), VM_Thread.getCurrentThread(), -1);
    }

    if (verbose > 1) {
      VM.sysWrite("Thread ");
      thread.dump();
      VM.sysWrite(" acquired lock ",id);
      VM.sysWriteln(" ",name);
    }
    VM_Magic.isync();
  }

  public void check (int w) {
    if (!REPORT_SLOW) return;
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(VM_Thread.getCurrentThread() == thread);
    long diff = (REPORT_SLOW) ? VM_Time.cycles() - start : 0;
    boolean show = (verbose > 1) || (diff > SLOW_THRESHOLD);
    if (show) {
      VM.sysWrite("GC Warning: Thread ");
      thread.dump();
      VM.sysWrite(" reached point ",w);
      VM.sysWrite(" while holding lock ",id);
      VM.sysWrite(" ",name);
      VM.sysWrite(" at ", VM_Time.cyclesToMillis(diff));
      VM.sysWriteln(" ms");
    }
    where = w;
  }

  // Release the lock by incrementing serving counter.
  // (1) The sync is needed to flush changes made while the lock is held and also prevent 
  //        instructions floating into the critical section.
  // (2) When verbose, the amount of time the lock is ehld is printed.
  //
  public void release() {
    long diff = (REPORT_SLOW) ? VM_Time.cycles() - start : 0;
    boolean show = (verbose > 1) || (diff > SLOW_THRESHOLD);
    if (show) {
      VM.sysWrite("GC Warning: Thread ");
      thread.dump();
      VM.sysWrite(" released lock ",id);
      VM.sysWrite(" ",name);
      VM.sysWrite(" after ");
      VM.sysWrite(VM_Time.cyclesToMillis(diff));
      VM.sysWriteln(" ms");
    }

    if (REPORT_SLOW) {
      endHistory[serving % 100] = VM_Time.cycles();
      setLocker(0, null, -1);
    }

    VM_Magic.sync();
    VM_Synchronization.fetchAndAdd(this, servingFieldOffset, 1);
  }

  // want to avoid generating a putfield so as to avoid write barrier recursion
  private final void setLocker(long start, VM_Thread thread, int w) throws InlinePragma {
    VM_Magic.setLongAtOffset(this, startFieldOffset, start);
    VM_Magic.setObjectAtOffset(this, threadFieldOffset, (Object) thread);
    where = w;
  }

}
