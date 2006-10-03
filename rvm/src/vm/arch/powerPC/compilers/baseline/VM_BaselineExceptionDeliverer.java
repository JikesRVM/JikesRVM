/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

import org.vmmagic.unboxed.*;

/**
 *  Handle exception delivery and stack unwinding for methods compiled 
 * by baseline compiler.
 *
 * @author Derek Lieber
 * @date 18 Sep 1998 
 */
class VM_BaselineExceptionDeliverer extends VM_ExceptionDeliverer 
  implements VM_BaselineConstants {

  /**
   * Pass control to a catch block.
   */
  public void deliverException(VM_CompiledMethod compiledMethod,
                               Address        catchBlockInstructionAddress,
                               Throwable         exceptionObject,
                               VM_Registers      registers) {
    Address fp    = registers.getInnermostFramePointer();
    VM_NormalMethod method = (VM_NormalMethod)compiledMethod.getMethod();

    // reset sp to "empty expression stack" state
    //
    Address sp = fp.plus(VM_Compiler.getEmptyStackOffset(method));

    // push exception object as argument to catch block
    //
    sp = sp.minus(BYTES_IN_ADDRESS);
    sp.store(VM_Magic.objectAsAddress(exceptionObject));

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
    if (method.isSynchronized()) { 
      Address ip = registers.getInnermostInstructionAddress();
      Offset instr = compiledMethod.getInstructionOffset(ip);
      Offset lockOffset = ((VM_BaselineCompiledMethod)compiledMethod).getLockAcquisitionOffset();
      if (instr.sGT(lockOffset)) { // we actually have the lock, so must unlock it.
        Object lock;
        if (method.isStatic()) {
          lock = method.getDeclaringClass().getClassForType();
        } else {
          Address fp = registers.getInnermostFramePointer();
          int offset = VM_Compiler.getFirstLocalOffset(method);
          lock = VM_Magic.addressAsObject(fp.plus(offset).loadAddress());
        }
        VM_ObjectModel.genericUnlock(lock);
      }
    }
    registers.unwindStackFrame();
  }
}
