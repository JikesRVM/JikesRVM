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
package org.jikesrvm.mm.mmtk;

import org.mmtk.policy.Space;

import org.jikesrvm.VM;
import org.jikesrvm.scheduler.RVMThread;

import org.vmmagic.pragma.*;

@Uninterruptible public class Assert extends org.mmtk.vm.Assert {
  @Override
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

  @Override
  public final void fail(String message) {
    Space.printUsagePages();
    Space.printUsageMB();
    VM.sysFail(message);
  }

  @Uninterruptible
  public final void exit(int rc) {
    VM.sysExit(rc);
  }

  @Override
  @Inline(value=Inline.When.AllArgumentsAreConstant)
  public final void _assert(boolean cond) {
    if (!org.mmtk.vm.VM.VERIFY_ASSERTIONS)
      VM.sysFail("All assertions must be guarded by VM.VERIFY_ASSERTIONS: please check the failing assertion");
    //CHECKSTYLE:OFF - Checkstyle assertion plugin would warn otherwise
    VM._assert(cond);
    //CHECKSTYLE:ON
  }

  @Override
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  public final void _assert(boolean cond, String message) {
    if (!org.mmtk.vm.VM.VERIFY_ASSERTIONS)
      VM.sysFail("All assertions must be guarded by VM.VERIFY_ASSERTIONS: please check the failing assertion");
    if (!cond) VM.sysWriteln(message);
    //CHECKSTYLE:OFF - Checkstyle assertion plugin would warn otherwise
    VM._assert(cond);
    //CHECKSTYLE:ON
  }

  @Override
  public final void dumpStack() {
    RVMThread.dumpStack();
  }
}
