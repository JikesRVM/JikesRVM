/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.jni.*;

/**
 * A place to put hand written machine code typically invoked by VM_Magic 
 * methods.
 *
 * <p>Hand coding of small inline instruction sequences is typically handled by 
 * each compiler's implementation of VM_Magic methods.  
 * A few VM_Magic methods are so complex that their implementations require 
 * many instructions.  But our compilers do not inline 
 * arbitrary amounts of machine code. We therefore write such code blocks 
 * here, out of line.
 * 
 * <p>These code blocks can be shared by all compilers. They can be branched to
 * via a jtoc offset (obtained from VM_Entrypoints.XXXInstructionsField).
 * 
 * <p> 17 Mar 1999 Derek Lieber (adapted from powerPC version in 2000 
 * by somebody)
 * 
 * <p> 15 Jun 2001 Dave Grove and Bowen Alpern (Derek believed that compilers 
 * could inline these methods if they wanted.  We do not believe this would 
 * be very easy since they return assuming the return address is on the stack.)
 *
 * @author Maria Butrico
 */
class VM_OutOfLineMachineCode implements VM_BaselineConstants {
  //-----------//
  // interface //
  //-----------//
   
  static void init() {
    reflectiveMethodInvokerInstructions        = generateReflectiveMethodInvokerInstructions();
    saveThreadStateInstructions                = generateSaveThreadStateInstructions();
    threadSwitchInstructions                   = generateThreadSwitchInstructions();
    restoreHardwareExceptionStateInstructions  = generateRestoreHardwareExceptionStateInstructions();
    invokeNativeFunctionInstructions           = generateInvokeNativeFunctionInstructions();
  }

  //----------------//
  // implementation //
  //----------------//

  private static VM_CodeArray reflectiveMethodInvokerInstructions;
  private static VM_CodeArray saveThreadStateInstructions;
  private static VM_CodeArray threadSwitchInstructions;
  private static VM_CodeArray restoreHardwareExceptionStateInstructions;
  private static VM_CodeArray invokeNativeFunctionInstructions;
   
  private static final int PARAMS_FP_OFFSET	= WORDSIZE * 2;
  private static final int FPRS_FP_OFFSET	= WORDSIZE * 3;
  private static final int GPRS_FP_OFFSET	= WORDSIZE * 4;
  private static final int CODE_FP_OFFSET	= WORDSIZE * 5;


  /**
   * Machine code for reflective method invocation.
   *
   * VM compiled with NUM_PARAMETERS_GPRS == 0
   *   Registers taken at runtime:
   *     none
   *   Stack taken at runtime:
   *     hi-mem
   *         address of method entrypoint to be called
   *         address of gpr registers to be loaded
   *         address of fpr registers to be loaded
   *         address of parameters area in calling frame
   *         return address
   *     low-mem
   * 
   * VM compiled with NUM_PARAMETERS_GPRS == 1
   *   T0 == address of method entrypoint to be called
   *   Stack taken at runtime:
   *     hi-mem
   *         space ???
   *         address of gpr registers to be loaded
   *         address of fpr registers to be loaded
   *         address of parameters area in calling frame
   *         return address
   *     low-mem
   * 
   * VM compiled with NUM_PARAMETERS_GPRS == 2
   *   T0 == address of method entrypoint to be called
   *   T1 == address of gpr registers to be loaded
   *   Stack taken at runtime:
   *     hi-mem
   *         space ???
   *         space ???
   *         address of fpr registers to be loaded
   *         address of parameters area in calling frame
   *         return address
   *     low-mem
   * 
   * Registers returned at runtime:
   *   standard return value conventions used
   *
   * Side effects at runtime:
   *   artificial stackframe created and destroyed
   *   volatile, and scratch registers destroyed
   *
  */
  private static VM_CodeArray generateReflectiveMethodInvokerInstructions() {
    VM_Assembler asm = new VM_Assembler(100);

    /* write at most 2 parameters from registers in the stack.  This is
     * logically equivalent to ParamaterRegisterUnload in the compiler
     */
    int gprs;
    int fpOffset = VM_Entrypoints.framePointerField.getOffset();
    byte T = T0;
    gprs = NUM_PARAMETER_GPRS;
    int offset = 4 << LG_WORDSIZE;		// we have exactly 4 paramaters
    if (gprs > 0) {
      gprs--;
      asm.emitMOV_RegDisp_Reg(SP, offset, T); 
      T = T1;
      offset -= WORDSIZE;
    }

    if (gprs > 0)
      asm.emitMOV_RegDisp_Reg(SP, offset, T); 
    /* available registers S0, T0, T1 */


    /* push a new frame */
    asm.emitPUSH_RegDisp(PR, fpOffset); // link this frame with next
    VM_ProcessorLocalState.emitMoveRegToField(asm, fpOffset, SP); // establish base of new frame
    asm.emitPUSH_Imm    (INVISIBLE_METHOD_ID);
    asm.emitADD_Reg_Imm (SP, STACKFRAME_BODY_OFFSET);
    
    /* write parameters on stack 
     * move data from memory addressed by Paramaters array, the fourth
     * parameter to this, into the stack.
     * SP target address
     * S0 source address
     * T1 length
     * T0 scratch
     */
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0, fpOffset);
    asm.emitMOV_Reg_RegDisp (S0, S0, PARAMS_FP_OFFSET);// S0 <- Parameters
    asm.emitMOV_Reg_RegDisp (T1, S0, VM_ObjectModel.getArrayLengthOffset());	// T1 <- Parameters.length()
    asm.emitCMP_Reg_Imm     (T1, 0);			// length == 0 ?

    int parameterLoopLabel = asm.getMachineCodeIndex();
    VM_ForwardReference fr1 = asm.forwardJcc(asm.EQ);	// done? --> branch to end
    asm.emitMOV_Reg_RegInd (T0, S0);			// T0 <- Paramaters[i]
    asm.emitPUSH_Reg (T0);				// mem[j++] <- Parameters[i]
    asm.emitADD_Reg_Imm (S0, WORDSIZE);			// i++
    asm.emitADD_Reg_Imm (T1, -1);			// length--
    asm.emitJMP_Imm (parameterLoopLabel);

    fr1.resolve(asm);					// end of the loop
    
    /* write fprs onto fprs registers */
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0, fpOffset);
    asm.emitMOV_Reg_RegDisp (S0, S0, FPRS_FP_OFFSET);	// S0 <- FPRs
    asm.emitMOV_Reg_RegDisp (T1, S0, VM_ObjectModel.getArrayLengthOffset());	// T1 <- FPRs.length()
    asm.emitSHL_Reg_Imm (T1, LG_WORDSIZE + 1 );		// length in bytes
    asm.emitADD_Reg_Reg (S0, T1);			// S0 <- last FPR + 8
    asm.emitCMP_Reg_Imm (T1, 0);			// length == 0 ?

    int fprsLoopLabel = asm.getMachineCodeIndex();
    VM_ForwardReference fr2 = asm.forwardJcc(asm.EQ);	// done? --> branch to end
    asm.emitSUB_Reg_Imm ( S0, 2 * WORDSIZE);		// i--
    asm.emitFLD_Reg_RegInd_Quad (FP0, S0);		// frp[fpr_sp++] <-FPRs[i]
    asm.emitSUB_Reg_Imm (T1, 2* WORDSIZE);		// length--
    asm.emitJMP_Imm (fprsLoopLabel);

    fr2.resolve(asm);					// end of the loop


    /* write gprs: S0 = Base address of GPRs[], T1 = GPRs.length */
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0, fpOffset);
    asm.emitMOV_Reg_RegDisp (S0, S0, GPRS_FP_OFFSET);	// S0 <- GPRs
    asm.emitMOV_Reg_RegDisp (T1, S0, VM_ObjectModel.getArrayLengthOffset());	// T1 <- GPRs.length()
    asm.emitCMP_Reg_Imm (T1, 0);			// length == 0 ?
    VM_ForwardReference fr3 = asm.forwardJcc(asm.EQ);	// result 0 --> branch to end
    asm.emitMOV_Reg_RegInd (T0, S0);			// T0 <- GPRs[0]
    asm.emitADD_Reg_Imm (S0, WORDSIZE);			// S0 += WORDSIZE
    asm.emitADD_Reg_Imm (T1, -1);			// T1--
    VM_ForwardReference fr4 = asm.forwardJcc(asm.EQ);	// result 0 --> branch to end
    asm.emitMOV_Reg_RegInd (T1, S0);			// T1 <- GPRs[1]
    fr3.resolve(asm);
    fr4.resolve(asm);

    /* branch to method.  On a good day we might even be back */
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0, fpOffset);
    asm.emitMOV_Reg_RegDisp (S0, S0, CODE_FP_OFFSET);	// S0 <- code
    asm.emitCALL_Reg (S0);				// go there
    // T0/T1 have returned value

    /* and get out */
    // NOTE: RVM callee has popped the params, so we can simply
    //       add back in the initial SP to FP delta to get SP to be a framepointer again!
    asm.emitADD_Reg_Imm (SP, -STACKFRAME_BODY_OFFSET + 4); 
    asm.emitPOP_RegDisp (PR, fpOffset);

    asm.emitRET_Imm(4 << LG_WORDSIZE);			// again, exactly 4 parameters

    return asm.getMachineCodes();
  }


  /**
   * Machine code to implement "VM_Magic.saveThreadState()".
   * 
   *  Registers taken at runtime:
   *    T0 == address of VM_Registers object
   * 
   *  Registers returned at runtime:
   *    none
   * 
   *  Side effects at runtime:
   *    S0, T1 destroyed
   *    Thread state stored into VM_Registers object 
   */
  private static VM_CodeArray generateSaveThreadStateInstructions() {
    if (VM.VerifyAssertions) VM._assert(NUM_NONVOLATILE_FPRS == 0); // assuming no NV FPRs (otherwise would have to save them here)
    VM_Assembler asm = new VM_Assembler(0);
    int   ipOffset = VM_Entrypoints.registersIPField.getOffset();
    int   fpOffset = VM_Entrypoints.registersFPField.getOffset();
    int gprsOffset = VM_Entrypoints.registersGPRsField.getOffset();
    asm.emitMOV_Reg_RegDisp(S0, PR, VM_Entrypoints.framePointerField.getOffset()); 
    asm.emitMOV_RegDisp_Reg(T0, fpOffset, S0);        // registers.fp := pr.framePointer
    asm.emitPOP_Reg        (T1);                      // T1 := return address 
    asm.emitMOV_RegDisp_Reg(T0, ipOffset, T1);        // registers.ip := return address
    asm.emitADD_Reg_Imm    (SP, 4);                   // throw away space for registers parameter (in T0)
    asm.emitMOV_Reg_RegDisp(S0, T0, gprsOffset);      // S0 := registers.gprs[]
    asm.emitMOV_RegDisp_Reg(S0, SP<<LG_WORDSIZE, SP); // registers.gprs[#SP] := SP
    for (int i=0; i<NUM_NONVOLATILE_GPRS; i++) {
      asm.emitMOV_RegDisp_Reg(S0, NONVOLATILE_GPRS[i]<<LG_WORDSIZE, NONVOLATILE_GPRS[i]); // registers.gprs[i] := i'th register
    }
    asm.emitJMP_Reg        (T1);                      // return to return address
    return asm.getMachineCodes();
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
    if (VM.VerifyAssertions) VM._assert(NUM_NONVOLATILE_FPRS == 0); // assuming no NV FPRs (otherwise would have to save them here)
    VM_Assembler asm = new VM_Assembler(0);
    int   ipOffset = VM_Entrypoints.registersIPField.getOffset();
    int   fpOffset = VM_Entrypoints.registersFPField.getOffset();
    int gprsOffset = VM_Entrypoints.registersGPRsField.getOffset();
    int regsOffset = VM_Entrypoints.threadContextRegistersField.getOffset();

    // (1) Save hardware state of thread we are switching off of.
    asm.emitMOV_Reg_RegDisp  (S0, T0, regsOffset);      // S0 = T0.contextRegisters
    asm.emitPOP_RegDisp      (S0, ipOffset);            // T0.contextRegisters.ip = returnAddress
    asm.emitPUSH_RegDisp     (PR, VM_Entrypoints.framePointerField.getOffset()); // push PR.framePointer
    asm.emitPOP_RegDisp      (S0, fpOffset);            // T0.contextRegisters.fp = pushed framepointer
    asm.emitADD_Reg_Imm      (SP, 8);                   // discard 2 words of parameters (T0, T1)
    asm.emitMOV_Reg_RegDisp  (S0, S0, gprsOffset);      // S0 = T0.contextRegisters.gprs;
    asm.emitMOV_RegDisp_Reg  (S0, SP<<LG_WORDSIZE, SP); // T0.contextRegisters.gprs[#SP] := SP
    for (int i=0; i<NUM_NONVOLATILE_GPRS; i++) {
      asm.emitMOV_RegDisp_Reg(S0, NONVOLATILE_GPRS[i]<<LG_WORDSIZE, NONVOLATILE_GPRS[i]); // T0.contextRegisters.gprs[i] := i'th register
    }

    // (2) Set currentThread.beingDispatched to false
    asm.emitMOV_RegDisp_Imm(T0, VM_Entrypoints.beingDispatchedField.getOffset(), 0); // previous thread's stack is nolonger in use, so it can now be dispatched on any virtual processor 
    
    // (3) Restore hardware state of thread we are switching to.
    asm.emitMOV_Reg_RegDisp(S0, T1, fpOffset);        // S0 := restoreRegs.fp
    VM_ProcessorLocalState.emitMoveRegToField(asm, VM_Entrypoints.framePointerField.getOffset(), S0); // PR.framePointer = restoreRegs.fp
    asm.emitMOV_Reg_RegDisp(S0, T1, gprsOffset);      // S0 := restoreRegs.gprs[]
    asm.emitMOV_Reg_RegDisp(SP, S0, SP<<LG_WORDSIZE); // SP := restoreRegs.gprs[#SP]
    for (int i=0; i<NUM_NONVOLATILE_GPRS; i++) {
      asm.emitMOV_Reg_RegDisp(NONVOLATILE_GPRS[i], S0, NONVOLATILE_GPRS[i]<<LG_WORDSIZE); // i'th register := restoreRegs.gprs[i]
    }
    asm.emitJMP_RegDisp    (T1, ipOffset);            // return to (save) return address
    return asm.getMachineCodes();
  }
      

  /**
   * Machine code to implement "VM_Magic.restoreHardwareExceptionState()".
   * 
   *  Registers taken at runtime:
   *    T0 == address of VM_Registers object
   * 
   *  Registers returned at runtime:
   *    none
   * 
   *  Side effects at runtime:
   *    all registers are restored except PROCESSOR_REGISTER and EFLAGS;
   *    execution resumes at "registers.ip"
   */
  private static VM_CodeArray generateRestoreHardwareExceptionStateInstructions() {
    VM_Assembler asm = new VM_Assembler(0);

    int   ipOffset = VM_Entrypoints.registersIPField.getOffset();
    int   fpOffset = VM_Entrypoints.registersFPField.getOffset();
    int gprsOffset = VM_Entrypoints.registersGPRsField.getOffset();

    // Set PR.framePointer to be registers.fp
    asm.emitMOV_Reg_RegDisp(S0, T0, fpOffset); 
    VM_ProcessorLocalState.emitMoveRegToField(asm,
                                              VM_Entrypoints.framePointerField.getOffset(),
                                              S0);

    // Restore SP
    asm.emitMOV_Reg_RegDisp (S0, T0, gprsOffset);
    asm.emitMOV_Reg_RegDisp (SP, S0, SP<<LG_WORDSIZE);
    
    // Push registers.ip to stack (now that SP has been restored)
    asm.emitPUSH_RegDisp(T0, ipOffset);

    // Restore the GPRs except for S0, PR, and SP 
    // (restored above and then modified by pushing registers.ip!)
    for (byte i= 0; i < NUM_GPRS; i++) {
      if (i != S0 && i != ESI && i != SP) {
	asm.emitMOV_Reg_RegDisp(i, S0, i<<LG_WORDSIZE);
      }
    }
    
    // Restore S0
    asm.emitMOV_Reg_RegDisp(S0, S0, S0<<LG_WORDSIZE);

    // Return to registers.ip (popping stack)
    asm.emitRET();
    return asm.getMachineCodes();
  }

  // Out of line prolog/epilog called from generated prologues for user
  // written native methods (see VM_JNICompiler).  Completes the call
  // into native code from java and handles the return from native back
  // to java.
  //
  // on entry assume:
  //   S0  = address of native function to branch to
  //
  private static VM_CodeArray generateInvokeNativeFunctionInstructions() {
    VM_Assembler asm = new VM_Assembler(0);

    // save PR in glue frame 
    VM_ProcessorLocalState.emitStoreProcessor(asm, EBP, 
                                              VM_JNICompiler.JNI_PR_OFFSET);

    // save callers ret addr in glue frame
    asm.emitPOP_RegDisp (EBP, VM_JNICompiler.JNI_RETURN_ADDRESS_OFFSET);

    // change processor status to IN_NATIVE
    VM_ProcessorLocalState.emitMoveImmToField(asm, VM_Entrypoints.vpStatusField.getOffset(), 
                                              VM_Processor.IN_NATIVE);
    
    // make the call...
    asm.emitCALL_Reg(S0);

    // return from native code here...
    // T0 contains single word return value from native
    // T1 will contain the second word of a long return value

    // push return values on stack
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(T1);

    int retryLabel = asm.getMachineCodeIndex();     // backward branch label

    // reload PR (virtual processor) from glue frame
    VM_ProcessorLocalState.emitLoadProcessor(asm, EBP,
                                             VM_JNICompiler.JNI_PR_OFFSET);

    // reload JTOC from vitual processor 
    // NOTE: EDI saved in glue frame is just EDI (opt compiled code uses it as normal non-volatile)
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC,
                                              VM_Entrypoints.jtocField.getOffset());

    // T0 gets PR.statusField
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, T0,
                                              VM_Entrypoints.vpStatusField.getOffset());

    asm.emitCMP_Reg_Imm (T0, VM_Processor.IN_NATIVE);      // still IN_NATIVE?
    VM_ForwardReference fr = asm.forwardJcc(asm.EQ);       // if so, skip over call to pthread yield

    // blocked in native, do pthread yield
    asm.emitMOV_Reg_RegDisp(T0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());  // T0<-bootrecord addr
    asm.emitCALL_RegDisp(T0, VM_Entrypoints.sysVirtualProcessorYieldIPField.getOffset());
    asm.emitJMP_Imm (retryLabel);                          // retry from beginning

    fr.resolve(asm);      // branch here if IN_NATIVE, attempt to go to IN_JAVA

    // T0 (EAX) contains "old value" (required for CMPXCNG instruction)
    asm.emitMOV_Reg_Imm (T1, VM_Processor.IN_JAVA);  // T1<-new value (IN_JAVA)
    VM_ProcessorLocalState.emitCompareAndExchangeField(asm, 
                                                       VM_Entrypoints.vpStatusField.getOffset(),
                                                       T1); // atomic compare-and-exchange
    asm.emitJCC_Cond_Imm(asm.NE,retryLabel);
									
    // status is now IN_JAVA (normal operation)

    // pop return values off stack into expected regs before returning to caller
    asm.emitPOP_Reg(T1);
    asm.emitPOP_Reg(T0);

    // push callers return address onto stack, previously saved in glue frame
    asm.emitPUSH_RegDisp (EBP, VM_JNICompiler.JNI_RETURN_ADDRESS_OFFSET);

    asm.emitRET();
    return asm.getMachineCodes();
  }
}
