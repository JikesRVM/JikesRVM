/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * This class provides a set of static method entrypoints used in the
 * implementation of standard library thread operations.
 *
 * @author Stephen Fink
 */

package com.ibm.JikesRVM.librarySupport;
import VM_Thread;

public class ThreadSupport {
  /**
   * Suspend execution of current thread for specified number of seconds 
   * (or fraction).
   */ 
  public static void sleep (long millis) throws InterruptedException {
    VM_Thread.sleep(millis);
  }

  /**
   * Suspend execution of current thread, in favor of some other thread.
   */ 
  public static void yield () {
    VM_Thread.yield();
  }

  /**
   * Get current thread.
   */ 
  public static Thread getCurrentThread() {
    return (Thread)VM_Thread.getCurrentThread();
  }
}
