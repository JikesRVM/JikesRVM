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
package org.jikesrvm.compilers.opt.runtimesupport.ppc;

import org.jikesrvm.ArchitectureSpecific.Registers;
import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.classloader.BytecodeConstants;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.runtime.ExceptionDeliverer;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Handle exception delivery and stack unwinding for
 * opt compiled methods.
 */
public abstract class OptExceptionDeliverer extends ExceptionDeliverer
    implements Constants, BytecodeConstants {

  /**
   * Pass control to a catch block.
   */
  @Override
  @Unpreemptible("Deliver exception possibly from unpreemptible code")
  public void deliverException(CompiledMethod cm, Address catchBlockInstructionAddress, Throwable exceptionObject,
                               Registers registers) {

    // store exception object for later retrieval by catch block
    OptCompiledMethod compiledMethod = (OptCompiledMethod) cm;
    Offset offset = Offset.fromIntSignExtend(compiledMethod.getUnsignedExceptionOffset());
    if (!offset.isZero()) {
      // only put the exception object in the stackframe if the catch block is expecting it.
      // (if the method hasn't allocated a stack slot for caught exceptions, then we can safely
      //  drop the exceptionObject on the floor).
      Address fp = registers.getInnermostFramePointer();
      Magic.setObjectAtOffset(Magic.addressAsObject(fp), offset, exceptionObject);
    }

    // set address at which to resume executing frame
    registers.ip = catchBlockInstructionAddress;
    VM.enableGC(); // disabled right before Runtime.deliverException was called

    if (VM.VerifyAssertions) VM._assert(registers.inuse);
    registers.inuse = false;

    // "branches" to catchBlockInstructionAddress
    Magic.restoreHardwareExceptionState(registers);
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
  }

  /**
   * Unwind a stackframe.
   */
  @Override
  @Unpreemptible("Deliver exception possibly from unpreemptible code")
  public void unwindStackFrame(CompiledMethod cm, Registers registers) {
    Address fp = registers.getInnermostFramePointer();
    OptCompiledMethod compiledMethod = (OptCompiledMethod) cm;

    // restore non-volatile registers
    Offset frameOffset = Offset.fromIntSignExtend(compiledMethod.getUnsignedNonVolatileOffset());
    int firstInteger = compiledMethod.getFirstNonVolatileGPR();
    if (firstInteger >= 0) {
      if (VM.BuildFor64Addr) {
        frameOffset = frameOffset.plus(7).toWord().and(Word.fromIntSignExtend(~7)).toOffset();
      }
      for (int i = firstInteger; i < 32; i++) {
        registers.gprs.set(i, fp.loadWord(frameOffset));
        frameOffset = frameOffset.plus(BYTES_IN_ADDRESS);
      }
    }
    int firstFloat = compiledMethod.getFirstNonVolatileFPR();
    if (firstFloat >= 0) {
      frameOffset = frameOffset.plus(7).toWord().and(Word.fromIntSignExtend(~7)).toOffset();
      for (int i = firstFloat; i < 32; i++) {
        long temp = Magic.getLongAtOffset(Magic.addressAsObject(fp), frameOffset);
        registers.fprs[i] = Magic.longBitsAsDouble(temp);
        frameOffset = frameOffset.plus(BYTES_IN_DOUBLE);
      }
    }

    registers.unwindStackFrame();
  }
}



