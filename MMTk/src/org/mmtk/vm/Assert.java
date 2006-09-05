/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package org.mmtk.vm;

import org.vmmagic.pragma.Uninterruptible;

/**
 * $Id: Assert.java,v 1.5 2006/06/21 07:38:13 steveb-oss Exp $
 * 
 * @author Steve Blackburn
 * @author Perry Cheng
 * 
 * @version $Revision: 1.5 $
 * @date $Date: 2006/06/21 07:38:13 $
 */
public abstract class Assert implements Uninterruptible {
  /**
   * Logs a message and traceback, then exits.
   * 
   * @param message the string to log
   */
  public abstract void fail(String message);

  /**
   * Checks that the given condition is true.  If it is not, this
   * method does a traceback and exits.  All calls to this method
   * must be guarded by <code>VM.VERIFY_ASSERTIONS</code>.
   * 
   * @param cond the condition to be checked
   */
  public abstract void _assert(boolean cond);

  /**
   * Checks that the given condition is true.  If it is not, this
   * method prints a message, does a traceback and exits. All calls
   * to this method must be guarded by <code>VM.VERIFY_ASSERTIONS</code>.
   * 
   * @param cond the condition to be checked
   * @param message the message to print
   */
  public abstract void _assert(boolean cond, String message);

  /**
   * Print a stack trace
   */
  public abstract void dumpStack();

  /**
   * Throw an out of memory exception.
   */
  public abstract void failWithOutOfMemoryError();

  /**
   * Checks if the virtual machine is running.  This value changes, so
   * the call-through to the VM must be a method.  In Jikes RVM, just
   * returns VM.runningVM.
   * 
   * @return <code>true</code> if the virtual machine is running
   */
  public abstract boolean runningVM();
  
  /**
   * <code>true</code> if assertions should be verified
   * 
   * This must be implemented by subclasses, but is never called by MMTk users.
   */
  protected abstract boolean getVerifyAssertionsConstant();

  /**
   * NOTE: This method should not be called by anything other than the
   * reflective mechanisms in org.mmtk.vm.VM, and is not implemented by
   * subclasses.
   * 
   * This hack exists only to allow us to declare getVerifyAssertions() as 
   * a protected method.
   */
  static boolean verifyAssertionsTrapdoor(Assert a) {
    return a.getVerifyAssertionsConstant();
  }
}
