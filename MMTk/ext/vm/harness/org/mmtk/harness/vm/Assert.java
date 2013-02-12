/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.harness.vm;

import org.vmmagic.pragma.Uninterruptible;

/**
 * MMTk Harness implementation of Assert
 */
@Uninterruptible
public class Assert extends org.mmtk.vm.Assert {

  /**
   * Used from within the interface to indicate features that are not implemented yet.
   */
  public static void notImplemented() {
    throw new UnsupportedOperationException("Not Implemented");
  }

  @Override
  public void fail(String message) {
    throw new RuntimeException("Assertion Failed: " + message);
  }

  @Override
  public void _assert(boolean cond) {
    if (!cond) fail("");
  }

  @Override
  public void _assert(boolean cond, String message) {
    if (!cond) fail(message);
  }

  @Override
  public void dumpStack() {
    new Exception().printStackTrace();
  }

  /**
   * Checks if the virtual machine is running.  This value changes, so
   * the call-through to the VM must be a method.  In Jikes RVM, just
   * returns VM.runningVM.
   *
   * @return <code>true</code> if the virtual machine is running
   */
  public boolean runningVM() {
    return true;
  }

  @Override
  protected boolean getVerifyAssertionsConstant() {
    String value = System.getProperty("org.mmtk.harness.verify.assertions", "true");
    return Boolean.valueOf(value);
  }
}
