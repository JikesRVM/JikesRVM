/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.baseline.ia32;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.baseline.VM_BaselineCompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.ia32.VM_BaselineConstants;
import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.runtime.VM_ExceptionDeliverer;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Processor;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.VM_Thread;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Handle exception delivery and stack unwinding for methods compiled by
 * baseline compiler.
 */
public abstract class VM_BaselineExceptionDeliverer extends VM_ExceptionDeliverer implements VM_BaselineConstants {

  /**
   * Pass control to a catch block.
   */
  @Override
  public void deliverException(VM_CompiledMethod compiledMethod, Address catchBlockInstructionAddress,
                               Throwable exceptionObject, ArchitectureSpecific.VM_Registers registers) {
    Address fp = registers.getInnermostFramePointer();
    VM_NormalMethod method = (VM_NormalMethod) compiledMethod.getMethod();
    VM_Thread myThread = VM_Scheduler.getCurrentThread();

    // reset sp to "empty expression stack" state
    //
    Address sp = fp.plus(VM_Compiler.getEmptyStackOffset(method));

    // push exception object as argument to catch block
    //
    sp = sp.minus(BYTES_IN_ADDRESS);
    sp.store(VM_Magic.objectAsAddress(exceptionObject));
    registers.gprs.set(SP.value(), sp.toWord());

    // set address at which to resume executing frame
    registers.ip = catchBlockInstructionAddress;

    // branch to catch block
    //
    VM.enableGC(); // disabled right before VM_Runtime.deliverException was called
    if (VM.VerifyAssertions) VM._assert(registers.inuse);

    registers.inuse = false;

    // 'give back' the portion of the stack we borrowed to run
    // exception delivery code when invoked for a hardware trap.
    // If this was a straight software trap (athrow) then setting
    // the stacklimit should be harmless, since the stacklimit should already have exactly
    // the value we are setting it too.
    myThread.stackLimit = VM_Magic.objectAsAddress(myThread.getStack()).plus(STACK_SIZE_GUARD);
    VM_Processor.getCurrentProcessor().activeThreadStackLimit = myThread.stackLimit;

    VM_Magic.restoreHardwareExceptionState(registers);
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
  }

  /**
   * Unwind a stackframe.
   */
  public void unwindStackFrame(VM_CompiledMethod compiledMethod, ArchitectureSpecific.VM_Registers registers) {
    VM_NormalMethod method = (VM_NormalMethod) compiledMethod.getMethod();
    Address fp = registers.getInnermostFramePointer();
    if (method.isSynchronized()) { // release the lock, if it is being held
      Address ip = registers.getInnermostInstructionAddress();
      Offset instr = compiledMethod.getInstructionOffset(ip);
      Offset lockOffset = ((VM_BaselineCompiledMethod) compiledMethod).getLockAcquisitionOffset();
      if (instr.sGT(lockOffset)) { // we actually have the lock, so must unlock it.
        Object lock;
        if (method.isStatic()) {
          lock = method.getDeclaringClass().getClassForType();
        } else {
          lock =
              VM_Magic.addressAsObject(fp.plus(VM_Compiler.locationToOffset(((VM_BaselineCompiledMethod) compiledMethod).getGeneralLocalLocation(
                  0)) - BYTES_IN_ADDRESS).loadAddress());
        }
        if (VM_ObjectModel.holdsLock(lock, VM_Scheduler.getCurrentThread())) {
          VM_ObjectModel.genericUnlock(lock);
        }
      }
    }
    // Restore nonvolatile registers used by the baseline compiler.
    if (VM.VerifyAssertions) VM._assert(SAVED_GPRS == 2);
    registers.gprs.set(EDI.value(), fp.plus(EDI_SAVE_OFFSET).loadWord());
    registers.gprs.set(EBX.value(), fp.plus(EBX_SAVE_OFFSET).loadWord());

    registers.unwindStackFrame();
  }
}


