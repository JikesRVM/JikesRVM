/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * A place to put hand written machine code typically invoked by VM_Magic 
 * methods.
 *
 * Hand coding of small inline instruction sequences is typically handled by 
 * each compiler's implementation of VM_Magic methods.  A few VM_Magic methods
 * are so complex that their implementations require many instructions.  
 * But our compilers do not inline arbitrary amounts of machine code. 
 * We therefore write such code blocks here, out of line.
 *
 * These code blocks can be shared by all compilers. They can be branched to
 * via a jtoc offset (obtained from VM_Entrypoints.XXXInstructionsMethod).
 *
 * 17 Mar 1999 Derek Lieber
 *
 * 15 Jun 2001 Dave Grove and Bowen Alpern (Derek believed that compilers 
 * could inline these methods if they wanted.  We do not believe this would 
 * be very easy since they return thru the LR.)
 *
 * @author Derek Lieber
 */
class VM_OutOfLineMachineCode implements VM_BaselineConstants, VM_AssemblerConstants {
  //-----------//
  // interface //
  //-----------//
   
  static void
    init() {
    reflectiveMethodInvokerInstructions       = generateReflectiveMethodInvokerInstructions();
    saveThreadStateInstructions               = generateSaveThreadStateInstructions();
    threadSwitchInstructions                  = generateThreadSwitchInstructions();
    restoreHardwareExceptionStateInstructions = generateRestoreHardwareExceptionStateInstructions();
    getTimeInstructions                       = generateGetTimeInstructions();
    invokeNativeFunctionInstructions          = generateInvokeNativeFunctionInstructions();
  }

  //----------------//
  // implementation //
  //----------------//

  private static INSTRUCTION[] reflectiveMethodInvokerInstructions;
  private static INSTRUCTION[] saveThreadStateInstructions;
  private static INSTRUCTION[] threadSwitchInstructions;
  private static INSTRUCTION[] restoreHardwareExceptionStateInstructions;
  private static INSTRUCTION[] getTimeInstructions;
  private static INSTRUCTION[] invokeNativeFunctionInstructions;
   
  // Machine code for reflective method invocation.
  // See also: "VM_MagicCompiler.generateMethodInvocation".
  //
  // Registers taken at runtime:
  //   T0 == address of method entrypoint to be called
  //   T1 == address of gpr registers to be loaded
  //   T2 == address of fpr registers to be loaded
  //   T3 == address of spill area in calling frame
  //
  // Registers returned at runtime:
  //   standard return value conventions used
  //
  // Side effects at runtime:
  //   artificial stackframe created and destroyed
  //   R0, volatile, and scratch registers destroyed
  //
  private static INSTRUCTION[]
    generateReflectiveMethodInvokerInstructions() {
    VM_Assembler asm = new VM_Assembler(0);
      
    //
    // free registers: 0, S0
    //
    asm.emitMFLR(0);                                         // save...
    asm.emitSTW (0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); // ...return address
      
    asm.emitMTCTR(T0);                          // CTR := start of method code
      
    //
    // free registers: 0, S0, T0
    //
      
    // create new frame
    //
    asm.emitADDI  (S0,  0, FP);                  // S0 := old frame pointer
    asm.emitLWZ   (T0, VM_ObjectModel.getArrayLengthOffset(), T3); // T0 := number of spill words
    asm.emitADDI  (T3, -4, T3);                  // T3 -= 4 (predecrement, ie. T3 + 4 is &spill[0] )
    int spillLoopLabel = asm.getMachineCodeIndex();
    asm.emitADDICr  (T0, T0, -1);                  // T0 -= 1 (and set CR)
    VM_ForwardReference fr1 = asm.emitForwardBC(LT); // if T0 < 0 then break
    asm.emitLWZU   (0,   4, T3);                  // R0 := *(T3 += 4)
    asm.emitSTWU  (0,  -4, FP);                  // put one word of spill area
    asm.emitB    (spillLoopLabel); // goto spillLoop:
    fr1.resolve(asm);
      
    asm.emitSTWU  (S0, -STACKFRAME_HEADER_SIZE, FP);     // allocate frame header and save old fp
    asm.emitLVAL (T0, INVISIBLE_METHOD_ID);
    asm.emitSTW  (T0, STACKFRAME_METHOD_ID_OFFSET, FP); // set method id

    //
    // free registers: 0, S0, T0, T3
    //
      
    // load up fprs
    //
    VM_ForwardReference setupFPRLoader = asm.emitForwardBL();

    FPRLoader:
    for (int i = LAST_VOLATILE_FPR; i >= FIRST_VOLATILE_FPR; --i)
      asm.emitLFDU(i, +8, T2);                 // FPRi := fprs[i]
         
    //
    // free registers: 0, S0, T0, T2, T3
    //
      
    // load up gprs
    //
    VM_ForwardReference setupGPRLoader = asm.emitForwardBL();

    GPRLoader:
    for (int i = LAST_VOLATILE_GPR; i >= FIRST_VOLATILE_GPR; --i)
      asm.emitLWZU  (i, +4, S0);                 // GPRi := gprs[i]
      
    //
    // free registers: 0, S0
    //
      
    // invoke method
    //
    asm.emitBCCTRL();                            // branch and link to method code

    // emit method epilog
    //
    asm.emitLWZ   (FP,  0, FP);                                    // restore caller's frame
    asm.emitLWZ   (S0,  STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);   // pick up return address
    asm.emitMTLR (S0);                                            //
    asm.emitBCLR();                                                // return to caller

    setupFPRLoader.resolve(asm);
    asm.emitMFLR (T3);                          // T3 := address of first fpr load instruction
    asm.emitLWZ   (T0, VM_ObjectModel.getArrayLengthOffset(), T2); // T0 := number of fprs to be loaded
    asm.emitADDI  (T3, VOLATILE_FPRS<<2,    T3); // T3 := address of first instruction following fpr loads
    asm.emitSLWI  (T0, T0,                   2); // T0 := number of bytes of fpr load instructions
    asm.emitSUBFC   (T3, T0,                  T3); // T3 := address of instruction for highest numbered fpr to be loaded
    asm.emitMTLR (T3);                          // LR := """
    asm.emitADDI  (T2, -8, T2);                  // predecrement fpr index (to prepare for update instruction)
    asm.emitBCLR  ();                            // branch to fpr loading instructions

    setupGPRLoader.resolve(asm);
    asm.emitMFLR (T3);                          // T3 := address of first gpr load instruction
    asm.emitLWZ   (T0, VM_ObjectModel.getArrayLengthOffset(), T1); // T0 := number of gprs to be loaded
    asm.emitADDI  (T3, VOLATILE_GPRS<<2,    T3); // T3 := address of first instruction following gpr loads
    asm.emitSLWI  (T0, T0,                   2); // T0 := number of bytes of gpr load instructions
    asm.emitSUBFC   (T3, T0,                  T3); // T3 := address of instruction for highest numbered gpr to be loaded
    asm.emitMTLR (T3);                          // LR := """
    asm.emitADDI  (S0, -4, T1);                  // predecrement gpr index (to prepare for update instruction)
    asm.emitBCLR  ();                            // branch to gpr loading instructions
     
    return asm.makeMachineCode().getInstructions();
  }

  // Machine code to implement "VM_Magic.saveThreadState()".
  //
  // Registers taken at runtime:
  //   T0 == address of VM_Registers object
  //
  // Registers returned at runtime:
  //   none
  //
  // Side effects at runtime:
  //   T1 destroyed
  //
  private static INSTRUCTION[] generateSaveThreadStateInstructions() {
    VM_Assembler asm = new VM_Assembler(0);

    int   ipOffset = VM_Entrypoints.registersIPField.getOffset();
    int fprsOffset = VM_Entrypoints.registersFPRsField.getOffset();
    int gprsOffset = VM_Entrypoints.registersGPRsField.getOffset();

    asm.emitLI(T1, -1);           // T1 = -1
    asm.emitSTW(T1, ipOffset, T0); // registers.ip = -1

    // save non-volatile fprs
    //
    asm.emitLWZ(T1, fprsOffset, T0); // T1 := registers.fprs[]
    for (int i = FIRST_NONVOLATILE_FPR; i <= LAST_NONVOLATILE_FPR; ++i)
      asm.emitSTFD(i, i*8, T1);

    // save non-volatile gprs
    //
    asm.emitLWZ(T1, gprsOffset, T0); // T1 := registers.gprs[]
    for (int i = FIRST_NONVOLATILE_GPR; i <= LAST_NONVOLATILE_GPR; ++i)
      asm.emitSTW(i, i*4, T1);

    // save fp, sp, and ti
    //
    asm.emitSTW(FP, FP*4, T1);
    asm.emitSTW(TI, TI*4, T1);
    asm.emitSTW(SP, SP*4, T1);
      
    // return to caller
    //
    asm.emitBCLR();
     
    return asm.makeMachineCode().getInstructions();
  }
      
  /**
   * Machine code to implement "VM_Magic.threadSwitch()".
   * 
   *  Parameters taken at runtime:
   *    T0 == address of VM_Thread object for the current thread
   *    T1 == address of VM_Registers object for the new thread
   * 
   *  Registers returned at runtime:
   *    none
   * 
   *  Side effects at runtime:
   *    sets current Thread's beingDispatched field to false
   *    saves current Thread's nonvolatile hardware state in its VM_Registers object
   *    restores new thread's VM_Registers nonvolatile hardware state.
   *    execution resumes at address specificed by restored thread's VM_Registers ip field
   */
  private static INSTRUCTION[] generateThreadSwitchInstructions() {
    VM_Assembler asm = new VM_Assembler(0);

    int   ipOffset = VM_Entrypoints.registersIPField.getOffset();
    int fprsOffset = VM_Entrypoints.registersFPRsField.getOffset();
    int gprsOffset = VM_Entrypoints.registersGPRsField.getOffset();
    int regsOffset = VM_Entrypoints.threadContextRegistersField.getOffset();

    // (1) Save nonvolatile hardware state of current thread.
    asm.emitMFLR (T3);                         // T3 gets return address
    asm.emitLWZ   (T2, regsOffset, T0);         // T2 = T0.contextRegisters
    asm.emitSTW  (T3, ipOffset, T2);           // T0.contextRegisters.ip = return address

    // save non-volatile fprs
    asm.emitLWZ(T3, fprsOffset, T2); // T3 := T0.contextRegisters.fprs[]
    for (int i = FIRST_NONVOLATILE_FPR; i <= LAST_NONVOLATILE_FPR; ++i)
      asm.emitSTFD(i, i*8, T3);

    // save non-volatile gprs
    asm.emitLWZ(T3, gprsOffset, T2); // T3 := registers.gprs[]
    for (int i = FIRST_NONVOLATILE_GPR; i <= LAST_NONVOLATILE_GPR; ++i)
      asm.emitSTW(i, i*4, T3);

    // save other 'nonvol' gprs: fp and ti
    asm.emitSTW(FP, FP*4, T3);
    asm.emitSTW(TI, TI*4, T3);

    // (2) Set currentThread.beingDispatched to false
    asm.emitLI(0, 0);                                        // R0 := 0
    asm.emitSTW(0, VM_Entrypoints.beingDispatchedField.getOffset(), T0); // T0.beingDispatched := R0

    // (3) Restore nonvolatile hardware state of new thread.

    // restore non-volatile fprs
    asm.emitLWZ(T0, fprsOffset, T1); // T0 := T1.fprs[]
    for (int i = FIRST_NONVOLATILE_FPR; i <= LAST_NONVOLATILE_FPR; ++i)
      asm.emitLFD(i, i*8, T0);

    // restore non-volatile gprs
    asm.emitLWZ(T0, gprsOffset, T1); // T0 := T1.gprs[]
    for (int i = FIRST_NONVOLATILE_GPR; i <= LAST_NONVOLATILE_GPR; ++i)
      asm.emitLWZ(i, i*4, T0);

    // restore fp, and ti
    asm.emitLWZ(FP, FP*4, T0);
    asm.emitLWZ(TI, TI*4, T0);

    // resume execution at saved ip (T1.ipOffset)
    asm.emitLWZ(T0, ipOffset, T1);
    asm.emitMTLR(T0);
    asm.emitBCLR();

    return asm.makeMachineCode().getInstructions();
  }
  
      
  // Machine code to implement "VM_Magic.restoreHardwareExceptionState()".
  //
  // Registers taken at runtime:
  //   T0 == address of VM_Registers object
  //
  // Registers returned at runtime:
  //   none
  //
  // Side effects at runtime:
  //   all registers are restored except condition registers, count register,
  //   JTOC_POINTER, and PROCESSOR_REGISTER with execution resuming at "registers.ip"
  //
  private static INSTRUCTION[] generateRestoreHardwareExceptionStateInstructions() {
    VM_Assembler asm = new VM_Assembler(0);

    int   ipOffset = VM_Entrypoints.registersIPField.getOffset();
    int fprsOffset = VM_Entrypoints.registersFPRsField.getOffset();
    int gprsOffset = VM_Entrypoints.registersGPRsField.getOffset();
    int lrOffset   = VM_Entrypoints.registersLRField.getOffset();

    // restore LR
    //
    asm.emitLWZ(REGISTER_ZERO, lrOffset, T0);
    asm.emitMTLR(REGISTER_ZERO);

    // restore IP (hold it in CT register for a moment)
    //
    asm.emitLWZ(REGISTER_ZERO, ipOffset, T0);
    asm.emitMTCTR(REGISTER_ZERO);
      
    // restore fprs
    //
    asm.emitLWZ(T1, fprsOffset, T0); // T1 := registers.fprs[]
    for (int i = 0; i < NUM_FPRS; ++i)
      asm.emitLFD(i, i*8, T1);

    // restore gprs
    //
    asm.emitLWZ(T1, gprsOffset, T0); // T1 := registers.gprs[]

    for (int i = FIRST_NONVOLATILE_GPR; i <= LAST_NONVOLATILE_GPR; ++i)
      asm.emitLWZ(i, i*4, T1);

    for (int i = FIRST_SCRATCH_GPR; i <= LAST_SCRATCH_GPR; ++i)
      asm.emitLWZ(i, i*4, T1);

    for (int i = FIRST_VOLATILE_GPR; i <= LAST_VOLATILE_GPR; ++i)
      if (i != T1)
	asm.emitLWZ(i, i*4, T1);

    // restore specials
    //
    asm.emitLWZ(REGISTER_ZERO, REGISTER_ZERO*4, T1);
    asm.emitLWZ(FP, FP*4, T1);
    asm.emitLWZ(TI, TI*4, T1);
      
    // restore last gpr
    //
    asm.emitLWZ(T1, T1*4, T1);

    // resume execution at IP
    //
    asm.emitBCCTR();

    return asm.makeMachineCode().getInstructions();
  }

  // Machine code to implement "VM_Magic.getTime()".
  //
  // Registers taken at runtime:
  //   T0 == address of VM_Processor object
  //
  // Registers returned at runtime:
  //   F0 == return value
  //
  // Side effects at runtime:
  //   T0..T3 and F0..F3 are destroyed
  //   scratch fields used in VM_Processor object
  //
  private static INSTRUCTION[] generateGetTimeInstructions() {
    VM_Assembler asm = new VM_Assembler(0);

    int scratchSecondsOffset     = VM_Entrypoints.scratchSecondsField.getOffset();
    int scratchNanosecondsOffset = VM_Entrypoints.scratchNanosecondsField.getOffset();

    asm.emitOR    (T3, T0, T0);                             // t3 := address of VM_Processor object
    asm.emitLFDtoc(F2, VM_Entrypoints.billionthField.getOffset(), T1); // f2 := 1e-9
    asm.emitLFDtoc(F3, VM_Entrypoints.IEEEmagicField.getOffset(), T1); // f3 := IEEEmagic

    asm.emitSTFD  (F3, scratchNanosecondsOffset, T3);       // scratch_nanos   := IEEEmagic
    asm.emitSTFD  (F3, scratchSecondsOffset,     T3);       // scratch_seconds := IEEEmagic

    int loopLabel = asm.getMachineCodeIndex();
    if (VM.BuildForLinux) {
      asm.emitMFTBU (T0);                                     // t0 := real time clock, upper
      asm.emitMFTB  (T1);                                     // t1 := real time clock, lower
      asm.emitMFTBU (T2);                                     // t2 := real time clock, upper
    } else {
      asm.emitMFSPR (T0, 4 );                                 // t0 := real time clock, upper
      asm.emitMFSPR (T1, 5 );                                 // t1 := real time clock, lower
      asm.emitMFSPR (T2, 4 );                                 // t2 := real time clock, upper
    }
    asm.emitCMP   (T0, T2);                                 // t0 == t2?
    asm.emitSTW   (T1, scratchNanosecondsOffset + 4, T3);   // scratch_nanos_lo   := nanos
    asm.emitSTW   (T0, scratchSecondsOffset     + 4, T3);   // scratch_seconds_lo := seconds
    asm.emitBC    (NE, loopLabel);                          // seconds have rolled over, try again

    asm.emitLFD   (F0, scratchNanosecondsOffset, T3);       // f0 := IEEEmagic + nanos
    asm.emitLFD   (F1, scratchSecondsOffset,     T3);       // f1 := IEEEmagic + seconds

    asm.emitFSUB    (F0, F0, F3);                             // f0 := f0 - IEEEmagic == (double)nanos
    asm.emitFSUB    (F1, F1, F3);                             // f1 := f1 - IEEEmagic == (double)seconds

    asm.emitFMADD   (F0, F2, F0, F1);                         // f0 := f2 * f0 + f1

    // return to caller
    //
    asm.emitBCLR();

    return asm.makeMachineCode().getInstructions();
  }

  // on entry:
  //   JTOC = TOC for native call
  //   TI - IP address of native function to branch to
  //   S0 -> threads JNIEnvironment, which contains saved PR & TI regs
  //   Parameter regs R4-R10, FP1-FP6 loaded for call to native
  //   (R3 will be set here before branching to the native function)
  // 
  //   GPR3 (T0), SP, PR regs are available for scratch regs on entry
  //
  private static INSTRUCTION[] generateInvokeNativeFunctionInstructions() {

    VM_Assembler asm = new VM_Assembler(0);
    int lockoutLockOffset = VM_Entrypoints.lockoutProcessorField.getOffset();
    //
    // store the return address to the Java to C glue prolog, which is now in LR
    // into transition frame. If GC occurs, the JNIGCMapIterator will cause
    // this ip address to be relocated if the generated glue code is moved.
    //
    asm.emitLWZ    (SP, 0, FP);
    asm.emitMFLR  (T0);
	//-#if RVM_FOR_LINUX
	// save return address of JNI method in mini frame (2)
	asm.emitSTW   (T0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, SP);
	//-#endif
	//-#if RVM_FOR_AIX
    asm.emitSTW   (T0, -JNI_PROLOG_RETURN_ADDRESS_OFFSET, SP);  // save return address in stack frame
    //-#endif
	//
    // Load required JNI function ptr into first parameter reg (GPR3/T0)
    // This pointer is in the JNIEnvAddress field of JNIEnvironment
    //
    asm.emitLWZ(T0, VM_Entrypoints.JNIEnvAddressField.getOffset(), S0);
    //
    // change the vpstatus of the VP to "in Native"
    //
    asm.emitLWZ    (PROCESSOR_REGISTER, VM_Entrypoints.JNIEnvSavedPRField.getOffset(), S0); 
    asm.emitLWZ    (SP, VM_Entrypoints.vpStatusAddressField.getOffset(), PROCESSOR_REGISTER); // SP gets addr vpStatus word
    asm.emitADDI   (S0,  VM_Processor.IN_NATIVE, 0 );              // S0  <- new status value
    asm.emitSTW   (S0,  0, SP);                                   // change state to native

    // set word following JNI function ptr to addr of current processors vpStatus word
    asm.emitSTW   (SP,  4, T0);

    //
    asm.emitMTLR  (TI);                                // move native code address to link reg
    //
    // goto the native code
    //
    asm.emitBCLRL  ();                                       // call native method
    //
    // save the return value in R3-R4 in the glue frame spill area since they may be overwritten
    // in the call to becomeRVMThreadOffset
    asm.emitSTW   (T0, NATIVE_FRAME_HEADER_SIZE, FP);
    asm.emitSTW   (T1, NATIVE_FRAME_HEADER_SIZE+4, FP);
    //
    // try to return to Java state, by testing state word of process
    //
    int label1    = asm.getMachineCodeIndex();                            // inst index of the following load
    asm.emitLWZ    (PROCESSOR_REGISTER, 0, FP);                            // get previous frame
	//-#if RVM_FOR_LINUX
	// mimi (1) FP -> mimi(2) FP -> java caller
	asm.emitLWZ    (PROCESSOR_REGISTER, 0, PROCESSOR_REGISTER);
	//-#endif
    asm.emitLWZ    (JTOC, -JNI_JTOC_OFFSET, PROCESSOR_REGISTER);                         // load JTOC reg
    asm.emitLWZ    (PROCESSOR_REGISTER, - JNI_PR_OFFSET, PROCESSOR_REGISTER); //load processor register  
    asm.emitLWZ    (T3, VM_Entrypoints.vpStatusAddressField.getOffset(), PROCESSOR_REGISTER); // T3 gets addr of vpStatus word
    asm.emitLWARX (S0, 0, T3);                                            // get status for processor
    asm.emitCMPI  (S0, VM_Processor.BLOCKED_IN_NATIVE);                   // are we blocked in native code?
    VM_ForwardReference fr = asm.emitForwardBC(NE);
    //
    // if blocked in native, call C routine to do pthread_yield
    //
    asm.emitLWZ    (T2, VM_Entrypoints.the_boot_recordField.getOffset(), JTOC);       // T2 gets boot record address
    asm.emitLWZ    (JTOC, VM_Entrypoints.sysTOCField.getOffset(), T2);                // load TOC for syscalls from bootrecord
    asm.emitLWZ    (T1,   VM_Entrypoints.sysVirtualProcessorYieldIPField.getOffset(), T2);  // load addr of function
    asm.emitMTLR  (T1);
    asm.emitBCLRL  ();                                          // call sysVirtualProcessorYield in sys.C
    asm.emitB     (label1); // retest the blocked in native
    //
    //  br to here -not blocked in native
    //
    fr.resolve(asm);
    asm.emitADDI   (S0,  VM_Processor.IN_JAVA, 0 );           // S0  <- new state value
    asm.emitSTWCXr(S0,  0, T3);                              // attempt to change state to java
    asm.emitBC    (NE, label1);                              // br if failure -retry lwarx
    //
    // return to caller
    //
    asm.emitLWZ    (T3, 0 , FP);                                // get previous frame
	//-#if RVM_FOR_LINUX
	asm.emitLWZ    (S0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, T3);
	//-#endif
	//-#if RVM_FOR_AIX
	asm.emitLWZ    (S0, -JNI_PROLOG_RETURN_ADDRESS_OFFSET, T3); // get return address from stack frame
    //-#endif
	asm.emitMTLR  (S0);
    asm.emitBCLR   ();
    //
    return asm.makeMachineCode().getInstructions();

  }  // generateInvokeNativeFunctionInstructions
}
