/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package org.mmtk.vm;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Scheduler;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * $Id$ 
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 *
 * @version $Revision$
 * @date $Date$
 */
public class Assert implements Uninterruptible {
  public static final boolean VERIFY_ASSERTIONS = VM.VerifyAssertions;
  /**
   * Logs a message and traceback, then exits.
   *
   * @param message the string to log
   */
  public static void fail(String message) { 
    VM.sysFail(message); 
  }

  public static void exit(int rc) throws UninterruptiblePragma {
    VM.sysExit(rc);
  }

  /**
   * Checks that the given condition is true.  If it is not, this
   * method does a traceback and exits.
   *
   * @param cond the condition to be checked
   */
  public static void _assert(boolean cond) throws InlinePragma {
    if (VERIFY_ASSERTIONS) VM._assert(cond);
  }


  /**
   * <code>true</code> if assertions should be verified
   */
  public static final boolean VerifyAssertions = VM.VerifyAssertions;

  public static void _assert(boolean cond, String s) throws InlinePragma {
    if (VERIFY_ASSERTIONS) {
      if (!cond) VM.sysWriteln(s);
      VM._assert(cond);
    }
  }

  public static final void dumpStack() {
    VM_Scheduler.dumpStack();
  }

  /**
   * Throw an out of memory exception.
   */
  public static void failWithOutOfMemoryError()
    throws LogicallyUninterruptiblePragma, NoInlinePragma {
    throw new OutOfMemoryError();
  }

  /**
   * Checks if the virtual machine is running.  This value changes, so
   * the call-through to the VM must be a method.  In Jikes RVM, just
   * returns VM.runningVM.
   *
   * @return <code>true</code> if the virtual machine is running
   */
  public static boolean runningVM() { return VM.runningVM; }

}
