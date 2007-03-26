/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.opt.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.VM_CompiledMethod;
import org.jikesrvm.VM_Constants;
import org.jikesrvm.runtime.VM_ExceptionDeliverer;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Processor;
import org.jikesrvm.scheduler.VM_Thread;
import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.ArchitectureSpecific.VM_Registers;
import org.jikesrvm.opt.VM_OptCompiledMethod;

import org.vmmagic.unboxed.*;

/**
 * Handle exception delivery and stack unwinding for methods 
 *  compiled by optimizing Compiler 
 *
 * @author Dave Grove
 */
public abstract class VM_OptExceptionDeliverer extends VM_ExceptionDeliverer
    implements ArchitectureSpecific.VM_ArchConstants {

  private static final boolean TRACE = false;
  
  /** 
   * Pass control to a catch block.
   */
  public void deliverException(VM_CompiledMethod compiledMethod,
                        Address catchBlockInstructionAddress,
                        Throwable exceptionObject,
                        VM_Registers registers)  {
    VM_OptCompiledMethod optMethod = (VM_OptCompiledMethod)compiledMethod;
    Address fp = registers.getInnermostFramePointer();
    VM_Thread myThread = VM_Thread.getCurrentThread();
    
    if (TRACE) {
      VM.sysWrite("Frame size of ");
      VM.sysWrite(optMethod.getMethod());
      VM.sysWrite(" is ");
      VM.sysWrite(optMethod.getFrameFixedSize());
      VM.sysWrite("\n");
    }

    // reset sp to "empty params" state (ie same as it was after prologue)
    Address sp = fp.minus(optMethod.getFrameFixedSize());
    registers.gprs.set(STACK_POINTER, sp.toWord());

    // store exception object for later retrieval by catch block
    int offset = optMethod.getUnsignedExceptionOffset();
    if (offset != 0) {
      // only put the exception object in the stackframe if the catch block is expecting it.
      // (if the method hasn't allocated a stack slot for caught exceptions, then we can safely
      //  drop the exceptionObject on the floor).
      VM_Magic.setObjectAtOffset(VM_Magic.addressAsObject(fp), Offset.fromIntSignExtend(-offset), exceptionObject);
      if (TRACE) {
        VM.sysWrite("Storing exception object ");
        VM.sysWrite(VM_Magic.objectAsAddress(exceptionObject));
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
      for (int i=0; i<NUM_GPRS; i++) {
        VM.sysWrite(GPR_NAMES[i]);
        VM.sysWrite(" = ");
        VM.sysWrite(registers.gprs.get(i));
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

    VM.enableGC(); // disabled right before VM_Runtime.deliverException was called

    if (VM.VerifyAssertions) VM._assert(registers.inuse == true);
    registers.inuse = false;

    // 'give back' the portion of the stack we borrowed to run 
    // exception delivery code when invoked for a hardware trap.
    // If this was a straight software trap (athrow) then setting 
    // the stacklimit should be harmless, since the stacklimit should already have exactly
    // the value we are setting it too. 
    if (!myThread.hardwareExceptionRegisters.inuse) {
      myThread.stackLimit = VM_Magic.objectAsAddress(myThread.stack).plus(STACK_SIZE_GUARD);
      VM_Processor.getCurrentProcessor().activeThreadStackLimit = myThread.stackLimit;
    }

    // "branches" to catchBlockInstructionAddress
    VM_Magic.restoreHardwareExceptionState(registers);
    if (VM.VerifyAssertions) VM._assert(VM_Constants.NOT_REACHED);
  }
  

  /**
   * Unwind a stackframe.
   */
  public void unwindStackFrame(VM_CompiledMethod compiledMethod, 
                        VM_Registers registers) {
    Address fp = registers.getInnermostFramePointer();
    VM_OptCompiledMethod optMethod = (VM_OptCompiledMethod)compiledMethod;
    
    if (TRACE) {
      VM.sysWrite("Registers before unwinding frame for ");
      VM.sysWrite(optMethod.getMethod());
      VM.sysWrite("\n");
      for (int i=0; i<NUM_GPRS; i++) {
        VM.sysWrite(GPR_NAMES[i]);
        VM.sysWrite(" = ");
        VM.sysWrite(registers.gprs.get(i));
        VM.sysWrite("\n");
      }
    }

    // restore non-volatile registers
    int frameOffset = optMethod.getUnsignedNonVolatileOffset();
    for (int i = optMethod.getFirstNonVolatileGPR(); 
         i<NUM_NONVOLATILE_GPRS; 
         i++, frameOffset += 4) {
      registers.gprs.set(NONVOLATILE_GPRS[i],fp.minus(frameOffset).loadWord());
    }
    if (VM.VerifyAssertions) VM._assert(NUM_NONVOLATILE_FPRS == 0);
    
    registers.unwindStackFrame();

    if (TRACE) {
      VM.sysWrite("Registers after unwinding frame for ");
      VM.sysWrite(optMethod.getMethod());
      VM.sysWrite("\n");
      for (int i=0; i<NUM_GPRS; i++) {
        VM.sysWrite(GPR_NAMES[i]);
        VM.sysWrite(" = ");
        VM.sysWrite(registers.gprs.get(i));
        VM.sysWrite("\n");
      }
    }
  }
}

