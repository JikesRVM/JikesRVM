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
package org.jikesrvm.compilers.baseline.arm;

import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;
import static org.jikesrvm.arm.RegisterConstants.SP;

import org.jikesrvm.VM;
import org.jikesrvm.architecture.AbstractRegisters;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.ExceptionDeliverer;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 *  Handle exception delivery and stack unwinding for methods compiled
 * by baseline compiler.
 */
public final class BaselineExceptionDeliverer extends ExceptionDeliverer {

  /**
   * Pass control to a catch block.
   */
  @Override
  @Unpreemptible("Unwind stack possibly from unpreemptible code")
  public void deliverException(CompiledMethod compiledMethod, Address catchBlockInstructionAddress,
                               Throwable exceptionObject, AbstractRegisters registers) {
    Address fp = registers.getInnermostFramePointer();
    ArchBaselineCompiledMethod bcm = (ArchBaselineCompiledMethod) compiledMethod;

    // reset sp to "empty expression stack" state
    Address sp = fp.plus(bcm.getEmptyStackOffset());

    // push exception object as argument to catch block
    sp = sp.minus(BYTES_IN_ADDRESS);
    sp.store(Magic.objectAsAddress(exceptionObject));

    // set address at which to resume executing frame
    registers.setIP(catchBlockInstructionAddress);
    registers.getGPRs().set(SP.value(), sp.toWord());

    // branch to catch block
    VM.enableGC(); // disabled right before Runtime.deliverException was called
    if (VM.VerifyAssertions) VM._assert(registers.getInUse());

    registers.setInUse(false);
    Magic.restoreHardwareExceptionState(registers);
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /**
   * Unwind a stackframe.
   */
  @Override
  @Unpreemptible("Unwind stack possibly from unpreemptible code")
  public void unwindStackFrame(CompiledMethod compiledMethod, AbstractRegisters registers) {
    NormalMethod method = (NormalMethod) compiledMethod.getMethod();
    ArchBaselineCompiledMethod bcm = (ArchBaselineCompiledMethod) compiledMethod;
    if (method.isSynchronized()) {
      Address ip = registers.getInnermostInstructionAddress();
      Offset instr = compiledMethod.getInstructionOffset(ip);
      Offset lockOffset = bcm.getLockAcquisitionOffset();
      if (instr.sGT(lockOffset)) { // we actually have the lock, so must unlock it.
        Object lock;
        if (method.isStatic()) {
          lock = method.getDeclaringClass().getResolvedClassForType();
        } else {
          Address fp = registers.getInnermostFramePointer();
          Address addr = bcm.getLocalValueForExceptionDeliverer(0, registers, fp);
          lock = Magic.addressAsObject(addr);
        }

        if (ObjectModel.holdsLock(lock, RVMThread.getCurrentThread())) {
          ObjectModel.genericUnlock(lock);
        }
      }
    }
    // restore non-volatile registers
    Address fp = registers.getInnermostFramePointer();
    bcm.writeSavedRegisterValues(registers, fp);
    registers.unwindStackFrame();
  }
}
