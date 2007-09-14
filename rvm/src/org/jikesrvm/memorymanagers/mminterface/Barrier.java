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
import org.jikesrvm.mm.mmtk.SynchronizedCounter;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Time;
import org.vmmagic.pragma.Uninterruptible;

/**
 * This class implements barrier synchronization.
 * The mechanism handles proper resetting by usnig 3 underlying counters
 * and supports unconditional blocking until the number of participants
 * can be determined.
 */
@Uninterruptible
final class Barrier {

  public static final int VERBOSE = 0;

  // The value of target is one more than the number of threads we expect to arrive.
  // It is one greater to allow safely updating the currentCounter value.
  // If target is -1, no thread can proceed past the barrier.
  // 3 counters are needed to support proper resetting without race conditions.
  //
  private volatile int target = -1;
  private static final int NUM_COUNTERS = 3;
  final SynchronizedCounter[] counters;
  SynchronizedCounter currentCounter;

  // Debugging constants
  private static final long WARN_PERIOD =  VM_Time.secsToNanos(20); // Print msg every WARN_PERIOD seconds
  private static final long TIME_OUT =  3 * WARN_PERIOD; // Die after TIME_OUT seconds;

  public Barrier() {
    counters = new SynchronizedCounter[NUM_COUNTERS];
    for (int i = 0; i < NUM_COUNTERS; i++) {
      counters[i] = new SynchronizedCounter();
    }
    currentCounter = new SynchronizedCounter();
  }

  // Set target to appropriate value
  //
  public void setTarget(int t) {
    VM_Magic.isync();
    if (VM.VerifyAssertions) VM._assert(t >= 0);
    target = t + 1;
    VM_Magic.sync();
  }

  public void clearTarget() {
    VM_Magic.isync();
    target = -1;
    VM_Magic.sync();
  }

  // Returns whether caller was first to arrive.
  // The coding to ensure resetting is delicate.
  //
  public int arrive(int where) {
    VM_Magic.isync();
    int cur = currentCounter.peek();
    SynchronizedCounter c = counters[cur];
    int myValue = c.increment();
    // Do NOT use the currentCounter variables unless designated thread
    if (VERBOSE >= 1) {
      VM.sysWriteln(where,": myValue = ",myValue);
    }
    if (VM.VerifyAssertions) VM._assert(myValue >= 0 && (target == -1 || myValue <= target));
    if (myValue + 2 == target) {
      // last one to show up
      int next = (cur + 1) % NUM_COUNTERS;
      int prev = (cur - 1 + NUM_COUNTERS) % NUM_COUNTERS;
      counters[prev].reset();       // everyone has seen the value so safe to reset now
      if (next == 0) {
        currentCounter.reset(); // everyone has arrived but still waiting
      } else {
        currentCounter.increment();
      }
      c.increment(); // now safe to let others past barrier
      VM_Magic.sync();
      return myValue;
    } else {
      // everyone else
      long startNano = 0;
      long lastElapsedNano = 0;
      while (true) {
        long startCycles = VM_Time.cycles();
        long endCycles = startCycles + ((long) 1e9); // a few hundred milliseconds more or less.
        long nowCycles;
        do {
          if (target != -1 && c.peek() == target) {
            VM_Magic.sync();
            return myValue;
          }
          nowCycles = VM_Time.cycles();
        } while (startCycles < nowCycles && nowCycles < endCycles); /* check against both ends to guard against CPU migration */

        /*
         * According to the cycle counter, we've been spinning for a while.
         * Time to check nanoTime and see if we should print a warning and/or sysFail.
         */
        if (startNano == 0) {
          startNano = VM_Time.nanoTime();
        } else {
          long nowNano = VM_Time.nanoTime();
          long elapsedNano = nowNano - startNano;
          if (elapsedNano - lastElapsedNano > WARN_PERIOD) {
            VM.sysWrite("GC Warning: Barrier wait has reached ",VM_Time.nanosToSecs(elapsedNano),
                        " seconds.  Called from ");
            VM.sysWrite(where,".  myOrder = ",myValue,"  count is ");
            VM.sysWriteln(c.peek()," waiting for ",target - 1);
            lastElapsedNano = elapsedNano;
          }
          if (elapsedNano > TIME_OUT) {
            VM.sysFail("GC Error: Barrier Timeout");
          }
        }
      }
    }
  }
}
