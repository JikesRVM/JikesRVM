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
			VM_Address        catchBlockInstructionAddress,
			Throwable         exceptionObject,
			VM_Registers      registers) {
    VM_Address fp    = registers.getInnermostFramePointer();
    VM_Method method = compiledMethod.getMethod();

    // reset sp to "empty expression stack" state
    //
    VM_Address sp = fp.add(VM_Compiler.getEmptyStackOffset(method));

    // push exception object as argument to catch block
    //
    sp = sp.sub(4);
    VM_Magic.setMemoryWord(sp, VM_Magic.objectAsAddress(exceptionObject).toInt());
    registers.gprs[SP] = sp.toInt();

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
      VM_Address ip = registers.getInnermostInstructionAddress();
      VM_Address base = VM_Magic.objectAsAddress(compiledMethod.getInstructions());
      int instr = ip.diff(base);
      VM_BaselineCompilerInfo info = (VM_BaselineCompilerInfo) compiledMethod.getCompilerInfo();
      if (instr < info.lockAcquisitionOffset) {
	// in prologue, lock not owned; nothing to do
      } else if (method.isStatic()) {
	Object lock = method.getDeclaringClass().getClassForType();
	VM_ObjectModel.genericUnlock(lock);
      } else {
	VM_Address fp = registers.getInnermostFramePointer();
	int offset = VM_Compiler.getFirstLocalOffset(method);
	Object lock = VM_Magic.addressAsObject(VM_Magic.getMemoryAddress(fp.add(offset)));
	VM_ObjectModel.genericUnlock(lock);
      }
    }
    registers.unwindStackFrame();
  }
}
