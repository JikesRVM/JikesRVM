/*
 * (C) Copyright IBM Corp. 2003
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * This class implements barrier synchronization.
 * The mechanism handles proper resetting by usnig 3 underlying counters
 * and supports unconditional blocking until the number of participants
 * can be determined.
 *
 * @author   Perry Cheng
 */
public final class Barrier implements VM_Uninterruptible {

  public static int verbose = 0;

  // The value of target is one more than the number of threads we expect to arrive.
  // It is one greater to allow safely updating the currentCounter value.
  // If target is -1, no thread can proceed past the barrier.
  // 3 counters are needed to support proper resetting without race conditions.
  //
  private volatile int target = -1;  
  private static final int NUM_COUNTERS = 3;
  SynchronizedCounter [] counters;
  int currentCounter = 0;

  // Debugging constants
  private static final int TIME_CHECK = 1000000;   // Check time every TIME_CHECK-th iteration
  private static final double WARN_PERIOD = 3.0;   // Print msg every WARN_PERIOD seconds
  private static final double TIME_OUT    = 30.0;  // Die after TIME_OUT seconds

  public Barrier () {
    counters = new SynchronizedCounter[NUM_COUNTERS];
    for (int i=0; i<NUM_COUNTERS; i++)
      counters[i] = new SynchronizedCounter();
  }

  // Set target to appropriate value
  //
  public void setTarget (int t) {
    if (VM.VerifyAssertions) VM._assert(t >= 0);
    target = t + 1;
  }

  public void clearTarget () {
    target = -1;
  }

  // Returns whether caller was first to arrive.
  // The coding to ensure resetting is delicate.
  //
  public int arrive (int where) {
    SynchronizedCounter c = counters[currentCounter];
    int myValue = c.increment();
    // Do NOT use the currentCounter variables unless designated thread
    if (verbose >= 1) VM.sysWriteln("", where, ": myValue = ", myValue);
    if (VM.VerifyAssertions) VM._assert(myValue >= 0 && (target == -1 || myValue <= target));
    if (myValue + 2 == target) { // last one to show up
      int nextCounter = (currentCounter + 1) % NUM_COUNTERS;
      int prevCounter = (currentCounter - 1 + NUM_COUNTERS) % NUM_COUNTERS; 
      counters[prevCounter].reset();  // everyone has seen the value so safe to reset now
      currentCounter = nextCounter;   // everyone has arrived but still waiting
      c.increment();                  // now safe to let others past barrier
      // VM.sysWriteln("last guy done ", where);
      return myValue;
    }
    else {                       // everyone else
      double startCheck = 0.0;
      double lastElapsed = 0.0;
      for (int i=0; ; i++) {
	if (target != -1 && c.peek() == target) 
	  return myValue;
	if (((i - 1) % TIME_CHECK) == 0) {
	  if (startCheck == 0.0) 
	    startCheck = VM_Interface.now();
	  else {
	    double elapsed = VM_Interface.now() - startCheck;
	    if (elapsed - lastElapsed > WARN_PERIOD) {
	      VM.sysWrite("GC Warning: Barrier wait has reached ", elapsed);
	      VM.sysWrite(" seconds.  Called from ", where, ".  myOrder = ", myValue);
	      VM.sysWriteln("  count is ", c.peek(), " waiting for ", target - 1);
	      lastElapsed = elapsed;
	    }
	    if (elapsed > TIME_OUT)
	      VM.sysFail("GC Error: Barrier Timeout");
	  }
	}
      }
    }
  }

}
