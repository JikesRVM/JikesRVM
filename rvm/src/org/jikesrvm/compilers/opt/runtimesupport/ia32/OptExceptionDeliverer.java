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
package org.jikesrvm.compilers.opt.runtimesupport.ia32;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import static org.jikesrvm.SizeConstants.BYTES_IN_ADDRESS;
import org.jikesrvm.ArchitectureSpecific.Registers;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.runtime.ExceptionDeliverer;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Handle exception delivery and stack unwinding for methods
 *  compiled by optimizing Compiler.
 */
public abstract class OptExceptionDeliverer extends ExceptionDeliverer
    implements ArchitectureSpecific.ArchConstants {

  private static final boolean TRACE = false;

  /**
   * Pass control to a catch block.
   */
  @Override
  @Unpreemptible("Deliver exception possibly from unpreemptible code")
  public void deliverException(CompiledMethod compiledMethod, Address catchBlockInstructionAddress,
                               Throwable exceptionObject, Registers registers) {
    OptCompiledMethod optMethod = (OptCompiledMethod) compiledMethod;
    Address fp = registers.getInnermostFramePointer();
    RVMThread myThread = RVMThread.getCurrentThread();

    if (TRACE) {
      VM.sysWrite("Frame size of ");
      VM.sysWrite(optMethod.getMethod());
      VM.sysWrite(" is ");
      VM.sysWrite(optMethod.getFrameFixedSize());
      VM.sysWrite("\n");
    }

    // reset sp to "empty params" state (ie same as it was after prologue)
    Address sp = fp.minus(optMethod.getFrameFixedSize());
    registers.gprs.set(STACK_POINTER.value(), sp.toWord());

    // store exception object for later retrieval by catch block
    int offset = optMethod.getUnsignedExceptionOffset();
    if (offset != 0) {
      // only put the exception object in the stackframe if the catch block is expecting it.
      // (if the method hasn't allocated a stack slot for caught exceptions, then we can safely
      //  drop the exceptionObject on the floor).
      Magic.setObjectAtOffset(Magic.addressAsObject(fp), Offset.fromIntSignExtend(-offset), exceptionObject);
      if (TRACE) {
        VM.sysWrite("Storing exception object ");
        VM.sysWrite(Magic.objectAsAddress(exceptionObject));
        VM.sysWrite(" at offset ");
        VM.sysWrite(offset);
        VM.sysWrite(" from framepoint ");
        VM.sysWrite(fp);
        VM.sysWrite("\n");
      }
    }

    if (TRACE) {
      VM.sysWrite("Registers before delivering exception in ");
      VM.sysWrite(optMethod.getMethod());
      VM.sysWrite("\n");
      for (GPR reg : GPR.values()) {
        VM.sysWrite(reg.toString());
        VM.sysWrite(" = ");
        VM.sysWrite(registers.gprs.get(reg.value()));
        VM.sysWrite("\n");
      }
    }

    // set address at which to resume executing frame
    registers.ip = catchBlockInstructionAddress;

    if (TRACE) {
      VM.sysWrite("Set ip to ");
      VM.sysWrite(registers.ip);
      VM.sysWrite("\n");
    }

    VM.enableGC(); // disabled right before RuntimeEntrypoints.deliverException was called

    if (VM.VerifyAssertions) VM._assert(registers.inuse);
    registers.inuse = false;

    // 'give back' the portion of the stack we borrowed to run
    // exception delivery code when invoked for a hardware trap.
    // If this was a straight software trap (athrow) then setting
    // the stacklimit should be harmless, since the stacklimit should already have exactly
    // the value we are setting it too.
    myThread.stackLimit = Magic.objectAsAddress(myThread.getStack()).plus(STACK_SIZE_GUARD);

    // "branches" to catchBlockInstructionAddress
    Magic.restoreHardwareExceptionState(registers);
    if (VM.VerifyAssertions) VM._assert(Constants.NOT_REACHED);
  }

  /**
   * Unwind a stackframe.
   */
  @Override
  @Unpreemptible("Unwind stack possibly from unpreemptible code")
  public void unwindStackFrame(CompiledMethod compiledMethod, Registers registers) {
    Address fp = registers.getInnermostFramePointer();
    OptCompiledMethod optMethod = (OptCompiledMethod) compiledMethod;

    if (TRACE) {
      VM.sysWrite("Registers before unwinding frame for ");
      VM.sysWrite(optMethod.getMethod());
      VM.sysWrite("\n");
      for (GPR reg : GPR.values()) {
        VM.sysWrite(reg.toString());
        VM.sysWrite(" = ");
        VM.sysWrite(registers.gprs.get(reg.value()));
        VM.sysWrite("\n");
      }
    }

    // restore non-volatile registers
    int frameOffset = optMethod.getUnsignedNonVolatileOffset();
    for (int i = optMethod.getFirstNonVolatileGPR(); i < NUM_NONVOLATILE_GPRS; i++, frameOffset += BYTES_IN_ADDRESS) {
      registers.gprs.set(NONVOLATILE_GPRS[i].value(), fp.minus(frameOffset).loadWord());
    }
    if (VM.VerifyAssertions) VM._assert(NUM_NONVOLATILE_FPRS == 0);

    registers.unwindStackFrame();

    if (TRACE) {
      VM.sysWrite("Registers after unwinding frame for ");
      VM.sysWrite(optMethod.getMethod());
      VM.sysWrite("\n");
      for (GPR reg : GPR.values()) {
        VM.sysWrite(reg.toString());
        VM.sysWrite(" = ");
        VM.sysWrite(registers.gprs.get(reg.value()));
        VM.sysWrite("\n");
      }
    }
  }
}
