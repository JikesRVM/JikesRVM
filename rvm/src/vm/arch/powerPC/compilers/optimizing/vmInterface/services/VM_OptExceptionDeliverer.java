/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/** 
 * Handle exception delivery and stack unwinding for 
 * opt compiled methods.
 * 
 * @author Dave Grove
 * @author Mauricio J. Serrano 
 */
final class VM_OptExceptionDeliverer extends VM_ExceptionDeliverer
  implements VM_Constants, VM_BytecodeConstants {

  /** 
   * Pass control to a catch block.
   */
  public void deliverException(VM_CompiledMethod cm, 
                        Address catchBlockInstructionAddress, 
                        Throwable exceptionObject, 
                        VM_Registers registers) {

    // store exception object for later retrieval by catch block
    VM_OptCompiledMethod compiledMethod = (VM_OptCompiledMethod)cm;
    int offset = compiledMethod.getUnsignedExceptionOffset();
    if (offset != 0) {
      // only put the exception object in the stackframe if the catch block is expecting it.
      // (if the method hasn't allocated a stack slot for caught exceptions, then we can safely
      //  drop the exceptionObject on the floor).
      Address fp = registers.getInnermostFramePointer();
      VM_Magic.setObjectAtOffset(VM_Magic.addressAsObject(fp), offset, exceptionObject);
    }

    // set address at which to resume executing frame
    registers.ip = catchBlockInstructionAddress;
    VM.enableGC(); // disabled right before VM_Runtime.deliverException was called

    if (VM.VerifyAssertions) VM._assert(registers.inuse == true);
    registers.inuse = false;

    // "branches" to catchBlockInstructionAddress
    VM_Magic.restoreHardwareExceptionState(registers);
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
  }

  /**
   * Unwind a stackframe.
   */ 
  public void unwindStackFrame(VM_CompiledMethod cm, VM_Registers registers) {
    Address fp = registers.getInnermostFramePointer();
    VM_OptCompiledMethod compiledMethod = (VM_OptCompiledMethod)cm;

    // restore non-volatile registers
    int frameOffset = compiledMethod.getUnsignedNonVolatileOffset();
    int firstInteger = compiledMethod.getFirstNonVolatileGPR();
    if (firstInteger >= 0) {
      //-#if RVM_FOR_64_ADDR
      frameOffset = (frameOffset + 7) & ~7;
      //-#endif
      for (int i = firstInteger; i < 32; i++) {
        registers.gprs.set(i, fp.add(frameOffset).loadWord());
        frameOffset += BYTES_IN_ADDRESS;
      }
    }
    int firstFloat = compiledMethod.getFirstNonVolatileFPR();
    if (firstFloat >= 0) {
      frameOffset = (frameOffset + 7) & ~7;  // align pointer for doubles
      for (int i = firstFloat; i < 32; i++) {
        long temp = VM_Magic.getLongAtOffset(VM_Magic.addressAsObject(fp), frameOffset);
        registers.fprs[i] = VM_Magic.longBitsAsDouble(temp);
        frameOffset += BYTES_IN_DOUBLE;
      }
    }

    registers.unwindStackFrame();
  }
}



