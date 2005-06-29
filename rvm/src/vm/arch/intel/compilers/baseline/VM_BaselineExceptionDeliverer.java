/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

import org.vmmagic.unboxed.*;

/**
 * Handle exception delivery and stack unwinding for methods compiled by 
 * baseline compiler.
 *
 * @author Derek Lieber
 * @date 18 Sep 1998 
 */
class VM_BaselineExceptionDeliverer extends VM_ExceptionDeliverer 
   implements VM_BaselineConstants  {

  /**
   * Pass control to a catch block.
   */
  public void deliverException(VM_CompiledMethod compiledMethod,
                               Address        catchBlockInstructionAddress,
                               Throwable         exceptionObject,
                               VM_Registers      registers) {
    Address fp     = registers.getInnermostFramePointer();
    VM_NormalMethod method = (VM_NormalMethod)compiledMethod.getMethod();
    VM_Thread myThread = VM_Thread.getCurrentThread();

    // reset sp to "empty expression stack" state
    //
    Address sp = fp.add(VM_Compiler.getEmptyStackOffset(method));
    
    // push exception object as argument to catch block
    //
    sp = sp.sub(BYTES_IN_ADDRESS);
    sp.store(VM_Magic.objectAsAddress(exceptionObject));
    registers.gprs.set(SP, sp);

    // set address at which to resume executing frame
    registers.ip = catchBlockInstructionAddress;

    // branch to catch block
    //
    VM.enableGC(); // disabled right before VM_Runtime.deliverException was called
    if (VM.VerifyAssertions) VM._assert(registers.inuse == true); 

    registers.inuse = false;

    // 'give back' the portion of the stack we borrowed to run 
    // exception delivery code when invoked for a hardware trap.
    // If this was a straight software trap (athrow) then setting 
    // the stacklimit should be harmless, since the stacklimit should already have exactly
    // the value we are setting it too. 
    if (!myThread.hardwareExceptionRegisters.inuse) {
      myThread.stackLimit = VM_Magic.objectAsAddress(myThread.stack).add(STACK_SIZE_GUARD);
      VM_Processor.getCurrentProcessor().activeThreadStackLimit = myThread.stackLimit;
    }

    VM_Magic.restoreHardwareExceptionState(registers);
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
  }
   

  /**
   * Unwind a stackframe.
   */
  public void unwindStackFrame(VM_CompiledMethod compiledMethod, VM_Registers registers) {
    VM_NormalMethod method = (VM_NormalMethod)compiledMethod.getMethod();
    Address fp     = registers.getInnermostFramePointer();
    if (method.isSynchronized()) { // release the lock, if it is being held
      Address ip = registers.getInnermostInstructionAddress();
      Offset instr = compiledMethod.getInstructionOffset(ip);
      Offset lockOffset = ((VM_BaselineCompiledMethod)compiledMethod).getLockAcquisitionOffset();
      if (instr.sGT(lockOffset)) { // we actually have the lock, so must unlock it.
        Object lock;
        if (method.isStatic()) {
          lock = method.getDeclaringClass().getClassForType();
        } else {
          lock = VM_Magic.addressAsObject(fp.add(VM_Compiler.getFirstLocalOffset(method)).loadAddress());
        }
        VM_ObjectModel.genericUnlock(lock);
      }
    }
    // Restore nonvolatile registers used by the baseline compiler.
    if (VM.VerifyAssertions) VM._assert(VM_Compiler.SAVED_GPRS == 2);
    registers.gprs.set(JTOC, fp.add(VM_Compiler.JTOC_SAVE_OFFSET).loadWord());
    registers.gprs.set(EBX, fp.add(VM_Compiler.EBX_SAVE_OFFSET).loadWord());
    
    registers.unwindStackFrame();
  }
}


