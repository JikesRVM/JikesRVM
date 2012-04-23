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
package org.jikesrvm.compilers.baseline.ia32;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.baseline.BaselineCompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.ia32.BaselineConstants;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.ExceptionDeliverer;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Handle exception delivery and stack unwinding for methods compiled by
 * baseline compiler.
 */
public abstract class BaselineExceptionDeliverer extends ExceptionDeliverer implements BaselineConstants {

  /**
   * Pass control to a catch block.
   */
  @Override
  @Unpreemptible("Deliver exception possibly from unpreemptible code")
  public void deliverException(CompiledMethod compiledMethod, Address catchBlockInstructionAddress,
                               Throwable exceptionObject, ArchitectureSpecific.Registers registers) {
    Address fp = registers.getInnermostFramePointer();
    NormalMethod method = (NormalMethod) compiledMethod.getMethod();
    RVMThread myThread = RVMThread.getCurrentThread();

    // reset sp to "empty expression stack" state
    //
    Address sp = fp.plus(BaselineCompilerImpl.getEmptyStackOffset(method));

    // push exception object as argument to catch block
    //
    sp = sp.minus(BYTES_IN_ADDRESS);
    sp.store(Magic.objectAsAddress(exceptionObject));
    registers.gprs.set(SP.value(), sp.toWord());

    // set address at which to resume executing frame
    registers.ip = catchBlockInstructionAddress;

    // branch to catch block
    //
    VM.enableGC(); // disabled right before RuntimeEntrypoints.deliverException was called
    if (VM.VerifyAssertions) VM._assert(registers.inuse);

    registers.inuse = false;

    // 'give back' the portion of the stack we borrowed to run
    // exception delivery code when invoked for a hardware trap.
    // If this was a straight software trap (athrow) then setting
    // the stacklimit should be harmless, since the stacklimit should already have exactly
    // the value we are setting it too.
    myThread.stackLimit = Magic.objectAsAddress(myThread.getStack()).plus(STACK_SIZE_GUARD);
    Magic.restoreHardwareExceptionState(registers);
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
  }

  /**
   * Unwind a stackframe.
   */
  @Override
  @Unpreemptible("Unwind stack possibly from unpreemptible code")
  public void unwindStackFrame(CompiledMethod compiledMethod, ArchitectureSpecific.Registers registers) {
    NormalMethod method = (NormalMethod) compiledMethod.getMethod();
    Address fp = registers.getInnermostFramePointer();
    if (method.isSynchronized()) { // release the lock, if it is being held
      Address ip = registers.getInnermostInstructionAddress();
      Offset instr = compiledMethod.getInstructionOffset(ip);
      Offset lockOffset = ((BaselineCompiledMethod) compiledMethod).getLockAcquisitionOffset();
      if (instr.sGT(lockOffset)) { // we actually have the lock, so must unlock it.
        Object lock;
        if (method.isStatic()) {
          lock = method.getDeclaringClass().getResolvedClassForType();
        } else {
          lock =
              Magic.addressAsObject(fp.plus(BaselineCompilerImpl.locationToOffset(((BaselineCompiledMethod) compiledMethod).getGeneralLocalLocation(
                  0)) - BYTES_IN_ADDRESS).loadAddress());
        }
        if (ObjectModel.holdsLock(lock, RVMThread.getCurrentThread())) {
          ObjectModel.genericUnlock(lock);
        }
      }
    }
    // Restore nonvolatile registers used by the baseline compiler.
    if (VM.VerifyAssertions) VM._assert(SAVED_GPRS == 2);
    registers.gprs.set(EDI.value(), fp.plus(EDI_SAVE_OFFSET).loadWord());
    registers.gprs.set(EBX.value(), fp.plus(EBX_SAVE_OFFSET).loadWord());
    if (method.hasBaselineSaveLSRegistersAnnotation()) {
      registers.gprs.set(EBP.value(), fp.plus(EBP_SAVE_OFFSET).toWord());
    }

    registers.unwindStackFrame();
  }
}


