/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

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
  void deliverException(VM_CompiledMethod compiledMethod,
			int               catchBlockInstructionAddress,
			Throwable         exceptionObject,
			VM_Registers      registers) {
    int       fp     = registers.getInnermostFramePointer();
    VM_Method method = compiledMethod.getMethod();

    // reset sp to "empty expression stack" state
    //
    int sp = fp + VM_Compiler.getEmptyStackOffset(method);

    // push exception object as argument to catch block
    //
    VM_Magic.setMemoryWord(sp -= 4, VM_Magic.objectAsAddress(exceptionObject));
    registers.gprs[SP] = sp;

    // set address at which to resume executing frame
    //
    registers.ip = catchBlockInstructionAddress;

    // branch to catch block
    //
    VM.enableGC(); // disabled right before VM_Runtime.deliverException was called
    if (VM.VerifyAssertions) VM.assert(registers.inuse == true); 

    registers.inuse = false;
    VM_Magic.restoreHardwareExceptionState(registers);
    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
  }
   
  /**
   * Unwind a stackframe.
   */
  void unwindStackFrame(VM_CompiledMethod compiledMethod, VM_Registers registers) {
    VM_Method method = compiledMethod.getMethod();
    if (method.isSynchronized()) { 
      int ip = registers.getInnermostInstructionAddress();
      int instr = ip - VM_Magic.objectAsAddress(compiledMethod.getInstructions());
      VM_BaselineCompilerInfo info = (VM_BaselineCompilerInfo) compiledMethod.getCompilerInfo();
      if (instr < info.lockAcquisitionOffset) {       // in prologue, lock not owned
      } else if (method.isStatic()) {
	Object lock = method.getDeclaringClass().getClassForType();
	VM_Lock.unlock(lock);
      } else {
	int fp = registers.getInnermostFramePointer();
	Object lock = VM_Magic.addressAsObject(VM_Magic.getMemoryWord(fp + VM_Compiler.getFirstLocalOffset(method)));
	VM_Lock.unlock(lock);
      }
    }
    registers.unwindStackFrame();
  }
}
