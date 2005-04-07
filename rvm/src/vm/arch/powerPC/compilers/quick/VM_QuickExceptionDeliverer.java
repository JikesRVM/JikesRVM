/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.quick;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import org.vmmagic.unboxed.*;

/**
 *  Handle exception delivery and stack unwinding for methods compiled 
 * by quick compiler.
 *
 * Based on VM_ExceptionDeliverer
 *
 * @author Chris Hoffmann
 * @date Oct 28, 2004
 */
class VM_QuickExceptionDeliverer extends VM_ExceptionDeliverer 
  implements VM_QuickConstants {

  /**
   * Pass control to a catch block.
   */
  public void deliverException(VM_CompiledMethod compiledMethod,
                               Address        catchBlockInstructionAddress,
                               Throwable         exceptionObject,
                               VM_Registers      registers) {
    Address fp    = registers.getInnermostFramePointer();
    VM_NormalMethod method = (VM_NormalMethod)compiledMethod.getMethod();

    // Find offset of store exception object in frame
    //
    Address exceptionSlot =
      fp.add(VM_QuickCompiler.getExceptionObjectOffset(method));

    // Put exception object in expected place in the frame
    //
    exceptionSlot.store(VM_Magic.objectAsAddress(exceptionObject));

    // set address at which to resume executing frame
    //
    registers.ip = catchBlockInstructionAddress;

    // branch to catch block
    //
    VM.enableGC(); // disabled right before VM_Runtime.deliverException was called
    if (VM.VerifyAssertions) VM._assert(registers.inuse == true); 

    registers.inuse = false;
    VM_Magic.restoreHardwareExceptionState(registers);
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
  }
   
  /**
   * Unwind a stackframe.
   */
  public void unwindStackFrame(VM_CompiledMethod compiledMethod, VM_Registers registers) {
    VM_NormalMethod method = (VM_NormalMethod)compiledMethod.getMethod();
    VM_QuickCompiledMethod qcm = (VM_QuickCompiledMethod)compiledMethod;
    if (method.isSynchronized()) { 
      Address ip = registers.getInnermostInstructionAddress();
      Address base = VM_Magic.objectAsAddress(compiledMethod.getInstructions());
      Offset instr = ip.diff(base);
      Offset lockOffset = ((VM_QuickCompiledMethod)compiledMethod).getLockAcquisitionOffset();
      if (instr.sGT(lockOffset)) { // we actually have the lock, so must unlock it.
        Object lock;
        if (method.isStatic()) {
          lock = method.getDeclaringClass().getClassForType();
        } else {
          Address fp = registers.getInnermostFramePointer();
          int offset = VM_QuickCompiler.getThisPtrSaveAreaOffset(method);
          lock = VM_Magic.addressAsObject(fp.add(offset).loadAddress());
        }
        VM_ObjectModel.genericUnlock(lock);
      }
    }
    // restore non-volatile registers
    Address fp = registers.getInnermostFramePointer();
    Offset frameOffset = Offset.fromIntSignExtend(VM_QuickCompiler.getCallerSaveOffset(method));
    int limit;

    for (int i = qcm.firstGPR; i <= qcm.lastGPR; i++, frameOffset = frameOffset.sub(BYTES_IN_ADDRESS)) {
      registers.gprs.set(i, fp.loadWord(frameOffset));
    }
  
    registers.gprs.set(VM_QuickCompiler.S1,
                       fp.loadWord(frameOffset));
    frameOffset = frameOffset.sub(BYTES_IN_ADDRESS);
    registers.gprs.set(VM_QuickCompiler.S0,
                       fp.loadWord(frameOffset));
    frameOffset = frameOffset.sub(BYTES_IN_ADDRESS);

    for (int i = qcm.firstFPR; i <= qcm.lastFPR ; i++,frameOffset = frameOffset.sub(BYTES_IN_DOUBLE)) {
      long temp = VM_Magic.getLongAtOffset(VM_Magic.addressAsObject(fp), frameOffset);
        registers.fprs[i] = VM_Magic.longBitsAsDouble(temp);
    }

    long temp = VM_Magic.getLongAtOffset(VM_Magic.addressAsObject(fp), frameOffset);
    frameOffset = frameOffset.sub(BYTES_IN_DOUBLE);
    registers.fprs[VM_QuickCompiler.SF0] = VM_Magic.longBitsAsDouble(temp);

    registers.unwindStackFrame();
  }
}
