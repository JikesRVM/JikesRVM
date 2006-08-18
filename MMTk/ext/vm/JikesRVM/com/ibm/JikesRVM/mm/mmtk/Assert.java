/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package com.ibm.JikesRVM.mm.mmtk;

import org.mmtk.policy.Space;

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Scheduler;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * $Id: Assert.java,v 1.3 2006/06/19 06:08:16 steveb-oss Exp $ 
 *
 * @author Steve Blackburn
 * @author Perry Cheng
 *
 * @version $Revision: 1.3 $
 * @date $Date: 2006/06/19 06:08:16 $
 */
public class Assert extends org.mmtk.vm.Assert implements Uninterruptible {
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

  public final void exit(int rc) throws UninterruptiblePragma {
    VM.sysExit(rc);
  }

  /**
   * Checks that the given condition is true.  If it is not, this
   * method does a traceback and exits.
   *
   * @param cond the condition to be checked
   */
  public final void _assert(boolean cond) throws InlinePragma {
    VM._assert(cond);
  }


  /**
   * <code>true</code> if assertions should be verified
   */
 /* public static final boolean VerifyAssertions = VM.VerifyAssertions; */

  public final void _assert(boolean cond, String s) throws InlinePragma {
    if (!cond) VM.sysWriteln(s);
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
  public final void failWithOutOfMemoryError()
    throws LogicallyUninterruptiblePragma, NoInlinePragma {
    failWithOutOfMemoryErrorStatic();
  }

  /**
   * Throw an out of memory exception.  If the context is one where
   * we're already dealing with a problem, first request some
   * emergency heap space.
   */
  public static final void failWithOutOfMemoryErrorStatic()
    throws LogicallyUninterruptiblePragma, NoInlinePragma {
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
