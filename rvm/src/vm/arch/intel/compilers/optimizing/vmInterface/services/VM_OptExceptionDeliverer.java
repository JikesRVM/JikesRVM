/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Handle exception delivery and stack unwinding for methods 
 *  compiled by optimizing Compiler 
 *
 * @author Dave Grove
 */
final class VM_OptExceptionDeliverer extends VM_ExceptionDeliverer 
  implements VM_Constants {

  private static final boolean TRACE = false;
  
  /** 
   * Pass control to a catch block.
   */
  void deliverException(VM_CompiledMethod compiledMethod,
			int catchBlockInstructionAddress,
			Throwable exceptionObject,
			VM_Registers registers)  {
    VM_OptCompilerInfo info = (VM_OptCompilerInfo)compiledMethod.getCompilerInfo();
    int fp = registers.getInnermostFramePointer();
    VM_Thread myThread = VM_Thread.getCurrentThread();
    
    if (TRACE) {
      VM.sysWrite("Frame size of ");
      VM.sysWrite(compiledMethod.getMethod());
      VM.sysWrite(" is ");
      VM.sysWrite(info.getFrameFixedSize());
      VM.sysWrite("\n");
    }

    // reset sp to "empty params" state (ie same as it was after prologue)
    int sp = fp - info.getFrameFixedSize();
    registers.gprs[STACK_POINTER] = sp;

    // store exception object for later retrieval by catch block
    int offset = info.getUnsignedExceptionOffset();
    if (offset != 0) {
      // only put the exception object in the stackframe if the catch block is expecting it.
      // (if the method hasn't allocated a stack slot for caught exceptions, then we can safely
      //  drop the exceptionObject on the floor).
      VM_Magic.setObjectAtOffset(VM_Magic.addressAsObject(fp), -offset, exceptionObject);
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
      VM.sysWrite(compiledMethod.getMethod());
      VM.sysWrite("\n");
      for (int i=0; i<NUM_GPRS; i++) {
	VM.sysWrite(GPR_NAMES[i]);
	VM.sysWrite(" = ");
	VM.sysWrite(registers.gprs[i]);
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

    if (VM.VerifyAssertions) VM.assert(registers.inuse == true);
    registers.inuse = false;

    // 'give back' the portion of the stack we borrowed to run 
    // exception delivery code when invoked for a hardware trap.
    // If this was a straight software trap (athrow) then setting 
    // the stacklimit should be harmless, since the stacklimit should already have exactly
    // the value we are setting it too. 
    if (!myThread.hardwareExceptionRegisters.inuse) {
      myThread.stackLimit = VM_Magic.objectAsAddress(myThread.stack) + STACK_SIZE_GUARD;
      VM_Processor.getCurrentProcessor().activeThreadStackLimit = myThread.stackLimit;
    }

    // "branches" to catchBlockInstructionAddress
    VM_Magic.restoreHardwareExceptionState(registers);
    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
  }
  

  /**
   * Unwind a stackframe.
   */
  void unwindStackFrame(VM_CompiledMethod compiledMethod, 
			VM_Registers registers) {
    int fp = registers.getInnermostFramePointer();
    VM_OptCompilerInfo info = (VM_OptCompilerInfo)compiledMethod.getCompilerInfo();
    
    if (TRACE) {
      VM.sysWrite("Registers before unwinding frame for ");
      VM.sysWrite(compiledMethod.getMethod());
      VM.sysWrite("\n");
      for (int i=0; i<NUM_GPRS; i++) {
	VM.sysWrite(GPR_NAMES[i]);
	VM.sysWrite(" = ");
	VM.sysWrite(registers.gprs[i]);
	VM.sysWrite("\n");
      }
    }

    // restore non-volatile registers
    int frameOffset = info.getUnsignedNonVolatileOffset();
    for (int i = info.getFirstNonVolatileGPR(); 
	 i<NUM_NONVOLATILE_GPRS; 
	 i++, frameOffset += 4) {
      registers.gprs[NONVOLATILE_GPRS[i]] = VM_Magic.getMemoryWord(fp - frameOffset);
    }
    if (VM.VerifyAssertions) VM.assert(NUM_NONVOLATILE_FPRS == 0);
    
    registers.unwindStackFrame();

    if (TRACE) {
      VM.sysWrite("Registers after unwinding frame for ");
      VM.sysWrite(compiledMethod.getMethod());
      VM.sysWrite("\n");
      for (int i=0; i<NUM_GPRS; i++) {
	VM.sysWrite(GPR_NAMES[i]);
	VM.sysWrite(" = ");
	VM.sysWrite(registers.gprs[i]);
	VM.sysWrite("\n");
      }
    }
  }
}

