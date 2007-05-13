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
package org.mmtk.vm;

import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible public abstract class Assert {
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
  
  /*
   * NOTE: The following methods must be implemented by subclasses of this
   * class, but are internal to the VM<->MM interface glue, so are never
   * called by MMTk users.
   */
   /** @return true if assertions should be verified */
  protected abstract boolean getVerifyAssertionsConstant();

  /*
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
