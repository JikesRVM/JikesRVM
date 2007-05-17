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

import org.mmtk.policy.Space;

import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.VM;
import org.jikesrvm.scheduler.VM_Scheduler;

import org.vmmagic.pragma.*;

@Uninterruptible public class Assert extends org.mmtk.vm.Assert {
  /* wriggle-room to accommodate memory demands while handling failures */
  private static final int EMERGENCY_HEAP_REQ = 5<<20; // 5MB
  /**
   * <code>true</code> if assertions should be verified
   */
  protected final boolean getVerifyAssertionsConstant() { return VM.VerifyAssertions;}

  /**
   * This method should be called whenever an error is encountered.
   *
   * @param str A string describing the error condition.
   */
  public final void error(String str) {
    Space.printUsagePages();
    Space.printUsageMB();
    fail(str);
  }

  /**
   * Logs a message and traceback, then exits.
   *
   * @param message the string to log
   */
  public final void fail(String message) { 
    VM.sysFail(message); 
  }

  @Uninterruptible
  public final void exit(int rc) { 
    VM.sysExit(rc);
  }

  /**
   * Checks that the given condition is true.  If it is not, this
   * method does a traceback and exits. All calls to this method
   * must be guarded by <code>VM.VERIFY_ASSERTIONS</code>.
   *
   * @param cond the condition to be checked
   */
  @Inline
  public final void _assert(boolean cond) { 
    if (!org.mmtk.vm.VM.VERIFY_ASSERTIONS)
      VM.sysFail("All assertions must be guarded by VM.VERIFY_ASSERTIONS: please check the failing assertion");
    VM._assert(cond);
  }

  /**
   * Checks that the given condition is true.  If it is not, this
   * method prints a message, does a traceback and exits. All calls
   * to this method must be guarded by <code>VM.VERIFY_ASSERTIONS</code>.
   * 
   * @param cond the condition to be checked
   * @param message the message to print
   */
  @Inline
  public final void _assert(boolean cond, String message) { 
    if (!org.mmtk.vm.VM.VERIFY_ASSERTIONS)
      VM.sysFail("All assertions must be guarded by VM.VERIFY_ASSERTIONS: please check the failing assertion");
    if (!cond) VM.sysWriteln(message);
    VM._assert(cond);
  }

  public final void dumpStack() {
    VM_Scheduler.dumpStack();
  }

  /**
   * Throw an out of memory exception.  If the context is one where
   * we're already dealing with a problem, first request some
   * emergency heap space.
   */
  @LogicallyUninterruptible
  @NoInline
  public final void failWithOutOfMemoryError() { 
    failWithOutOfMemoryErrorStatic();
  }

  /**
   * Throw an out of memory exception.  If the context is one where
   * we're already dealing with a problem, first request some
   * emergency heap space.
   */
  @LogicallyUninterruptible
  @NoInline
  public static void failWithOutOfMemoryErrorStatic() {
    if (VM.doEmergencyGrowHeap)
      MM_Interface.emergencyGrowHeap(EMERGENCY_HEAP_REQ); // ask and pray
    throw new OutOfMemoryError();
  }

  /**
   * Checks if the virtual machine is running.  This value changes, so
   * the call-through to the VM must be a method.  In Jikes RVM, just
   * returns VM.runningVM.
   *
   * @return <code>true</code> if the virtual machine is running
   */
  public final boolean runningVM() { return VM.runningVM; }

}
