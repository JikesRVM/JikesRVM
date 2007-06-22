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
package org.mmtk.utility;

import org.mmtk.vm.SynchronizedCounter;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class implements barrier synchronization.
 * The mechanism handles proper resetting by usnig 3 underlying counters
 * and supports unconditional blocking until the number of participants
 * can be determined.
 */
@Uninterruptible public final class Barrier {

  public static final int verbose = 0;

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
  private static final int TIME_CHECK = 1000000;   // Check time every TIME_CHECK-th iteration
  private static long WARN_PERIOD =  Long.MAX_VALUE; // set to a real value by fullyBooted
  private static long TIME_OUT =  Long.MAX_VALUE; // set to a real value by fullyBooted

  public static void fullyBooted() {
    WARN_PERIOD = VM.statistics.secsToCycles(3);   // Print msg every WARN_PERIOD seconds
    TIME_OUT = 10 * WARN_PERIOD; // Die after TIME_OUT seconds
  }

  public Barrier() {
    counters = new SynchronizedCounter[NUM_COUNTERS];
    for (int i = 0; i < NUM_COUNTERS; i++)
      counters[i] = VM.newSynchronizedCounter();
    currentCounter = VM.newSynchronizedCounter();
  }

  // Set target to appropriate value
  //
  public void setTarget(int t) {
    VM.memory.isync();
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(t >= 0);
    target = t + 1;
    VM.memory.sync();
  }

  public void clearTarget() {
    VM.memory.isync();
    target = -1;
    VM.memory.sync();
  }

  // Returns whether caller was first to arrive.
  // The coding to ensure resetting is delicate.
  //
  public int arrive(int where) {
    VM.memory.isync();
    int cur = currentCounter.peek();
    SynchronizedCounter c = counters[cur];
    int myValue = c.increment();
    // Do NOT use the currentCounter variables unless designated thread
    if (verbose >= 1) {
      Log.write(where); Log.write(": myValue = "); Log.writeln(myValue);
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(myValue >= 0 && (target == -1 || myValue <= target));
    if (myValue + 2 == target) {
      // last one to show up
      int next = (cur + 1) % NUM_COUNTERS;
      int prev = (cur - 1 + NUM_COUNTERS) % NUM_COUNTERS;
      counters[prev].reset();       // everyone has seen the value so safe to reset now
      if (next == 0)
        currentCounter.reset(); // everyone has arrived but still waiting
      else
        currentCounter.increment();
      c.increment(); // now safe to let others past barrier
      // VM.sysWriteln("last guy done ", where);
      VM.memory.sync();
      return myValue;
    } else {
      // everyone else
      long startCheck = 0;
      long lastElapsed = 0;
      for (int i = 0;; i++) {
        if (target != -1 && c.peek() == target) {
          VM.memory.sync();
          return myValue;
        }
        if (((i - 1) % TIME_CHECK) == 0) {
          if (startCheck == 0) {
            startCheck = VM.statistics.cycles();
          } else {
            long elapsed = VM.statistics.cycles() - startCheck;
            if (elapsed - lastElapsed > WARN_PERIOD) {
              Log.write("GC Warning: Barrier wait has reached "); Log.write(VM.statistics.cyclesToSecs(elapsed));
              Log.write(" seconds.  Called from "); Log.write(where); Log.write(".  myOrder = "); Log.write(myValue);
              Log.write("  count is "); Log.write(c.peek()); Log.write(" waiting for "); Log.write(target - 1);
              Log.writeln();
              lastElapsed = elapsed;
            }
            if (elapsed > TIME_OUT)
              VM.assertions.fail("GC Error: Barrier Timeout");
          }
        }
      }
    }
  }

}
