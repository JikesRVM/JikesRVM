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
package org.jikesrvm.jni.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.ArchitectureSpecific.Registers;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.ia32.RegisterConstants.GPR;
import org.jikesrvm.runtime.ExceptionDeliverer;
import org.vmmagic.unboxed.Address;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Exception delivery mechanisms for JNI on IA32
 */
public class JNIExceptionDeliverer extends ExceptionDeliverer {

  /**
   * Deliver exception, not possible for JNI methods
   * @see org.jikesrvm.runtime.ExceptionDeliverer#deliverException(org.jikesrvm.compilers.common.CompiledMethod, org.vmmagic.unboxed.Address, java.lang.Throwable, org.jikesrvm.ArchitectureSpecific.Registers)
   */
  @Uninterruptible
  @Override
  public void deliverException(CompiledMethod compiledMethod,
      Address catchBlockInstructionAddress, Throwable exceptionObject,
      Registers registers) {
    // this method should never get called as native methods don't have catch blocks
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Unwind registers/stack through JNI method
   * @see org.jikesrvm.runtime.ExceptionDeliverer#unwindStackFrame(org.jikesrvm.compilers.common.CompiledMethod, org.jikesrvm.ArchitectureSpecific.Registers)
   */
  @Uninterruptible
  @Override
  public void unwindStackFrame(CompiledMethod compiledMethod,
      Registers registers) {
    Address fp = registers.getInnermostFramePointer();
    // Restore nonvolatile registers used by the JNI compiler
    registers.gprs.set(GPR.EDI.value(), fp.plus(JNICompiler.EDI_SAVE_OFFSET).loadWord());
    registers.gprs.set(GPR.EBX.value(), fp.plus(JNICompiler.EBX_SAVE_OFFSET).loadWord());
    registers.gprs.set(GPR.EBP.value(), fp.plus(JNICompiler.EBP_SAVE_OFFSET).loadWord());
    registers.unwindStackFrame();
  }

}
