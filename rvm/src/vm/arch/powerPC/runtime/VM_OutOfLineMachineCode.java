/*
 * (C) Copyright IBM Corp. 2001
 */
// A place to put hand written machine code typically invoked by VM_Magic methods.
//
// Hand coding of small inline instruction sequences is typically handled by each
// compiler's implementation of VM_Magic methods.  A few VM_Magic methods are so complex
// that their implementations require many instructions.  But our compilers do not inline 
// arbitrary amounts of machine code. We therefore write such code blocks here, out of line.
//
// These code blocks can be shared by all compilers. They can be branched to
// via a jtoc offset (obtained from VM_Entrypoints.XXXInstructionsOffset).
//
// 17 Mar 1999 Derek Lieber
//
// 15 Jun 2001 Dave Grove and Bowen Alpern (Derek believed that compilers could inline these
// methods if they wanted.  We do not believe this would be very easy since they return thru
// the LR.)
//
class VM_OutOfLineMachineCode implements VM_BaselineConstants
   {
   //-----------//
   // interface //
   //-----------//
   
   static void
   init()
      {
      reflectiveMethodInvokerInstructions       = generateReflectiveMethodInvokerInstructions();
      saveThreadStateInstructions               = generateSaveThreadStateInstructions();
      resumeThreadExecutionInstructions         = generateResumeThreadExecutionInstructions();
      restoreHardwareExceptionStateInstructions = generateRestoreHardwareExceptionStateInstructions();
      getTimeInstructions                       = generateGetTimeInstructions();
      invokeNativeFunctionInstructions          = generateInvokeNativeFunctionInstructions();
      }

   static INSTRUCTION[]
   getReflectiveMethodInvokerInstructions()
      {
      return reflectiveMethodInvokerInstructions;
      }
   
   static INSTRUCTION[]
   getSaveThreadStateInstructions()
      {
      return saveThreadStateInstructions;
      }
   
   static INSTRUCTION[]
   getResumeThreadExecutionInstructions()
      {
      return resumeThreadExecutionInstructions;
      }
   
   static INSTRUCTION[]
   getRestoreHardwareExceptionStateInstructions()
      {
      return restoreHardwareExceptionStateInstructions;
      }
   
   static INSTRUCTION[]
   getGetTimeInstructions()
      {
      return getTimeInstructions;
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
   generateReflectiveMethodInvokerInstructions()
      {
      VM_Assembler asm = new VM_Assembler(0);
      
      //
      // free registers: 0, S0
      //
 
      asm.emitMFLR(0);                                         // save...
      asm.emitST  (0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); // ...return address
      
      asm.emitMTCTR(T0);                          // CTR := start of method code
      
      //
      // free registers: 0, S0, T0
      //
      
      // create new frame
      //
      asm.emitCAL  (S0,  0, FP);                  // S0 := old frame pointer
      asm.emitL    (T0, ARRAY_LENGTH_OFFSET, T3); // T0 := number of spill words
      asm.emitCAL  (T3, -4, T3);                  // T3 -= 4 (predecrement, ie. T3 + 4 is &spill[0] )
   spillLoop:
      asm.emitAIr  (T0, T0, -1);                  // T0 -= 1 (and set CR)
      asm.emitBLT  (4);                           // if T0 < 0 then break
      asm.emitLU   (0,   4, T3);                  // R0 := *(T3 += 4)
      asm.emitSTU  (0,  -4, FP);                  // put one word of spill area
      asm.emitB    (-4);                          // goto spillLoop:
      
      asm.emitSTU  (S0, -STACKFRAME_HEADER_SIZE, FP);     // allocate frame header and save old fp
      asm.emitLVAL (T0, INVISIBLE_METHOD_ID);
      asm.emitST   (T0, STACKFRAME_METHOD_ID_OFFSET, FP); // set method id

      //
      // free registers: 0, S0, T0, T3
      //
      
      // load up fprs
      //
      asm.emitBL(VOLATILE_FPRS + 1 + VOLATILE_GPRS + 6); // goto setupFPRLoader:

   FPRLoader:
      for (int i = LAST_VOLATILE_FPR; i >= FIRST_VOLATILE_FPR; --i)
         asm.emitLFDU(i, +8, T2);                 // FPRi := fprs[i]
         
      //
      // free registers: 0, S0, T0, T2, T3
      //
      
      // load up gprs
      //
      asm.emitBL(VOLATILE_GPRS + 14);             // goto setupGPRLoader:

   GPRLoader:
      for (int i = LAST_VOLATILE_GPR; i >= FIRST_VOLATILE_GPR; --i)
         asm.emitLU  (i, +4, S0);                 // GPRi := gprs[i]
      
      //
      // free registers: 0, S0
      //
      
      // invoke method
      //
      asm.emitBCTRL();                            // branch and link to method code

      // emit method epilog
      //
      asm.emitL    (FP,  0, FP);                                    // restore caller's frame
      asm.emitL    (S0,  STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);   // pick up return address
      asm.emitMTLR (S0);                                            //
      asm.emitBLR();                                                // return to caller

   setupFPRLoader:
      asm.emitMFLR (T3);                          // T3 := address of first fpr load instruction
      asm.emitL    (T0, ARRAY_LENGTH_OFFSET, T2); // T0 := number of fprs to be loaded
      asm.emitCAL  (T3, VOLATILE_FPRS<<2,    T3); // T3 := address of first instruction following fpr loads
      asm.emitSLI  (T0, T0,                   2); // T0 := number of bytes of fpr load instructions
      asm.emitSF   (T3, T0,                  T3); // T3 := address of instruction for highest numbered fpr to be loaded
      asm.emitMTLR (T3);                          // LR := """
      asm.emitCAL  (T2, -8, T2);                  // predecrement fpr index (to prepare for update instruction)
      asm.emitBLR  ();                            // branch to fpr loading instructions

   setupGPRLoader:
      asm.emitMFLR (T3);                          // T3 := address of first gpr load instruction
      asm.emitL    (T0, ARRAY_LENGTH_OFFSET, T1); // T0 := number of gprs to be loaded
      asm.emitCAL  (T3, VOLATILE_GPRS<<2,    T3); // T3 := address of first instruction following gpr loads
      asm.emitSLI  (T0, T0,                   2); // T0 := number of bytes of gpr load instructions
      asm.emitSF   (T3, T0,                  T3); // T3 := address of instruction for highest numbered gpr to be loaded
      asm.emitMTLR (T3);                          // LR := """
      asm.emitCAL  (S0, -4, T1);                  // predecrement gpr index (to prepare for update instruction)
      asm.emitBLR  ();                            // branch to gpr loading instructions
     
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
   private static INSTRUCTION[]
   generateSaveThreadStateInstructions()
      {
      VM_Assembler asm = new VM_Assembler(0);

      int   ipOffset = VM.getMember("LVM_Registers;",   "ip",  "I").getOffset();
      int fprsOffset = VM.getMember("LVM_Registers;", "fprs", "[D").getOffset();
      int gprsOffset = VM.getMember("LVM_Registers;", "gprs", "[I").getOffset();

      asm.emitLIL(T1, -1);           // T1 = -1
      asm.emitST (T1, ipOffset, T0); // registers.ip = -1

      // save non-volatile fprs
      //
      asm.emitL(T1, fprsOffset, T0); // T1 := registers.fprs[]
      for (int i = FIRST_NONVOLATILE_FPR; i <= LAST_NONVOLATILE_FPR; ++i)
         asm.emitSTFD(i, i*8, T1);

      // save non-volatile gprs
      //
      asm.emitL(T1, gprsOffset, T0); // T1 := registers.gprs[]
      for (int i = FIRST_NONVOLATILE_GPR; i <= LAST_NONVOLATILE_GPR; ++i)
         asm.emitST(i, i*4, T1);

      // save fp, sp, and ti
      //
      asm.emitST(FP, FP*4, T1);
      asm.emitST(TI, TI*4, T1);
      asm.emitST(SP, SP*4, T1);
      
      // return to caller
      //
      asm.emitBLR();
     
      return asm.makeMachineCode().getInstructions();
      }
      
   // Machine code to implement "VM_Magic.resumeThreadExecution()".
   //
   // Registers taken at runtime:
   //   T0 == address of VM_Registers object
   //   T1 == address of VM_Thread object
   //
   // Registers returned at runtime:
   //   none
   //
   // Side effects at runtime:
   // - sets thread's "beingDispatched" field to false
   // - non-volatile registers are restored, with execution resuming at
   //   address specified by restored stackframe's "next instruction" field
   //
   private static INSTRUCTION[]
   generateResumeThreadExecutionInstructions()
      {
      VM_Assembler asm = new VM_Assembler(0);

      int fprsOffset = VM.getMember("LVM_Registers;", "fprs", "[D").getOffset();
      int gprsOffset = VM.getMember("LVM_Registers;", "gprs", "[I").getOffset();

      // set field false
      //
      asm.emitLIL(0, 0);                                        // R0 := 0
      asm.emitST (0, VM_Entrypoints.beingDispatchedOffset, T1); // thread.beingDispatched := R0

      // restore non-volatile fprs
      //
      asm.emitL(T1, fprsOffset, T0); // T1 := registers.fprs[]
      for (int i = FIRST_NONVOLATILE_FPR; i <= LAST_NONVOLATILE_FPR; ++i)
         asm.emitLFD(i, i*8, T1);

      // restore non-volatile gprs
      //
      asm.emitL(T1, gprsOffset, T0); // T1 := registers.gprs[]
      for (int i = FIRST_NONVOLATILE_GPR; i <= LAST_NONVOLATILE_GPR; ++i)
         asm.emitL(i, i*4, T1);

      // restore fp, sp, and ti
      //
      asm.emitL(FP, FP*4, T1);
      asm.emitL(TI, TI*4, T1);
      asm.emitL(SP, SP*4, T1);

      // resume execution at restored stackframe's "next instruction"
      //
      asm.emitL(T1, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);
      asm.emitMTLR(T1);
      asm.emitBLR();
         
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
   private static INSTRUCTION[]
   generateRestoreHardwareExceptionStateInstructions()
      {
      VM_Assembler asm = new VM_Assembler(0);

      int fprsOffset = VM.getMember("LVM_Registers;", "fprs", "[D").getOffset();
      int gprsOffset = VM.getMember("LVM_Registers;", "gprs", "[I").getOffset();
      int lrOffset   = VM.getMember("LVM_Registers;", "lr",   "I" ).getOffset();
      int ipOffset   = VM.getMember("LVM_Registers;", "ip",   "I" ).getOffset();

      // restore LR
      //
      asm.emitL(REGISTER_ZERO, lrOffset, T0);
      asm.emitMTLR(REGISTER_ZERO);

      // restore IP (hold it in CT register for a moment)
      //
      asm.emitL(REGISTER_ZERO, ipOffset, T0);
      asm.emitMTCTR(REGISTER_ZERO);
      
      // restore fprs
      //
      asm.emitL(T1, fprsOffset, T0); // T1 := registers.fprs[]
      for (int i = 0; i < NUM_FPRS; ++i)
         asm.emitLFD(i, i*8, T1);

      // restore gprs
      //
      asm.emitL(T1, gprsOffset, T0); // T1 := registers.gprs[]

      for (int i = FIRST_NONVOLATILE_GPR; i <= LAST_NONVOLATILE_GPR; ++i)
         asm.emitL(i, i*4, T1);

      for (int i = FIRST_SCRATCH_GPR; i <= LAST_SCRATCH_GPR; ++i)
         asm.emitL(i, i*4, T1);

      for (int i = FIRST_VOLATILE_GPR; i <= LAST_VOLATILE_GPR; ++i)
         if (i != T1)
            asm.emitL(i, i*4, T1);

      // restore specials
      //
      asm.emitL(REGISTER_ZERO, REGISTER_ZERO*4, T1);
      asm.emitL(FP, FP*4, T1);
      asm.emitL(TI, TI*4, T1);
      
      // restore last gpr
      //
      asm.emitL(T1, T1*4, T1);

      // resume execution at IP
      //
      asm.emitBCTR();

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
   private static INSTRUCTION[]
   generateGetTimeInstructions()
      {
      VM_Assembler asm = new VM_Assembler(0);

      int scratchSecondsOffset     = VM_Entrypoints.scratchSecondsOffset;
      int scratchNanosecondsOffset = VM_Entrypoints.scratchNanosecondsOffset;

      asm.emitOR    (T3, T0, T0);                             // t3 := address of VM_Processor object
      asm.emitLFDtoc(F2, VM_Entrypoints.billionthOffset, T1); // f2 := 1e-9
      asm.emitLFDtoc(F3, VM_Entrypoints.IEEEmagicOffset, T1); // f3 := IEEEmagic

      asm.emitSTFD  (F3, scratchNanosecondsOffset, T3);       // scratch_nanos   := IEEEmagic
      asm.emitSTFD  (F3, scratchSecondsOffset,     T3);       // scratch_seconds := IEEEmagic

   loop:
      //-#if RVM_FOR_PPCLINUX
      asm.emitMFTBU (T0);                                     // t0 := real time clock, upper
      asm.emitMFTB  (T1);                                     // t1 := real time clock, lower
      asm.emitMFTBU (T2);                                     // t2 := real time clock, upper
      //-#else
      asm.emitMFSPR (T0, 4 );                                 // t0 := real time clock, upper
      asm.emitMFSPR (T1, 5 );                                 // t1 := real time clock, lower
      asm.emitMFSPR (T2, 4 );                                 // t2 := real time clock, upper
      //-#endif
      asm.emitCMP   (T0, T2);                                 // t0 == t2?

      asm.emitST    (T1, scratchNanosecondsOffset + 4, T3);   // scratch_nanos_lo   := nanos
      asm.emitST    (T0, scratchSecondsOffset     + 4, T3);   // scratch_seconds_lo := seconds

      asm.emitBNE   (-6);                                     // seconds have rolled over, try again

      asm.emitLFD   (F0, scratchNanosecondsOffset, T3);       // f0 := IEEEmagic + nanos
      asm.emitLFD   (F1, scratchSecondsOffset,     T3);       // f1 := IEEEmagic + seconds

      asm.emitFS    (F0, F0, F3);                             // f0 := f0 - IEEEmagic == (double)nanos
      asm.emitFS    (F1, F1, F3);                             // f1 := f1 - IEEEmagic == (double)seconds

      asm.emitFMA   (F0, F2, F0, F1);                         // f0 := f2 * f0 + f1

      // return to caller
      //
      asm.emitBLR();

      return asm.makeMachineCode().getInstructions();
      }

//-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
// alternate implementation of jni

   // "checked in" non purple version of native processors

   private static INSTRUCTION[]
   generateInvokeNativeFunctionInstructions() {

      VM_Assembler asm = new VM_Assembler(0);
      int lockoutLockOffset = VM_Entrypoints.lockoutProcessorOffset;

      // storing the return address to the prolog, which is now in LR in the
      // frame.  T3 and V4 (register 7) are used as scratch
      asm.emitL     (T3, 0, FP);
      asm.emitMFLR  (7);
      asm.emitST    (7, -JNI_PROLOG_RETURN_ADDRESS_OFFSET, T3);   // save return address in stack frame


      asm.emitMTLR  (T0);                                     // set up native code address in link reg

      // release the lock that inhibits garbage collection
      // NOTE: from this point on GC can occur soooo all references to memory must  be to the fixed areas
      //
      asm.emitL     (T3, VM_Entrypoints.the_boot_recordOffset, JTOC);       // get boot record address
      asm.emitCAL   (T3, VM_Entrypoints.lockoutProcessorOffset, T3);        // get address of gc lockout word
      asm.emitCAL   (T0, 0, 0);                               // clear a reg
      asm.emitST    (T0, 0, T3);                              // and then clear the lock

      // setup the native code TOC register
      asm.emitCAL   (JTOC, 0, T1);                            // move native code toc register to proper place

      // restore the volatile registers from the spill area since they may not have 
      // survived the pthread switch
      for (int i = FIRST_AIX_VOLATILE_GPR, offset = AIX_FRAME_HEADER_SIZE;
	   i <= LAST_AIX_VOLATILE_GPR; i++, offset+=4) {
	asm.emitL  (i,  offset, FP);
      }

      // goto the native code
      asm.emitBLRL  ();                                       // call native method

      // save the return value in R3-R4 in the glue frame spill area since they may be overwritten
      // in the call to becomeJalapenoThreadOffset
      asm.emitST    (T0, AIX_FRAME_HEADER_SIZE, FP);
      asm.emitST    (T1, AIX_FRAME_HEADER_SIZE+4, FP);

      // get the GC lockout lock on return from native code
      //
      // restore jalapeno JTOC
      int label1    = asm.currentInstructionOffset();	      // inst index in the machine code array of the following load instr.
      asm.emitL     (JTOC, 0, FP);                            // get previous frame
      asm.emitL     (JTOC, -4, JTOC);                         // get real jtoc address  
      asm.emitL     (T2, VM_Entrypoints.the_boot_recordOffset, JTOC);       // T2 gets boot record address
      asm.emitCAL   (T3, VM_Entrypoints.lockoutProcessorOffset, T2);        // T3 gets address of gc lockout word
      //
      asm.emitLWARX (S0, 0, T3);                              // load GC lockput word
      asm.emitCMPI  (0,  S0,  0);                             // compare loaded word to 0, set CR0
      int f_branch_length = 6;
      int label2    = asm.currentInstructionOffset();
      asm.emitBEQ   (f_branch_length);                        // read 0, jump to try and set to 1

      // not zero, call C routine to do pthread_yield
      //
      asm.emitL     (JTOC, VM_Entrypoints.sysTOCOffset, T2);  // load TOC for syscalls from bootrecord
      asm.emitL     (T1,   VM_Entrypoints.sysVirtualProcessorYieldIPOffset, T2);  // load addr of function
      asm.emitMTLR  (T1);
      asm.emitBLRL  ();                                       // call sysVirtualProcessorYield in sys.C
      asm.emitB     ( label1 - asm.currentInstructionOffset() ); // try again to acquire lockout lock

      if (VM.VerifyAssertions) VM.assert ( ( asm.currentInstructionOffset() - label2 ) == f_branch_length );
      // branch to here if lockoutProcessor was 0, try and set to 1
      //
      asm.emitCAL   (S0,  1,  0);                             // S0 <- 1, value to store if lock is zero
      asm.emitSTWCXr(S0,  0, T3);                             // attempt to store new value (1)
      asm.emitBNE   ( label1 - asm.currentInstructionOffset() ); // retry lwarx


      // return to caller
      //
      asm.emitL     (T3, 0 , FP);
      asm.emitL     (S0, -JNI_PROLOG_RETURN_ADDRESS_OFFSET, T3);   // get return address from stack frame
      asm.emitMTLR  (S0);
      asm.emitBLR   ();

      return asm.makeMachineCode().getInstructions();

   } // generateInvokeNativeFunctionInstructions - alternate implementation

//-#else
// default implementation of jni

  // on entry:
  //   JTOC = TOC for native call
  //   TI - IP address of native function to branch to
  //   S0 -> threads JNIEnvironment, which contains saved PR & TI regs
  //   Parameter regs R4-R10, FP1-FP6 loaded for call to native
  //   (R3 will be set here before branching to the native function)
  // 
  //   GPR3 (T0), SP, PR regs are available for scratch regs on entry
  //
  private static INSTRUCTION[]
  generateInvokeNativeFunctionInstructions() {

    VM_Assembler asm = new VM_Assembler(0);
    int lockoutLockOffset = VM_Entrypoints.lockoutProcessorOffset;
    //
    // store the return address to the Java to C glue prolog, which is now in LR
    // into transition frame. If GC occurs, the JNIGCMapIterator will cause
    // this ip address to be relocated if the generated glue code is moved.
    //
    asm.emitL     (SP, 0, FP);
    asm.emitMFLR  (T0);
    asm.emitST    (T0, -JNI_PROLOG_RETURN_ADDRESS_OFFSET, SP);  // save return address in stack frame
    //
    // Load required JNI function ptr into first parameter reg (GPR3/T0)
    // This pointer is in the JNIEnvAddress field of JNIEnvironment
    //
    asm.emitL (T0, VM_Entrypoints.JNIEnvAddressOffset, S0);
    //
    // change the vpstatus of the VP to "in Native"
    //
    asm.emitL     (PROCESSOR_REGISTER, VM_Entrypoints.JNIEnvSavedPROffset, S0); 
    asm.emitL     (SP, VM_Entrypoints.vpStatusAddressOffset, PROCESSOR_REGISTER); // SP gets addr vpStatus word
    asm.emitCAL   (S0,  VM_Processor.IN_NATIVE, 0 );              // S0  <- new status value
    asm.emitST    (S0,  0, SP);                                   // change state to native

    // set word following JNI function ptr to addr of current processors vpStatus word
    asm.emitST    (SP,  4, T0);

    //
    asm.emitMTLR  (TI);                                // move native code address to link reg
    //
    // goto the native code
    //
    asm.emitBLRL  ();                                       // call native method
    //
    // save the return value in R3-R4 in the glue frame spill area since they may be overwritten
    // in the call to becomeJalapenoThreadOffset
    asm.emitST    (T0, AIX_FRAME_HEADER_SIZE, FP);
    asm.emitST    (T1, AIX_FRAME_HEADER_SIZE+4, FP);
    //
    // try to return to Java state, by testing state word of process
    //
    int label1    = asm.currentInstructionOffset();	                    // inst index of the following load
    asm.emitL     (PROCESSOR_REGISTER, 0, FP);                            // get previous frame
    asm.emitL     (JTOC, -4, PROCESSOR_REGISTER);                         // load JTOC reg
    asm.emitL     (PROCESSOR_REGISTER, - JNI_PR_OFFSET, PROCESSOR_REGISTER); //load processor register  
    asm.emitL     (T3, VM_Entrypoints.vpStatusAddressOffset, PROCESSOR_REGISTER); // T3 gets addr of vpStatus word
    asm.emitLWARX (S0, 0, T3);                                            // get status for processor
    asm.emitCMPI  (S0, VM_Processor.BLOCKED_IN_NATIVE);                   // are we blocked in native code
    asm.emitBNE   (+7);                                                   // br if not blocked
    //
    // if blocked in native, call C routine to do pthread_yield
    //
    asm.emitL     (T2, VM_Entrypoints.the_boot_recordOffset, JTOC);       // T2 gets boot record address
    asm.emitL     (JTOC, VM_Entrypoints.sysTOCOffset, T2);                // load TOC for syscalls from bootrecord
    asm.emitL     (T1,   VM_Entrypoints.sysVirtualProcessorYieldIPOffset, T2);  // load addr of function
    asm.emitMTLR  (T1);
    asm.emitBLRL  ();                                          // call sysVirtualProcessorYield in sys.C
    asm.emitB     ( label1 - asm.currentInstructionOffset() ); // retest the blocked in native
    //
    //  br to here -not blocked in native
    //
    asm.emitCAL   (S0,  VM_Processor.IN_JAVA, 0 );           // S0  <- new state value
    asm.emitSTWCXr(S0,  0, T3);                              // attempt to change state to java
    asm.emitBNE   ( label1 - asm.currentInstructionOffset() ); // br if failure -retry lwarx
    //
    // return to caller
    //
    asm.emitL     (T3, 0 , FP);                             // get previous frame
    asm.emitL     (S0, -JNI_PROLOG_RETURN_ADDRESS_OFFSET, T3);   // get return address from stack frame
    asm.emitMTLR  (S0);
    asm.emitBLR   ();
    //
    return asm.makeMachineCode().getInstructions();

  }  // generateInvokeNativeFunctionInstructions - default implementation

//-#endif

   }


