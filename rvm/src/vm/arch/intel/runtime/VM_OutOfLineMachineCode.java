/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

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
 * via a jtoc offset (obtained from VM_Entrypoints.XXXInstructionsOffset).
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
    resumeThreadExecutionInstructions          = generateResumeThreadExecutionInstructions();
    restoreHardwareExceptionStateInstructions  = generateRestoreHardwareExceptionStateInstructions();
    invokeNativeFunctionInstructions           = generateInvokeNativeFunctionInstructions();
  }

  static INSTRUCTION[] getReflectiveMethodInvokerInstructions() {
    return reflectiveMethodInvokerInstructions;
  }
   
  static INSTRUCTION[] getSaveThreadStateInstructions() {
    return saveThreadStateInstructions;
  }
   
  static INSTRUCTION[] getResumeThreadExecutionInstructions() {
    return resumeThreadExecutionInstructions;
  }
   
  static INSTRUCTION[] getRestoreHardwareExceptionStateInstructions() {
    return restoreHardwareExceptionStateInstructions;
  }

  // there is no getInvokeNativeFunctionInstrucions since
  // we do not wish that this code be ever inlined.

  //----------------//
  // implementation //
  //----------------//

  private static INSTRUCTION[] reflectiveMethodInvokerInstructions;
  private static INSTRUCTION[] saveThreadStateInstructions;
  private static INSTRUCTION[] resumeThreadExecutionInstructions;
  private static INSTRUCTION[] restoreHardwareExceptionStateInstructions;
  private static INSTRUCTION[] invokeNativeFunctionInstructions;
   
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
  private static INSTRUCTION[] generateReflectiveMethodInvokerInstructions() {

    VM_Assembler asm = new VM_Assembler(100);

    /* write at most 2 parameters from registers in the stack.  This is
     * logically equivalent to ParamaterRegisterUnload in the compiler
     */
    int gprs;
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
    asm.emitPUSH_Reg(FP);			// link this frame with next
    asm.emitMOV_Reg_Reg (FP, SP);		// establish base of new frame
    asm.emitMOV_RegDisp_Imm (FP, STACKFRAME_METHOD_ID_OFFSET, INVISIBLE_METHOD_ID);
    asm.emitADD_Reg_Imm ( SP, STACKFRAME_BODY_OFFSET + WORDSIZE);
    
    // so hardware trap handler can always find it 
    // (opt compiler will reuse FP register)
    VM_ProcessorLocalState.emitMoveRegToField(asm,
                                              VM_Entrypoints.framePointerOffset,
                                              FP);

    /* write parameters on stack 
     * move data from memory addressed by Paramaters array, the fourth
     * parameter to this, into the stack.
     * SP target address
     * S0 source address
     * T1 length
     * T0 scratch
     */
    asm.emitMOV_Reg_RegDisp (S0, FP, PARAMS_FP_OFFSET);// S0 <- Parameters
    asm.emitMOV_Reg_RegDisp (T1, S0, ARRAY_LENGTH_OFFSET);	// T1 <- Parameters.length()
    asm.emitCMP_Reg_Imm (T1, 0);			// length == 0 ?

    int parameterLoopLabel = asm.getMachineCodeIndex();
    VM_ForwardReference fr1 = asm.forwardJcc(asm.EQ);	// done? --> branch to end
    asm.emitMOV_Reg_RegInd (T0, S0);			// T0 <- Paramaters[i]
    asm.emitPUSH_Reg (T0);				// mem[j++] <- Parameters[i]
    asm.emitADD_Reg_Imm (S0, WORDSIZE);			// i++
    asm.emitADD_Reg_Imm (T1, -1);			// length--
    asm.emitJMP_Imm (parameterLoopLabel);

    fr1.resolve(asm);					// end of the loop
    
    /* write fprs onto fprs registers */
    asm.emitMOV_Reg_RegDisp (S0, FP, FPRS_FP_OFFSET);	// S0 <- FPRs
    asm.emitMOV_Reg_RegDisp (T1, S0, ARRAY_LENGTH_OFFSET);	// T1 <- FPRs.length()
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
    asm.emitMOV_Reg_RegDisp (S0, FP, GPRS_FP_OFFSET);	// S0 <- GPRs
    asm.emitMOV_Reg_RegDisp (T1, S0, ARRAY_LENGTH_OFFSET);	// T1 <- GPRs.length()
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
    asm.emitMOV_Reg_RegDisp (S0, FP, CODE_FP_OFFSET);	// S0 <- code
    asm.emitCALL_Reg (S0);				// go there
    // T0/T1 have returned value

    /* and get out */
    asm.emitLEAVE();
    
    // so hardware trap handler can always find it 
    // (opt compiler will reuse FP register)
    VM_ProcessorLocalState.emitMoveRegToField(asm,
                                              VM_Entrypoints.framePointerOffset,
                                              FP);

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
  private static INSTRUCTION[] generateSaveThreadStateInstructions() {
    if (VM.VerifyAssertions) VM.assert(NUM_NONVOLATILE_FPRS == 0); // assuming no NV FPRs (otherwise would have to save them here)
    VM_Assembler asm = new VM_Assembler(0);
    int   fpOffset = VM.getMember("LVM_Registers;",   "fp",  "I").getOffset();
    int gprsOffset = VM.getMember("LVM_Registers;", "gprs", "[I").getOffset();
    asm.emitMOV_RegDisp_Reg(T0, fpOffset, FP);        // registers.fp := FP // for use by stack walkers
    asm.emitPOP_Reg        (T1);                      // T1 := return address // note: registers.ip is set in VM_Processor.dispatch()
    asm.emitADD_Reg_Imm    (SP, 4);                   // throw away space for registers parameter (in T0)
    asm.emitMOV_Reg_RegDisp(S0, T0, gprsOffset);      // S0 := registers.gprs[]
    asm.emitMOV_RegDisp_Reg(S0, SP<<LG_WORDSIZE, SP); // registers.gprs[#SP] := SP
    asm.emitMOV_RegDisp_Reg(S0, FP<<LG_WORDSIZE, FP); // registers.gprs[#FP] := FP
    for (int i=0; i<NUM_NONVOLATILE_GPRS; i++) {
      asm.emitMOV_RegDisp_Reg(S0, NONVOLATILE_GPRS[i]<<LG_WORDSIZE, NONVOLATILE_GPRS[i]); // registers.gprs[i] := i'th register
    }
    asm.emitJMP_Reg        (T1);                      // return to return address
    return asm.getMachineCodes();
  }
      
  /**
   *  Machine code to implement "VM_Magic.resumeThreadExecution()".
   * 
   *  Registers taken at runtime:
   *    T0 == address of VM_Thread object (for the old thread)
   *    T1 == address of VM_Registers object (for the new thread)
   * 
   *  Registers returned at runtime:
   *   none
   * 
   * Side effects at runtime:
   *  - sets thread's "beingDispatched" field to false
   *  - thread state is restored, with execution resuming at
   *    address specified by restored VM_Registers object's ip field
   */
  private static INSTRUCTION[] generateResumeThreadExecutionInstructions() {
    if (VM.VerifyAssertions) VM.assert(NUM_NONVOLATILE_FPRS == 0); // assuming no NV FPRs (otherwise would have to restore them here)
    VM_Assembler asm = new VM_Assembler(0);
    int   ipOffset = VM.getMember("LVM_Registers;",   "ip",  "I").getOffset();
    int   fpOffset = VM.getMember("LVM_Registers;",   "fp",  "I").getOffset();
    int gprsOffset = VM.getMember("LVM_Registers;", "gprs", "[I").getOffset();
    // T0 is previous thread
    // T1 is VM_Registers for thread to be resumed
    asm.emitMOV_RegDisp_Imm(T0, VM_Entrypoints.beingDispatchedOffset, 0); // previous thread's stack is nolonger in use, so it can now be dispatched on any virtual processor 
    asm.emitMOV_Reg_RegDisp(S0, T1, fpOffset);        // S0 := registers.fp
    VM_ProcessorLocalState.emitMoveRegToField(asm,
                                              VM_Entrypoints.framePointerOffset,
                                              S0);
    asm.emitMOV_Reg_RegDisp(S0, T1, gprsOffset);      // S0 := registers.gprs[]
    asm.emitMOV_Reg_RegDisp(SP, S0, SP<<LG_WORDSIZE); // SP := registers.gprs[#SP]
    asm.emitMOV_Reg_RegDisp(FP, S0, FP<<LG_WORDSIZE); // FP := registers.gprs[#FP]
    for (int i=0; i<NUM_NONVOLATILE_GPRS; i++) {
      asm.emitMOV_Reg_RegDisp(NONVOLATILE_GPRS[i], S0, NONVOLATILE_GPRS[i]<<LG_WORDSIZE); // i'th register := registers.gprs[i]
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
  private static INSTRUCTION[] generateRestoreHardwareExceptionStateInstructions() {
    VM_Assembler asm = new VM_Assembler(0);
    int ipOffset   = VM.getMember("LVM_Registers;", "ip", "I").getOffset();
    int fpOffset   = VM.getMember("LVM_Registers;",   "fp",  "I").getOffset();
    int gprsOffset = VM.getMember("LVM_Registers;", "gprs", "[I").getOffset();
    int fprsOffset = VM.getMember("LVM_Registers;", "fprs", "[D").getOffset(); //TODO!

    // Restore the floating point registers and/or fp state
    /* TODO: We need to get the C signal handler to store the floating point state
       in the VM_Registers object before we can restore it here.
       Think about whether we want to store just the FPRs, or if it makes more sense to
       store the entire floating point state and then do a bulk restore of it here.
    asm.emitMOV_Reg_RegDisp (S0, T0, fprsOffset);
    asm.emitFNINIT(); // clear fp state
    // Reload FP stack from registers.fprs by pushing them onto the stack
    for (int i= NUM_FPRS; i >0; i--) {
      asm.emitFLD_Reg_RegDisp_Quad(FP0, S0, (i-1) << (LG_WORDSIZE+1));
    }
    */

    // Set PR.framePointer to be registers.fp
    asm.emitMOV_Reg_RegDisp(S0, T0, fpOffset); 
    VM_ProcessorLocalState.emitMoveRegToField(asm,
                                              VM_Entrypoints.framePointerOffset,
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

//-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
// alternate implementation of jni - not implemented for IA32
//-#else
// default implementation of jni

  // Out of line prolog/epilog called from generated prologues for user
  // written native methods (see VM_JNICompiler).  Completes the call
  // into native code from java and handles the return from native back
  // to java.
  //
  // on entry assume:
  //   TOC = TOC for native call
  //   S0  = address of native function to branch to
  //
  private static INSTRUCTION[]
  generateInvokeNativeFunctionInstructions() {
    VM_Assembler asm = new VM_Assembler(0);

    // save PR in glue frame - to be relocated by GC
    VM_ProcessorLocalState.emitStoreProcessor(asm, FP, 
                                              VM_JNICompiler.JNI_PR_OFFSET);

    // save callers ret addr in glue frame
    asm.emitPOP_RegDisp (FP, VM_JNICompiler.JNI_RETURN_ADDRESS_OFFSET);

    // change processor status to IN_NATIVE
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, T0, 
                                              VM_Entrypoints.vpStatusAddressOffset);
    asm.emitMOV_RegInd_Imm(T0, VM_Processor.IN_NATIVE);

    // make the call...
    asm.emitCALL_Reg(S0);

    // return from native code here...
    // T0 contains single word return value from native
    // T1 ...

    // push return values on stack
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(T1);

    int retryLabel = asm.getMachineCodeIndex();     // backward branch label

    // reload PR ref from glue frame
    VM_ProcessorLocalState.emitLoadProcessor(asm, FP,
                                             VM_JNICompiler.JNI_PR_OFFSET);

    // reload JTOC from processor NOTE: JTOC saved in glue frame may not be
    // the RVM JTOC 
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC,
                                              VM_Entrypoints.jtocOffset);

    // S0<-addr of statusword
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0,
                                              VM_Entrypoints.vpStatusAddressOffset);

    asm.emitMOV_Reg_RegInd(T0,S0);                         // T0<-contents of statusword 
    asm.emitCMP_Reg_Imm (T0, VM_Processor.IN_NATIVE);      // jmp if still IN_NATIVE
    VM_ForwardReference fr = asm.forwardJcc(asm.EQ);       // if so, skip 3 instructions

    // blocked in native, do pthread yield
    asm.emitMOV_Reg_RegDisp(T0, JTOC, VM_Entrypoints.the_boot_recordOffset);  // T0<-bootrecord addr
    asm.emitCALL_RegDisp(T0, VM_Entrypoints.sysVirtualProcessorYieldIPOffset);
    asm.emitJMP_Imm (retryLabel);                          // retry from beginning

    fr.resolve(asm);      // branch here if IN_NATIVE, attempt to go to IN_JAVA

    // T0 (EAX) contains "old value" (required for CMPXCNG instruction)
    // S0 contains address of status word to be swapped
    asm.emitMOV_Reg_Imm (T1, VM_Processor.IN_JAVA);  // T1<-new value (IN_JAVA)
    asm.emitCMPXCHG_RegInd_Reg(S0,T1);               // atomic compare-and-exchange
    asm.emitJCC_Cond_Imm(asm.NE,retryLabel);
									
    // status is now IN_JAVA. GC can not occur while we execute on a processor
    // in this state, so it is safe to access fields of objects

    // Test if returning to Java on a RVM processor or a Native processor.

    VM_ProcessorLocalState.emitMoveFieldToReg(asm, T0,
                                              VM_Entrypoints.processorModeOffset);
    asm.emitCMP_Reg_Imm (T0, VM_Processor.RVM);           // test for RVM
    VM_ForwardReference fr1 = asm.forwardJcc(asm.EQ);     // Br if yes

    // If here, on a native processor, it is necessary to transfer back to a
    // RVM processor before returning to the Java calling method.

    // !!! volatile FPRs will be lost during the yield to the transfer
    // queue of the RVM processor, and later redispatch on that processor.
    // ADD A SAVE OF FPR return reg (top on FPR stack?) before trnsferring
    // branch to becomeRVMThread to make the transfer

    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.becomeRVMThreadOffset);

    // execution here is now on the RVM processor, and on a different
    // os pThread. non-volatile GRPs & FPRs have been saved and restored
    // during the transfer. PR now points to the RVM processor we have
    // been transferred to.

    // XXX Restore the saved FPR return reg, before returning

    fr1.resolve(asm);  // branch to here if returning on a RVM processor

    // pop return values off stack into expected regs before returning to caller
    asm.emitPOP_Reg(T1);
    asm.emitPOP_Reg(T0);

    // push callers return address onto stack, prevoiusly saved in glue frame
    asm.emitPUSH_RegDisp (FP, VM_JNICompiler.JNI_RETURN_ADDRESS_OFFSET);

    asm.emitRET();

    return asm.getMachineCodes();
  }
  //-#endif

}
