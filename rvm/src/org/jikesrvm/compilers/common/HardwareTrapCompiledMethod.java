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
package org.jikesrvm.compilers.common;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.runtime.DynamicLink;
import org.jikesrvm.runtime.ExceptionDeliverer;
import org.jikesrvm.runtime.StackBrowser;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Offset;

/**
 * Information associated with artifical stackframe inserted by hardware
 * trap handler.
 */
final class HardwareTrapCompiledMethod extends CompiledMethod {

  public HardwareTrapCompiledMethod(int id, RVMMethod m) {
    super(id, m);
  }

  @Override
  @Uninterruptible
  public int getCompilerType() {
    return TRAP;
  }

  @Override
  public String getCompilerName() {
    return "<hardware trap>";
  }

  @Override
  @Uninterruptible
  public ExceptionDeliverer getExceptionDeliverer() {
    // this method should never get called, because exception delivery begins
    // at site of exception, which is one frame above artificial "trap" frame
    // corresponding to this compiler-info object
    //
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }

  @Override
  @Unpreemptible
  public int findCatchBlockForInstruction(Offset instructionOffset, RVMType exceptionType) {
    return -1;
  }

  @Override
  @Uninterruptible
  public void getDynamicLink(DynamicLink dynamicLink, Offset instructionOffset) {
    // this method should never get called, because exception delivery begins
    // at site of exception, which is one frame above artificial "trap" frame
    // corresponding to this compiler-info object
    //
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  @Override
  public boolean isWithinUninterruptibleCode(Offset instructionOffset) {
    return false;
  }

  @Override
  public void printStackTrace(Offset instructionOffset, org.jikesrvm.PrintLN out) {
    out.println("\tat <hardware trap>");
  }

  @Override
  public void set(StackBrowser browser, Offset instr) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  @Override
  public boolean up(StackBrowser browser) {
    return false;
  }

}
