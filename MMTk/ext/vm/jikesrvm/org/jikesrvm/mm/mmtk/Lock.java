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
package org.jikesrvm.mm.mmtk;

import org.jikesrvm.VM;
import org.jikesrvm.VM_Services;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.scheduler.VM_Synchronization;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.VM_Thread;
import org.jikesrvm.runtime.VM_Time;

import org.mmtk.utility.Log;


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
 */
@Uninterruptible public class Lock extends org.mmtk.vm.Lock {

  // Internal class fields
  private static final Offset dispenserFieldOffset = VM_Entrypoints.dispenserField.getOffset();
  private static final Offset servingFieldOffset = VM_Entrypoints.servingField.getOffset();
  private static final Offset threadFieldOffset = VM_Entrypoints.lockThreadField.getOffset();
  
  /**
   * VM_Time.nanoTime is expensive and accurate; VM_Time.cycles is cheap but inaccurate.
   * How many cycles to we let go by in between calls to VM_Time.nanoTime?
   * To hold timing overhead to acceptable levels, this should be a number that is 
   * highly likely to correspond to at least 1 millisecond of real time.  
   */
  private static final long CHECK_NANOTIME_CYCLE_THRESHOLD = (long)1e7;  // (1e7 cycles / 4e9 cycles/sec == 2.5ms)
  
  /**
   * A lock operation is considered slow if it takes more than 200 milliseconds.
   * The value is represented in nanoSeconds (for use with VM_Time.nanoTime()).
   */
  private static long SLOW_THRESHOLD = 200 * ((long)1e6);
  
  /**
   * A lock operation times out if it takes more than 10x SLOW_THRESHOLD.
   * The value is represented in nanoSeconds (for use with VM_Time.nanoTime()).
   */
  private static long TIME_OUT = 10 * SLOW_THRESHOLD;

  // Debugging
  public static final boolean verbose = false; // show who is acquiring and releasing the locks
  private static int lockCount = 0;

  // Core Instance fields
  private String name;        // logical name of lock
  private final int id;       // lock id (based on a non-resetting counter)

  @SuppressWarnings({"unused", "UnusedDeclaration", "CanBeFinal"}) // Accessed via VM_EntryPoints
  @Entrypoint
  private int dispenser;      // ticket number of next customer
  @Entrypoint
  private int serving;        // number of customer being served
  
  // Diagnosis Instance fields
  @Entrypoint
  private VM_Thread thread;   // if locked, who locked it?
  private int where = -1;     // how far along has the lock owner progressed?

  public Lock(String name) {
    this();
    this.name = name;
  }

  public Lock() {
    dispenser = serving = 0;
    id = lockCount++;
  }

  public void setName(String str) {
    name = str;
  }

  // Try to acquire a lock and spin-wait until acquired.
  // (1) The isync at the end is important to prevent hardware instruction re-ordering
  //       from floating instruction below the acquire above the point of acquisition.
  // (2) A deadlock is presumed to have occurred if it takes more than TIME_OUT nanos to acquire the lock.
  //
  public void acquire() {

    int ticket = VM_Synchronization.fetchAndAdd(this, dispenserFieldOffset, 1);

    long approximateStartNano = 0;
    long lastSlowReportNano = 0;
    long lastSlowReportCycles = 0;

    while (ticket != serving) {
      long nowCycles = VM_Time.cycles();

      if (lastSlowReportCycles == 0) {
        lastSlowReportCycles = nowCycles;
      }

      // Take absolute value to protect against CPU migration & cycle counter skew.
      long delta = nowCycles - lastSlowReportCycles;
      if (delta < 0) {
        delta = - delta;
      }
      if (delta > CHECK_NANOTIME_CYCLE_THRESHOLD) {
        lastSlowReportCycles = nowCycles;
        long nowNano = VM_Time.nanoTime();
        if (approximateStartNano == 0) {
          approximateStartNano = nowNano;
          lastSlowReportNano = nowNano;
        }

        if (nowNano - lastSlowReportNano > SLOW_THRESHOLD) {
          lastSlowReportNano = nowNano;

          Log.write("GC Warning: slow/deadlock - thread ");
          writeThreadIdToLog(VM_Scheduler.getCurrentThread());
          Log.write(" with ticket "); Log.write(ticket);
          Log.write(" failed to acquire lock "); Log.write(id);
          Log.write(" ("); Log.write(name);
          Log.write(") serving "); Log.write(serving);
          Log.write(" after ");
          Log.write(VM_Time.nanosToMillis(nowNano - approximateStartNano)); Log.write(" ms");
          Log.writelnNoFlush();

          VM_Thread t = thread;
          if (t == null) {
            Log.writeln("GC Warning: Locking thread unknown", false);
          } else {
            Log.write("GC Warning: Locking thread: ");
            writeThreadIdToLog(t);
            Log.write(" at position ");
            Log.writeln(where, false);
          }
          Log.write("GC Warning: my start = ");
          Log.writeln(approximateStartNano, false);
          Log.flush();
        }

        if (nowNano - approximateStartNano > TIME_OUT) {
          Log.write("GC Warning: Locked out thread: ");
          writeThreadIdToLog(VM_Scheduler.getCurrentThread());
          Log.writeln();
          VM_Scheduler.dumpStack();
          VM.sysFail("Deadlock or someone holding on to lock for too long");
        }
      }
    }

    if (verbose) {
      Log.write("Thread ");
      writeThreadIdToLog(thread);
      Log.write(" acquired lock "); Log.write(id);
      Log.write(" "); Log.write(name);
      Log.writeln();
    }
    
    setLocker(VM_Scheduler.getCurrentThread(), -1);
    
    VM_Magic.isync();
  }

  public void check (int w) {
    if (VM.VerifyAssertions) VM._assert(VM_Scheduler.getCurrentThread() == thread);
    if (verbose) {
      Log.write("Thread ");
      writeThreadIdToLog(thread);
      Log.write(" reached point "); Log.write(w);
      Log.write(" while holding lock "); Log.write(id);
      Log.write(" "); 
      Log.writeln(name);
    }
    where = w;
  }

  // Release the lock by incrementing serving counter.
  // (1) The sync is needed to flush changes made while the lock is held and also prevent
  //        instructions floating into the critical section.
  //
  public void release() {
    if (verbose) {
      Log.write("Thread ");
      writeThreadIdToLog(thread);
      Log.write(" released lock "); Log.write(id);
      Log.write(" "); 
      Log.writeln(name);
    }

    setLocker(null, -1);

    VM_Magic.sync();
    VM_Synchronization.fetchAndAdd(this, servingFieldOffset, 1);
  }

  // want to avoid generating a putfield so as to avoid write barrier recursion
  @Inline
  private void setLocker(VM_Thread thread, int w) {
    VM_Magic.setObjectAtOffset(this, threadFieldOffset, thread);
    where = w;
  }

  /** Write thread <code>t</code>'s identifying info via the MMTk Log class.
   * Does not use any newlines, nor does it flush.
   *
   *  This function may be called during GC; it avoids write barriers and
   *  allocation.
   *
   *  @param t  The {@link VM_Thread} we are interested in.
   */
  private static void writeThreadIdToLog(VM_Thread t) {
    char[] buf = VM_Services.grabDumpBuffer();
    int len = t.dump(buf);
    Log.write(buf, len);
    VM_Services.releaseDumpBuffer();
  }
}
