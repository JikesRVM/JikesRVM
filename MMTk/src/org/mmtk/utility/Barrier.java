/*
 * (C) Copyright IBM Corp. 2003
 */
//$Id$
package org.mmtk.utility;

import org.mmtk.vm.Assert;
import org.mmtk.vm.Statistics;
import org.mmtk.vm.SynchronizedCounter;
import org.mmtk.vm.Memory;

import org.vmmagic.pragma.*;

/**
 * This class implements barrier synchronization.
 * The mechanism handles proper resetting by usnig 3 underlying counters
 * and supports unconditional blocking until the number of participants
 * can be determined.
 *
 * @author   Perry Cheng
 */
public final class Barrier implements Uninterruptible {

  public static int verbose = 0;

  // The value of target is one more than the number of threads we expect to arrive.
  // It is one greater to allow safely updating the currentCounter value.
  // If target is -1, no thread can proceed past the barrier.
  // 3 counters are needed to support proper resetting without race conditions.
  //
  private volatile int target = -1;  
  private static final int NUM_COUNTERS = 3;
  SynchronizedCounter [] counters;
  SynchronizedCounter currentCounter;

  // Debugging constants
  private static final int TIME_CHECK = 1000000;   // Check time every TIME_CHECK-th iteration
  private static long WARN_PERIOD =  Long.MAX_VALUE; // set to a real value by fullyBooted
  private static long TIME_OUT =  Long.MAX_VALUE; // set to a real value by fullyBooted

  public static void fullyBooted() {
    WARN_PERIOD = Statistics.secsToCycles(3);   // Print msg every WARN_PERIOD seconds
    TIME_OUT    = 10 * WARN_PERIOD;               // Die after TIME_OUT seconds
  }

  public Barrier () {
    counters = new SynchronizedCounter[NUM_COUNTERS];
    for (int i=0; i<NUM_COUNTERS; i++)
      counters[i] = new SynchronizedCounter();
    currentCounter = new SynchronizedCounter();
  }

  // Set target to appropriate value
  //
  public void setTarget (int t) {
    Memory.isync();
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(t >= 0);
    target = t + 1;
    Memory.sync();
  }

  public void clearTarget () {
    Memory.isync();
    target = -1;
    Memory.sync();
  }

  // Returns whether caller was first to arrive.
  // The coding to ensure resetting is delicate.
  //
  public int arrive (int where) {
    Memory.isync();
    int cur = currentCounter.peek();
    SynchronizedCounter c = counters[cur];
    int myValue = c.increment();
    // Do NOT use the currentCounter variables unless designated thread
    if (verbose >= 1) { 
      Log.write(where); Log.write(": myValue = "); Log.writeln(myValue);
    }
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(myValue >= 0 && (target == -1 || myValue <= target));
    if (myValue + 2 == target) { 
      // last one to show up
      int next = (cur + 1) % NUM_COUNTERS;
      int prev = (cur - 1 + NUM_COUNTERS) % NUM_COUNTERS; 
      counters[prev].reset();       // everyone has seen the value so safe to reset now
      if (next == 0)
        currentCounter.reset();    // everyone has arrived but still waiting
      else
        currentCounter.increment();   
      c.increment();                // now safe to let others past barrier
      // VM.sysWriteln("last guy done ", where);
      Memory.sync();
      return myValue;
    } else {
      // everyone else
      long startCheck = 0;
      long lastElapsed = 0;
      for (int i=0; ; i++) {
        if (target != -1 && c.peek() == target) {
          Memory.sync();
          return myValue;
        }
        if (((i - 1) % TIME_CHECK) == 0) {
          if (startCheck == 0) {
            startCheck = Statistics.cycles();
          } else {
            long elapsed = Statistics.cycles() - startCheck;
            if (elapsed - lastElapsed > WARN_PERIOD) {
              Log.write("GC Warning: Barrier wait has reached "); Log.write(Statistics.cyclesToSecs(elapsed));
              Log.write(" seconds.  Called from "); Log.write(where); Log.write(".  myOrder = "); Log.write(myValue);
              Log.write("  count is "); Log.write(c.peek()); Log.write(" waiting for "); Log.write(target - 1);
              Log.writeln();
              lastElapsed = elapsed;
            }
            if (elapsed > TIME_OUT)
              Assert.fail("GC Error: Barrier Timeout");
          }
        }
      }
    }
  }

}
