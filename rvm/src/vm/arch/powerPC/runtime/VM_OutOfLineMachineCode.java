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
    invokeNativeFunctionInstructions          = generateInvokeNativeFunctionInstructions();
  }

  //----------------//
  // implementation //
  //----------------//

  private static VM_CodeArray reflectiveMethodInvokerInstructions;
  private static VM_CodeArray saveThreadStateInstructions;
  private static VM_CodeArray threadSwitchInstructions;
  private static VM_CodeArray restoreHardwareExceptionStateInstructions;
  private static VM_CodeArray invokeNativeFunctionInstructions;
   
  // Machine code for reflective method invocation.
  // See also: "VM_Compiler.generateMethodInvocation".
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
  private static VM_CodeArray generateReflectiveMethodInvokerInstructions() {
    VM_Assembler asm = new VM_Assembler(0);
      
    //
    // free registers: 0, S0
    //
    asm.emitMFLR(0);                                         // save...
    asm.emitSTAddr (0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); // ...return address
      
    asm.emitMTCTR(T0);                          // CTR := start of method code
      
    //
    // free registers: 0, S0, T0
    //
      
    // create new frame
    //
    asm.emitMR    (S0,  FP);                  // S0 := old frame pointer
    asm.emitLInt  (T0, VM_ObjectModel.getArrayLengthOffset(), T3); // T0 := number of spill words
    asm.emitADDI  (T3, -BYTES_IN_ADDRESS, T3);                  // T3 -= 4 (predecrement, ie. T3 + 4 is &spill[0] )
    int spillLoopLabel = asm.getMachineCodeIndex();
    asm.emitADDICr  (T0, T0, -1);                  // T0 -= 1 (and set CR)
    VM_ForwardReference fr1 = asm.emitForwardBC(LT); // if T0 < 0 then break
    asm.emitLAddrU   (0,   BYTES_IN_ADDRESS, T3);                  // R0 := *(T3 += 4)
    asm.emitSTAddrU  (0,  -BYTES_IN_ADDRESS, FP);                  // put one word of spill area
    asm.emitB    (spillLoopLabel); // goto spillLoop:
    fr1.resolve(asm);
      
    asm.emitSTAddrU  (S0, -STACKFRAME_HEADER_SIZE, FP);     // allocate frame header and save old fp
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
      asm.emitLFDU(i, BYTES_IN_DOUBLE, T2);                 // FPRi := fprs[i]
         
    //
    // free registers: 0, S0, T0, T2, T3
    //
      
    // load up gprs
    //
    VM_ForwardReference setupGPRLoader = asm.emitForwardBL();

    GPRLoader:
    for (int i = LAST_VOLATILE_GPR; i >= FIRST_VOLATILE_GPR; --i)
      asm.emitLAddrU  (i, BYTES_IN_ADDRESS, S0);                 // GPRi := gprs[i]
      
    //
    // free registers: 0, S0
    //
      
    // invoke method
    //
    asm.emitBCCTRL();                            // branch and link to method code

    // emit method epilog
    //
    asm.emitLAddr(FP,  0, FP);                                    // restore caller's frame
    asm.emitLAddr (S0,  STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);   // pick up return address
    asm.emitMTLR (S0);                                            //
    asm.emitBCLR();                                                // return to caller

    setupFPRLoader.resolve(asm);
    asm.emitMFLR (T3);                          // T3 := address of first fpr load instruction
    asm.emitLInt  (T0, VM_ObjectModel.getArrayLengthOffset(), T2); // T0 := number of fprs to be loaded
    asm.emitADDI  (T3, VOLATILE_FPRS<<LG_INSTRUCTION_WIDTH,    T3); // T3 := address of first instruction following fpr loads
    asm.emitSLWI  (T0, T0, LG_INSTRUCTION_WIDTH); // T0 := number of bytes of fpr load instructions
    asm.emitSUBFC   (T3, T0, T3);                // T3 := address of instruction for highest numbered fpr to be loaded
    asm.emitMTLR (T3);                           // LR := """
    asm.emitADDI  (T2, -BYTES_IN_DOUBLE, T2);    // predecrement fpr index (to prepare for update instruction)
    asm.emitBCLR  ();                            // branch to fpr loading instructions

    setupGPRLoader.resolve(asm);
    asm.emitMFLR (T3);                          // T3 := address of first gpr load instruction
    asm.emitLInt   (T0, VM_ObjectModel.getArrayLengthOffset(), T1); // T0 := number of gprs to be loaded
    asm.emitADDI  (T3, VOLATILE_GPRS<<LG_INSTRUCTION_WIDTH,    T3); // T3 := address of first instruction following gpr loads
    asm.emitSLWI  (T0, T0, LG_INSTRUCTION_WIDTH); // T0 := number of bytes of gpr load instructions
    asm.emitSUBFC   (T3, T0,T3);                  // T3 := address of instruction for highest numbered gpr to be loaded
    asm.emitMTLR (T3);                          // LR := """
    asm.emitADDI  (S0, -BYTES_IN_ADDRESS, T1);   // predecrement gpr index (to prepare for update instruction)
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
  private static VM_CodeArray generateSaveThreadStateInstructions() {
    VM_Assembler asm = new VM_Assembler(0);

    int   ipOffset = VM_Entrypoints.registersIPField.getOffset();
    int fprsOffset = VM_Entrypoints.registersFPRsField.getOffset();
    int gprsOffset = VM_Entrypoints.registersGPRsField.getOffset();

    // save return address
    // 
    asm.emitMFLR  (T1);               // T1 = LR (return address)
    asm.emitSTAddr(T1, ipOffset, T0); // registers.ip = return address

    // save non-volatile fprs
    //
    asm.emitLAddr(T1, fprsOffset, T0); // T1 := registers.fprs[]
    for (int i = FIRST_NONVOLATILE_FPR; i <= LAST_NONVOLATILE_FPR; ++i)
      asm.emitSTFD(i, i << LOG_BYTES_IN_DOUBLE, T1);

    // save non-volatile gprs
    //
    asm.emitLAddr(T1, gprsOffset, T0); // T1 := registers.gprs[]
    for (int i = FIRST_NONVOLATILE_GPR; i <= LAST_NONVOLATILE_GPR; ++i)
      asm.emitSTAddr(i, i << LOG_BYTES_IN_ADDRESS, T1);

    // save fp
    //
    asm.emitSTAddr(FP, FP<<LOG_BYTES_IN_ADDRESS, T1);
      
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
  private static VM_CodeArray generateThreadSwitchInstructions() {
    VM_Assembler asm = new VM_Assembler(0);

    int   ipOffset = VM_Entrypoints.registersIPField.getOffset();
    int fprsOffset = VM_Entrypoints.registersFPRsField.getOffset();
    int gprsOffset = VM_Entrypoints.registersGPRsField.getOffset();
    int regsOffset = VM_Entrypoints.threadContextRegistersField.getOffset();

    // (1) Save nonvolatile hardware state of current thread.
    asm.emitMFLR (T3);                         // T3 gets return address
    asm.emitLAddr (T2, regsOffset, T0);         // T2 = T0.contextRegisters
    asm.emitSTAddr (T3, ipOffset, T2);           // T0.contextRegisters.ip = return address

    // save non-volatile fprs
    asm.emitLAddr(T3, fprsOffset, T2); // T3 := T0.contextRegisters.fprs[]
    for (int i = FIRST_NONVOLATILE_FPR; i <= LAST_NONVOLATILE_FPR; ++i)
      asm.emitSTFD(i, i<<LOG_BYTES_IN_DOUBLE, T3);

    // save non-volatile gprs
    asm.emitLAddr(T3, gprsOffset, T2); // T3 := registers.gprs[]
    for (int i = FIRST_NONVOLATILE_GPR; i <= LAST_NONVOLATILE_GPR; ++i)
      asm.emitSTAddr(i, i<<LOG_BYTES_IN_ADDRESS, T3);

    // save fp
    asm.emitSTAddr(FP, FP<<LOG_BYTES_IN_ADDRESS, T3);

    // (2) Set currentThread.beingDispatched to false
    asm.emitLVAL(0, 0);                                       // R0 := 0
    asm.emitSTW(0, VM_Entrypoints.beingDispatchedField.getOffset(), T0); // T0.beingDispatched := R0

    // (3) Restore nonvolatile hardware state of new thread.

    // restore non-volatile fprs
    asm.emitLAddr(T0, fprsOffset, T1); // T0 := T1.fprs[]
    for (int i = FIRST_NONVOLATILE_FPR; i <= LAST_NONVOLATILE_FPR; ++i)
      asm.emitLFD(i, i<<LOG_BYTES_IN_DOUBLE, T0);

    // restore non-volatile gprs
    asm.emitLAddr(T0, gprsOffset, T1); // T0 := T1.gprs[]
    for (int i = FIRST_NONVOLATILE_GPR; i <= LAST_NONVOLATILE_GPR; ++i)
      asm.emitLAddr(i, i<<LOG_BYTES_IN_ADDRESS, T0);

    // restore fp
    asm.emitLAddr(FP, FP<<LOG_BYTES_IN_ADDRESS, T0);

    // resume execution at saved ip (T1.ipOffset)
    asm.emitLAddr(T0, ipOffset, T1);
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
  private static VM_CodeArray generateRestoreHardwareExceptionStateInstructions() {
    VM_Assembler asm = new VM_Assembler(0);

    int   ipOffset = VM_Entrypoints.registersIPField.getOffset();
    int fprsOffset = VM_Entrypoints.registersFPRsField.getOffset();
    int gprsOffset = VM_Entrypoints.registersGPRsField.getOffset();
    int lrOffset   = VM_Entrypoints.registersLRField.getOffset();

    // restore LR
    //
    asm.emitLAddr(REGISTER_ZERO, lrOffset, T0);
    asm.emitMTLR(REGISTER_ZERO);

    // restore IP (hold it in CT register for a moment)
    //
    asm.emitLAddr(REGISTER_ZERO, ipOffset, T0);
    asm.emitMTCTR(REGISTER_ZERO);
      
    // restore fprs
    //
    asm.emitLAddr(T1, fprsOffset, T0); // T1 := registers.fprs[]
    for (int i = 0; i < NUM_FPRS; ++i)
      asm.emitLFD(i, i<<LOG_BYTES_IN_DOUBLE, T1);

    // restore gprs
    //
    asm.emitLAddr(T1, gprsOffset, T0); // T1 := registers.gprs[]

    for (int i = FIRST_NONVOLATILE_GPR; i <= LAST_NONVOLATILE_GPR; ++i)
      asm.emitLAddr(i, i<<LOG_BYTES_IN_ADDRESS, T1);

    for (int i = FIRST_SCRATCH_GPR; i <= LAST_SCRATCH_GPR; ++i)
      asm.emitLAddr(i, i<<LOG_BYTES_IN_ADDRESS, T1);

    for (int i = FIRST_VOLATILE_GPR; i <= LAST_VOLATILE_GPR; ++i)
      if (i != T1)
	asm.emitLAddr(i, i<<LOG_BYTES_IN_ADDRESS, T1);

    // restore specials
    //
    asm.emitLAddr(REGISTER_ZERO, REGISTER_ZERO<<LOG_BYTES_IN_ADDRESS, T1);
    asm.emitLAddr(FP, FP<<LOG_BYTES_IN_ADDRESS, T1);
      
    // restore last gpr
    //
    asm.emitLAddr(T1, T1<<LOG_BYTES_IN_ADDRESS, T1);

    // resume execution at IP
    //
    asm.emitBCCTR();

    return asm.makeMachineCode().getInstructions();
  }

  /**
   * Generate innermost transition from Java => C code used by native method.
   * on entry:
   *   JTOC = TOC for native call
   *   S0 = threads JNIEnvironment, which contains saved PR reg
   *   S1 = IP address of native function to branch to
   *   Parameter regs R4-R10, FP1-FP6 loaded for call to native
   *   (R3 will be set here before branching to the native function)
   * 
   *   GPR3 (T0), PR regs are available for scratch regs on entry
   *
   * on exit:
   *  RVM JTOC and PR restored
   *  return values from native call stored in stackframe
   */
  private static VM_CodeArray generateInvokeNativeFunctionInstructions() {
    VM_Assembler asm = new VM_Assembler(0);

    // move native code address to CTR reg;
    // do this early so that S1 will be available as a scratch.
    asm.emitMTCTR (S1); 

    //
    // store the return address to the Java to C glue prolog, which is now in LR
    // into transition frame. If GC occurs, the JNIGCMapIterator will cause
    // this ip address to be relocated if the generated glue code is moved.
    //
    asm.emitLAddr (S1, 0, FP);
    asm.emitMFLR  (T0);
    //-#if RVM_FOR_LINUX || RVM_FOR_OSX
    // save return address of JNI method in mini frame (2)
    asm.emitSTAddr(T0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, S1);
    //-#endif
    //-#if RVM_FOR_AIX
    // save return address in stack frame
    asm.emitSTAddr(T0, -JNI_PROLOG_RETURN_ADDRESS_OFFSET, S1);
    //-#endif

    //
    // Load required JNI function ptr into first parameter reg (GPR3/T0)
    // This pointer is an interior pointer to the VM_JNIEnvironment which is
    // currently in S0.
    //
    asm.emitADDI (T0, VM_Entrypoints.JNIExternalFunctionsField.getOffset(), S0);

    //
    // change the vpstatus of the VP to "in Native"
    //
    asm.emitLAddr(PROCESSOR_REGISTER, VM_Entrypoints.JNIEnvSavedPRField.getOffset(), S0);   
    asm.emitLVAL (S0,  VM_Processor.IN_NATIVE);
    asm.emitSTW  (S0,  VM_Entrypoints.vpStatusField.getOffset(), PROCESSOR_REGISTER); 

    // 
    // CALL NATIVE METHOD
    // 
    asm.emitBCCTRL();

    // save the return value in R3-R4 in the glue frame spill area since they may be overwritten
    // if we have to call sysVirtualProcessorYield because we are locked in native.
    if (VM.BuildFor64Addr) {
      asm.emitSTD   (T0, NATIVE_FRAME_HEADER_SIZE, FP);
    } else {
      asm.emitSTW   (T0, NATIVE_FRAME_HEADER_SIZE, FP);
      asm.emitSTW   (T1, NATIVE_FRAME_HEADER_SIZE+BYTES_IN_ADDRESS, FP);
    }

    //
    // try to return virtual processor to vpStatus IN_JAVA
    //
    int label1 = asm.getMachineCodeIndex();
    asm.emitLAddr (S0, 0, FP);                            // get previous frame
    //-#if RVM_FOR_LINUX || RVM_FOR_OSX
    // mimi (1) FP -> mimi(2) FP -> java caller
    asm.emitLAddr (S0, 0, S0);
    //-#endif
    asm.emitLAddr (JTOC, -JNI_JTOC_OFFSET, S0);                 // load JTOC reg
    asm.emitLAddr (PROCESSOR_REGISTER, - JNI_ENV_OFFSET, S0);   // load VM_JNIEnvironment
    asm.emitLAddr (PROCESSOR_REGISTER, VM_Entrypoints.JNIEnvSavedPRField.getOffset(), PROCESSOR_REGISTER); // load PR
    asm.emitLVAL  (S1, VM_Entrypoints.vpStatusField.getOffset());
    asm.emitLWARX (S0, S1, PROCESSOR_REGISTER);                 // get status for processor
    asm.emitCMPI  (S0, VM_Processor.BLOCKED_IN_NATIVE);         // are we blocked in native code?
    VM_ForwardReference fr = asm.emitForwardBC(NE);
    //
    // if blocked in native, call C routine to do pthread_yield
    //
    asm.emitLAddr (T2, VM_Entrypoints.the_boot_recordField.getOffset(), JTOC);       // T2 gets boot record address
    asm.emitLAddr (JTOC, VM_Entrypoints.sysTOCField.getOffset(), T2);                // load TOC for syscalls from bootrecord
    asm.emitLAddr (T1,   VM_Entrypoints.sysVirtualProcessorYieldIPField.getOffset(), T2);  // load addr of function
    asm.emitMTLR  (T1);
    asm.emitBCLRL ();                                          // call sysVirtualProcessorYield in sys.C
    asm.emitB     (label1);                                    // retest the attempt to change status to IN_JAVAE
    //
    //  br to here -not blocked in native
    //
    fr.resolve(asm);
    asm.emitLVAL  (S0,  VM_Processor.IN_JAVA);               // S0  <- new state value
    asm.emitSTWCXr(S0,  S1, PROCESSOR_REGISTER);             // attempt to change state to java
    asm.emitBC    (NE, label1);                              // br if failure -retry lwarx

    //
    // return to caller
    //
    asm.emitLAddr  (S0, 0 , FP);                                // get previous frame
    //-#if RVM_FOR_LINUX || RVM_FOR_OSX
    asm.emitLAddr(S0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, S0);
    //-#endif
    //-#if RVM_FOR_AIX
    asm.emitLAddr(S0, -JNI_PROLOG_RETURN_ADDRESS_OFFSET, S0); // get return address from stack frame
    //-#endif
    asm.emitMTLR  (S0);
    asm.emitBCLR   ();

    return asm.makeMachineCode().getInstructions();
  }
}
