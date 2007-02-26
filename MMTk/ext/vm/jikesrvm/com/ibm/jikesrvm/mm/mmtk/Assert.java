/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package com.ibm.jikesrvm.mm.mmtk;

import org.mmtk.policy.Space;

import com.ibm.jikesrvm.memorymanagers.mminterface.MM_Interface;
import com.ibm.jikesrvm.VM;
import com.ibm.jikesrvm.VM_Scheduler;

import org.vmmagic.pragma.*;

/**
 * $Id$ 
 *
 * @author Steve Blackburn
 * @author Perry Cheng
 *
 * @version $Revision$
 * @date $Date$
 */
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
