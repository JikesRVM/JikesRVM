/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 *  Generate inline machine instructions for special methods that cannot be 
 *  implemented in java bytecodes. These instructions are generated whenever  
 *  we encounter an "invokestatic" bytecode that calls a method with a 
 *  signature of the form "static native VM_Magic.xxx(...)".
 *  23 Jan 1998 Derek Lieber
 * 
 *  NOTE: when adding a new "methodName" to "generate()", be sure to also 
 * consider how it affects the values on the stack and update 
 * "checkForActualCall()" accordingly.
 * If no call is actually generated, the map will reflect the status of the 
 * locals (including parameters) at the time of the call but nothing on the 
 * operand stack for the call site will be mapped.
 *  7 Jul 1998 Janice Shepherd
 *
 * @author Derek Lieber
 * @author Janice Sheperd
 */
class VM_MagicCompiler implements VM_BaselineConstants, 
				  VM_ObjectLayoutConstants {

  // These constants do not really belong here, but since I am making this change
  // I might as well make it a little better.  All size in bytes.
  static final int SIZE_IP = 4;
  static final int SIZE_TOC = 4;
  static final int SIZE_ADDRESS = 4;
  static final int SIZE_INTEGER = 4;

  //-----------//
  // interface //
  //-----------//
   
  // Generate inline code sequence for specified method.
  // Taken:    compiler we're generating code with
  //           method whose name indicates semantics of code to be generated
  // Returned: nothing
  //
  static void
  generateInlineCode(VM_Compiler compiler, VM_Method methodToBeCalled)
      {
      VM_Atom      methodName       = methodToBeCalled.getName();
      VM_Assembler asm              = compiler.asm;
      int          spSaveAreaOffset = compiler.spSaveAreaOffset;
      
      if (methodName == VM_MagicNames.sysCall0) 
         {
         generateSysCall1(asm, 0, false );
         generateSysCall2(asm, 0);
         generateSysCallRet_I(asm, 0);
         return;
         }
         
      if (methodName == VM_MagicNames.sysCall1) 
         {
		  int valueOffset;
         valueOffset = generateSysCall1(asm, SIZE_INTEGER, false );
		  valueOffset -= SIZE_INTEGER;
         asm.emitL(3, valueOffset,  SP);          // load value
         generateSysCall2(asm, SIZE_INTEGER);
         generateSysCallRet_I(asm, SIZE_INTEGER);
         return;
         }
         
      if (methodName == VM_MagicNames.sysCall2)
         {
		  int valueOffset;
         valueOffset = generateSysCall1(asm, 2 * SIZE_INTEGER, false );
		  valueOffset -= SIZE_INTEGER;
         asm.emitL(3, valueOffset,  SP);		 		 // load value1
		  valueOffset -= SIZE_INTEGER;
         asm.emitL(3 + 1, valueOffset,  SP);		 		 // load value2
         generateSysCall2(asm, 2 * SIZE_INTEGER);
         generateSysCallRet_I(asm, 2 * SIZE_INTEGER);
         return;
         }
         
      if (methodName == VM_MagicNames.sysCall3)
         {
		  int valueOffset;
         valueOffset = generateSysCall1(asm, 3 * SIZE_INTEGER, false );
		  valueOffset -= SIZE_INTEGER;
         asm.emitL(3, valueOffset,  SP);		 		 // load value1
		  valueOffset -= SIZE_INTEGER;
         asm.emitL(3 + 1, valueOffset,  SP);		 		 // load value2
		  valueOffset -= SIZE_INTEGER;
         asm.emitL(3 + 2, valueOffset,  SP);		 		 // load value3
         generateSysCall2(asm, 3 * SIZE_INTEGER);
         generateSysCallRet_I(asm, 3 * SIZE_INTEGER);
         return;
         }
         
      if (methodName == VM_MagicNames.sysCall4)
         {
		  int valueOffset;
         valueOffset = generateSysCall1(asm, 4 * SIZE_INTEGER, false );
		  valueOffset -= SIZE_INTEGER;
         asm.emitL(3, valueOffset,  SP);		 		 // load value1
		  valueOffset -= SIZE_INTEGER;
         asm.emitL(3 + 1, valueOffset,  SP);		 		 // load value2
		  valueOffset -= SIZE_INTEGER;
         asm.emitL(3 + 2, valueOffset,  SP);		 		 // load value3
		  valueOffset -= SIZE_INTEGER;
         asm.emitL(3 + 3, valueOffset,  SP);		 		 // load value4
         generateSysCall2(asm, 4 * SIZE_INTEGER);
         generateSysCallRet_I(asm, 4 * SIZE_INTEGER);
         return;
         }
         
      if (methodName == VM_MagicNames.sysCall_L_0) 
         {
         generateSysCall1(asm, 0, false );
         generateSysCall2(asm, 0);
         generateSysCallRet_L(asm, 0);
         return;
         }
         
      if (methodName == VM_MagicNames.sysCall_L_I) // returns long, 1 integer parameter
         {
		  int valueOffset;
         valueOffset = generateSysCall1(asm, SIZE_INTEGER, false );
		  valueOffset -= SIZE_INTEGER;
         asm.emitL(3, valueOffset,  SP);          // load value
         generateSysCall2(asm, SIZE_INTEGER);
         generateSysCallRet_L(asm, SIZE_INTEGER);
         return;
         }
         
      if (methodName == VM_MagicNames.sysCallAD) // returns intetger, 1 address 1 double par.
		  {
		  int valueOffset;
         valueOffset = generateSysCall1(asm, 3 * SIZE_INTEGER, false );
		  valueOffset -= SIZE_INTEGER;
         asm.emitL(3, valueOffset,  SP);		 		 // load value1
		  valueOffset -= SIZE_INTEGER;
         asm.emitLFD(0, valueOffset,  SP);		 		 // load value2
         generateSysCall2(asm, 3 * SIZE_INTEGER);
         generateSysCallRet_I(asm, 3 * SIZE_INTEGER);
         return;
         }
         
      if (methodName == VM_MagicNames.sysCallSigWait)
         {
         int   ipOffset = VM.getMember("LVM_Registers;",   "ip",  "I").getOffset();
         int gprsOffset = VM.getMember("LVM_Registers;", "gprs", "[I").getOffset();
 
         asm.emitL   (T0, 0, SP);	// t0 := address of VM_Registers object
         asm.emitCAL (SP, 4, SP);	// pop address of VM_Registers object
         asm.emitBL  (1);                 
         asm.emitMFLR(0);
	 asm.emitST  (0, ipOffset, T0 ); // store ip into VM_Registers Object
         asm.emitL   (T0, gprsOffset, T0); // TO <- registers.gprs[]
         asm.emitST  (FP, FP*4, T0);  
	 int valueOffset;
         valueOffset = generateSysCall1(asm, 2 * SIZE_INTEGER, true );
	 valueOffset -= SIZE_INTEGER;
         asm.emitL(3, valueOffset,  SP);		 		 // load value1
	 valueOffset -= SIZE_INTEGER;
         asm.emitL(3 + 1, valueOffset,  SP);		 		 // load value2
         generateSysCall2(asm, 2 * SIZE_INTEGER);
         generateSysCallRet_I(asm, 2 * SIZE_INTEGER);
         return;
         }
         
      if (methodName == VM_MagicNames.getFramePointer)
         {
         asm.emitSTU(FP, -4, SP); // push FP
         return;
         }

      if (methodName == VM_MagicNames.getCallerFramePointer)
         {
         asm.emitL (T0, 0, SP);                               // pop  frame pointer of callee frame
         asm.emitL (T1, STACKFRAME_FRAME_POINTER_OFFSET, T0); // load frame pointer of caller frame
         asm.emitST(T1, 0, SP);                               // push frame pointer of caller frame
         return;
         }

      if (methodName == VM_MagicNames.setCallerFramePointer)
         {
         asm.emitL  (T0, +4, SP); // fp
         asm.emitL  (T1,  0, SP); // value
         asm.emitST (T1,  STACKFRAME_FRAME_POINTER_OFFSET, T0); // *(address+SFPO) := value
         asm.emitCAL(SP,  8, SP); // pop address, pop value
         return;
         }

      if (methodName == VM_MagicNames.getCompiledMethodID)
         {
         asm.emitL (T0, 0, SP);                           // pop  frame pointer of callee frame
         asm.emitL (T1, STACKFRAME_METHOD_ID_OFFSET, T0); // load frame pointer of caller frame
         asm.emitST(T1, 0, SP);                           // push frame pointer of caller frame
         return;
         }

      if (methodName == VM_MagicNames.setCompiledMethodID)
         {
         asm.emitL  (T0, +4, SP); // fp
         asm.emitL  (T1,  0, SP); // value
         asm.emitST (T1,  STACKFRAME_METHOD_ID_OFFSET, T0); // *(address+SNIO) := value
         asm.emitCAL(SP,  8, SP); // pop address, pop value
         return;
         }

      if (methodName == VM_MagicNames.getNextInstructionAddress)
         {
         asm.emitL (T0, 0, SP);                                  // pop  frame pointer of callee frame
         asm.emitL (T1, STACKFRAME_NEXT_INSTRUCTION_OFFSET, T0); // load frame pointer of caller frame
         asm.emitST(T1, 0, SP);                                  // push frame pointer of caller frame
         return;
         }

      if (methodName == VM_MagicNames.setNextInstructionAddress)
         {
         asm.emitL  (T0, +4, SP); // fp
         asm.emitL  (T1,  0, SP); // value
         asm.emitST (T1,  STACKFRAME_NEXT_INSTRUCTION_OFFSET, T0); // *(address+SNIO) := value
         asm.emitCAL(SP,  8, SP); // pop address, pop value
         return;
         }

      if (methodName == VM_MagicNames.getReturnAddress)
         {
         asm.emitL (T0, 0, SP);                                  // pop  frame pointer of callee frame
         asm.emitL (T1, STACKFRAME_FRAME_POINTER_OFFSET, T0);    // load frame pointer of caller frame
         asm.emitL (T2, STACKFRAME_NEXT_INSTRUCTION_OFFSET, T1); // load frame pointer of caller frame
         asm.emitST(T2, 0, SP);                                  // push frame pointer of caller frame
         return;
         }

      if (methodName == VM_MagicNames.setReturnAddress)
         {
         asm.emitL  (T0, +4, SP); // fp
         asm.emitL  (T0, STACKFRAME_FRAME_POINTER_OFFSET, T0);    // load frame pointer of caller frame
	 asm.emitL  (T1,  0, SP); // value
         asm.emitST (T1,  STACKFRAME_NEXT_INSTRUCTION_OFFSET, T0); // *(address+SNIO) := value
         asm.emitCAL(SP,  8, SP); // pop address, pop value
         return;
         }
         
      if (methodName == VM_MagicNames.getTocPointer ||
		   methodName == VM_MagicNames.getJTOC)
         {
         asm.emitSTU(JTOC, -4, SP); // push JTOC
         return;
         }
         
      if (methodName == VM_MagicNames.getThreadId)
         {
         asm.emitSTU(TI, -4, SP); // push TI
         return;
         }
         
      if (methodName == VM_MagicNames.setThreadId)
         {
         asm.emitL  (TI, 0, SP); // TI := (shifted) thread index
         asm.emitCAL(SP, 4, SP); // pop threadid arg
         return;
         }
         
      if (methodName == VM_MagicNames.getProcessorRegister)
         {
         asm.emitSTU(PROCESSOR_REGISTER, -4, SP);
         return;
         }

      if (methodName == VM_MagicNames.setProcessorRegister)
         {
         asm.emitL  (PROCESSOR_REGISTER, 0, SP); // register := arg
         asm.emitCAL(SP, 4, SP);                 // pop arg
         return;
         }
         
      if (methodName == VM_MagicNames.getTimeBase)
         {
         loop:
         asm.emitMFTBU(T0);                      // T0 := time base, upper
         asm.emitMFTB (T1);                      // T1 := time base, lower
         asm.emitMFTBU(T2);                      // T2 := time base, upper
         asm.emitCMP  (T0, T2);                  // T0 == T2?
         asm.emitBNE  (-4);                      // lower rolled over, try again

         asm.emitSTU  (T1, -4, SP);              // push low
         asm.emitSTU  (T0, -4, SP);              // push high
         return;
         }

      if (methodName == VM_MagicNames.getTime)
         {
         asm.emitL  (T0, 0, SP); // t0 := address of VM_Processor object
         asm.emitCAL(SP, 4, SP); // pop arg
         
         asm.emitLtoc(S0, VM_Entrypoints.getTimeInstructionsOffset);
         asm.emitMTLR(S0);
         asm.emitCall(spSaveAreaOffset);             // call out of line machine code

         asm.emitSTFDU (F0, -8, SP); // push return value
         return;
         }

      if (methodName == VM_MagicNames.invokeMain)
         {
         asm.emitL   (T0, 0, SP); // t0 := ip
         asm.emitMTLR(T0);
         asm.emitCAL (SP, 4, SP); // pop ip
         asm.emitL   (T0, 0, SP); // t0 := parameter
         asm.emitCall(spSaveAreaOffset);          // call
         asm.emitCAL (SP, 4, SP); // pop parameter
         return;
         }

      if (methodName == VM_MagicNames.invokeClassInitializer)
         {
         asm.emitL   (T0, 0, SP); // t0 := address to be called
         asm.emitCAL (SP, 4, SP); // pop ip
         asm.emitMTLR(T0);
         asm.emitCall(spSaveAreaOffset);          // call
         return;
         }

      if (methodName == VM_MagicNames.invokeMethodReturningVoid)
         {
         generateMethodInvocation(asm, spSaveAreaOffset); // call method
         return;
         }
      if (methodName == VM_MagicNames.invokeMethodReturningInt)
         {
         generateMethodInvocation(asm, spSaveAreaOffset); // call method
         asm.emitSTU(T0, -4, SP);       // push result
         return;
         }
      if (methodName == VM_MagicNames.invokeMethodReturningLong)
         {
         generateMethodInvocation(asm, spSaveAreaOffset); // call method
         asm.emitSTU(T1, -4, SP);       // push result
         asm.emitSTU(T0, -4, SP);       // push result
         return;
         }
      if (methodName == VM_MagicNames.invokeMethodReturningFloat)
         {
         generateMethodInvocation(asm, spSaveAreaOffset); // call method
         asm.emitSTFSU(F0, -4, SP);     // push result
         return;
         }
      if (methodName == VM_MagicNames.invokeMethodReturningDouble)
         {
         generateMethodInvocation(asm, spSaveAreaOffset); // call method
         asm.emitSTFDU(F0, -8, SP);     // push result
         return;
         }
      if (methodName == VM_MagicNames.invokeMethodReturningObject)
         {
         generateMethodInvocation(asm, spSaveAreaOffset); // call method
         asm.emitSTU(T0, -4, SP);       // push result
         return;
         }

      if (methodName == VM_MagicNames.getIntAtOffset ||
		   methodName == VM_MagicNames.getObjectAtOffset)
      {
		  asm.emitL  (T0, +4, SP); // pop object
		  asm.emitL  (T1,  0, SP); // pop offset
		  asm.emitLX (T0, T1, T0); // *(object+offset)
		  asm.emitSTU (T0, 4, SP); // push *(object+offset)
		  return;
      }

      if (methodName == VM_MagicNames.setIntAtOffset ||
		   methodName == VM_MagicNames.setObjectAtOffset)
      {
		  asm.emitL  (T0, +8, SP); // pop object
		  asm.emitL  (T1, +4, SP); // pop offset
		  asm.emitL  (T2,  0, SP); // pop newvalue
		  asm.emitSTX(T2, T1, T0); // *(object+offset) = newvalue
		  asm.emitCAL(SP, 12, SP); // drop all args
		  return;
      }

      if (methodName == VM_MagicNames.getLongAtOffset)
      {
		  asm.emitL  (T1, +4, SP); // pop object
		  asm.emitL  (T2,  0, SP); // pop offset
		  asm.emitLX (T0, T1, T2); // *(object+offset)
		  asm.emitCAL(T2, +4, T2); // offset += 4
		  asm.emitLX (T1, T1, T2); // *(object+offset+4)
		  asm.emitST (T0,  0, SP); // *sp := *(object+offset)
		  asm.emitST (T1, +4, SP); // *sp+4 := *(object+offset+4)
		  return;
      }

      if (methodName == VM_MagicNames.setLongAtOffset)
      {
		  asm.emitL  (T0,+12, SP); // pop object
		  asm.emitL  (T1, +8, SP); // pop offset
		  asm.emitL  (T2,  0, SP); // pop newvalue low 
		  asm.emitSTX(T2, T1, T0); // *(object+offset) = newvalue low
		  asm.emitCAL(T1, +4, T1); // offset += 4
		  asm.emitL  (T2, +4, SP); // pop newvalue high 
		  asm.emitSTX(T2, T1, T0); // *(object+offset) = newvalue high
		  asm.emitCAL(SP, 16, SP); // drop all args
		  return;
      }

      if (methodName == VM_MagicNames.getMemoryWord)
         {
         asm.emitL  (T0,  0, SP); // address
         asm.emitL  (T0,  0, T0); // *address
         asm.emitST (T0,  0, SP); // *sp := *address
         return;
         }

      if (methodName == VM_MagicNames.setMemoryWord)
         {
         asm.emitL  (T0,  4, SP); // address
         asm.emitL  (T1,  0, SP); // value
         asm.emitST (T1,  0, T0); // *address := value
         asm.emitCAL(SP,  8, SP); // pop address, pop value
         return;
         }
      
      if (methodName == VM_MagicNames.prepare)
         {
	 asm.emitL    (T0,  4, SP); // pop object
	 asm.emitL    (T1,  0, SP); // pop offset
	 if (VM.BuildForSingleVirtualProcessor) {
	   asm.emitLX (T0, T1, T0); // *(object+offset)
	 } else {
	   asm.emitLWARX(T0,  T1, T0); // *(object+offset), setting processor's reservation address
	 }
	 asm.emitSTU (T0,  4, SP); // push *(object+offset)
         return;
         }

      if (methodName == VM_MagicNames.attempt)
         {
         asm.emitL     (T0, 12, SP);  // pop object
         asm.emitL     (T1,  8, SP);  // pop offset
         asm.emitL     (T2,  0, SP);  // pop newValue (ignore oldValue)
	 if (VM.BuildForSingleVirtualProcessor) 
            {
	    asm.emitSTX   (T2,  T1, T0); // store new value (on one VP this succeeds by definition)
	    } else {
	    asm.emitSTWCXr(T2,  T1, T0); // store new value and set CR0
            asm.emitCAL   (T0,  0, 0);  // T0 := false
            asm.emitBNE   (2);          // skip, if store failed
	    }
         asm.emitCAL   (T0,  1, 0);   // T0 := true
         asm.emitSTU   (T0,  12, SP);  // push success of conditional store
         return;
         }
      
      if (methodName == VM_MagicNames.setThreadSwitchBit)
         {
		  asm.emitCRORC(THREAD_SWITCH_BIT, 0, 0);
         return;
         }
     
      if (methodName == VM_MagicNames.clearThreadSwitchBit)
         {
         asm.emitCRANDC(THREAD_SWITCH_BIT, 0, 0);
         return;
         }

      if (methodName == VM_MagicNames.saveThreadState)
         {
         asm.emitL   (T0, 0, SP); // T0 := address of VM_Registers object
         
         asm.emitLtoc(S0, VM_Entrypoints.saveThreadStateInstructionsOffset);
         asm.emitMTLR(S0);
         asm.emitCall(spSaveAreaOffset); // call out of line machine code
         
         asm.emitCAL(SP, 4, SP);  // pop arg
         return;
         }

      if (methodName == VM_MagicNames.resumeThreadExecution)
         {
         asm.emitL(T0, 0, SP); // T0 := address of VM_Registers object
         asm.emitL(T1, 4, SP); // T1 := address of VM_Thread object
         
         asm.emitLtoc(S0, VM_Entrypoints.resumeThreadExecutionInstructionsOffset);
         asm.emitMTLR(S0);
         asm.emitBLR(); // branch to out of line machine code (does not return)
         return;
         }
         
      if (methodName == VM_MagicNames.restoreHardwareExceptionState)
         {
         asm.emitL(T0, 0, SP); // T0 := address of VM_Registers object

         asm.emitLtoc(S0, VM_Entrypoints.restoreHardwareExceptionStateInstructionsOffset);
         asm.emitMTLR(S0);
         asm.emitBLR(); // branch to out of line machine code (does not return)
         return;
         }
         
      if (methodName == VM_MagicNames.returnToNewStack)
         {
         asm.emitL   (FP, 0, SP);                                  // FP := new stackframe
         asm.emitL   (S0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); // fetch...
         asm.emitMTLR(S0);                                         // ...return address
         asm.emitBLR ();                                           // return to caller
         return;
         }
         
      if (methodName == VM_MagicNames.dynamicBridgeTo)
         {
         if (VM.VerifyAssertions) VM.assert(compiler.klass.isDynamicBridge());
         
         // fetch parameter (address to branch to) into CT register
         //
         asm.emitL(T0, 0, SP);
         asm.emitMTCTR(T0);

         // restore volatile and non-volatile registers
         // (note that these are only saved for "dynamic bridge" methods)
         //
         int offset = compiler.frameSize;

         // restore non-volatile and volatile fprs
         for (int i = LAST_NONVOLATILE_FPR; i >= FIRST_VOLATILE_FPR; --i)
            asm.emitLFD(i, offset -= 8, FP);

         // restore non-volatile gprs
         for (int i = LAST_NONVOLATILE_GPR; i >= FIRST_NONVOLATILE_GPR; --i)
            asm.emitL(i, offset -= 4, FP);
            
         // skip saved thread-id, processor, and scratch registers
         offset -= (FIRST_NONVOLATILE_GPR - LAST_VOLATILE_GPR - 1) * 4;
         
         // restore volatile gprs
         for (int i = LAST_VOLATILE_GPR; i >= FIRST_VOLATILE_GPR; --i)
            asm.emitL(i, offset -= 4, FP);
          
         // pop stackframe
         asm.emitL(FP, 0, FP);
         
         // restore link register
         asm.emitL(S0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);
         asm.emitMTLR(S0);

         asm.emitBCTR(); // branch always, through count register
         return;
         }
         
      if (methodName == VM_MagicNames.objectAsAddress         ||
          methodName == VM_MagicNames.addressAsByteArray      ||
          methodName == VM_MagicNames.addressAsIntArray       ||
          methodName == VM_MagicNames.addressAsObject         ||
          methodName == VM_MagicNames.addressAsType           ||
          methodName == VM_MagicNames.objectAsType            ||
          methodName == VM_MagicNames.objectAsByteArray       ||
          methodName == VM_MagicNames.objectAsShortArray      ||
          methodName == VM_MagicNames.addressAsThread         ||
          methodName == VM_MagicNames.objectAsThread          ||
          methodName == VM_MagicNames.objectAsProcessor       ||
	  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
          methodName == VM_MagicNames.addressAsBlockControl   ||
          methodName == VM_MagicNames.addressAsSizeControl    ||
          methodName == VM_MagicNames.addressAsSizeControlArray   ||
	  //-#if RVM_WITH_CONCURRENT_GC
          methodName == VM_MagicNames.threadAsRCCollectorThread ||
          //-#endif
          //-#endif
          methodName == VM_MagicNames.threadAsCollectorThread ||
          methodName == VM_MagicNames.addressAsRegisters      ||
          methodName == VM_MagicNames.addressAsStack          ||
          methodName == VM_MagicNames.floatAsIntBits          ||
          methodName == VM_MagicNames.intBitsAsFloat          ||
          methodName == VM_MagicNames.doubleAsLongBits        ||
          methodName == VM_MagicNames.longBitsAsDouble)
         {
         // no-op (a type change, not a representation change)
         return;
         }
         
      if (methodName == VM_MagicNames.getObjectType)
         {
         generateGetObjectType(asm);
         return;
         }
         
      if (methodName == VM_MagicNames.getObjectStatus)
         {
         generateGetObjectStatus(asm);
         return;
         }
         
      if (methodName == VM_MagicNames.getArrayLength)
         {
         generateGetArrayLength(asm);
         return;
         }
         
      if (methodName == VM_MagicNames.sync)
         {
         asm.emitSYNC();
         return;
         }
         
      if (methodName == VM_MagicNames.isync)
         {
         asm.emitISYNC();
         return;
         }

      if (methodName == VM_MagicNames.dcbst)
         {
         asm.emitL(T0, 0, SP);    // address
         asm.emitCAL(SP, 4, SP);  // pop
         asm.emitDCBST(0, T0);
         return;
         }

      if (methodName == VM_MagicNames.icbi)
         {
         asm.emitL(T0, 0, SP);    // address
         asm.emitCAL(SP, 4, SP);  // pop
         asm.emitICBI(0, T0);
         return;
         }

      if (methodName == VM_MagicNames.pragmaNoInline)
		  {
		  return; // meaningless for the baseline compiler
         }

      if (methodName == VM_MagicNames.pragmaInline)
		  {
		  return; // meaningless for the baseline compiler
         }

      if (methodName == VM_MagicNames.pragmaNoOptCompile)
		  {
		  return; // meaningless for the optimizing compiler
		 		  // forces baseline compilation
         }

      VM.sysWrite("VM_MagicCompiler.java: no magic for " + methodToBeCalled + "\n");
      if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
      }

    // Indicate if specified VM_Magic method causes a frame to be created on the runtime stack.
    // Taken:   VM_Method of the magic method being called
    // Returned: true if method causes a stackframe to be created
    //
    public static boolean
    checkForActualCall(VM_Method methodToBeCalled)
       {
       VM_Atom methodName = methodToBeCalled.getName();
       return methodName == VM_MagicNames.invokeMain                  ||
              methodName == VM_MagicNames.invokeClassInitializer      ||
              methodName == VM_MagicNames.invokeMethodReturningVoid   ||
              methodName == VM_MagicNames.invokeMethodReturningInt    ||
              methodName == VM_MagicNames.invokeMethodReturningLong   ||
              methodName == VM_MagicNames.invokeMethodReturningFloat  ||
              methodName == VM_MagicNames.invokeMethodReturningDouble ||
              methodName == VM_MagicNames.invokeMethodReturningObject;
      }


   //----------------//
   // implementation //
   //----------------//

   // Generate code to invoke arbitrary method with arbitrary parameters/return value.
   //
   // We generate inline code that calls "VM_OutOfLineMachineCode.reflectiveMethodInvokerInstructions"
   // which, at runtime, will create a new stackframe with an appropriately sized spill area
   // (but no register save area, locals, or operand stack), load up the specified
   // fpr's and gpr's, call the specified method, pop the stackframe, and return a value.
   //
   private static void
   generateMethodInvocation(VM_Assembler asm, int spSaveAreaOffset)
      {
      // On entry the stack looks like this:
      //
      //                       hi-mem
      //            +-------------------------+    \
      //            |         code[]          |     |
      //            +-------------------------+     |
      //            |         gprs[]          |     |
      //            +-------------------------+     |- java operand stack
      //            |         fprs[]          |     |
      //            +-------------------------+     |
      //    SP ->   |         spills[]        |     |
      //            +-------------------------+    /

      // fetch parameters and generate call to method invoker
      //
      asm.emitLtoc (S0, VM_Entrypoints.reflectiveMethodInvokerInstructionsOffset);
      asm.emitL    (T0, 12, SP);        // t0 := code
      asm.emitMTLR (S0);
      asm.emitL    (T1,  8, SP);        // t1 := gprs
      asm.emitL    (T2,  4, SP);        // t2 := fprs
      asm.emitL    (T3,  0, SP);        // t3 := spills
      asm.emitCall(spSaveAreaOffset);
      asm.emitCAL  (SP,  16, SP);       // pop parameters
      }

   // Generate code for "VM_Type VM_Magic.getObjectType(Object object)".
   //
   static void
   generateGetObjectType(VM_Assembler asm)
      {
      // On entry the stack looks like this:
      //
      //                     hi-mem
      //            +-------------------------+    \
      //    SP ->   |    (Object object)      |     |- java operand stack
      //            +-------------------------+    /

      asm.emitL (T0,  0, SP);                   // get object pointer
      asm.emitL (T0,  OBJECT_TIB_OFFSET, T0);   // get type information block pointer
      asm.emitL (T0,  TIB_TYPE_INDEX << 2, T0); // get "type" field from type information block
      asm.emitST(T0,  0, SP);                   // *sp := type
      }

   // Generate code for "int VM_Magic.getObjectStatus(Object object)".
   //
   static void
   generateGetObjectStatus(VM_Assembler asm)
      {
      // On entry the stack looks like this:
      //
      //                     hi-mem
      //            +-------------------------+    \
      //    SP ->   |    (Object object)      |     |- java operand stack
      //            +-------------------------+    /

      asm.emitL (T0,  0, SP);                    // get object pointer
      asm.emitL (T0,  OBJECT_STATUS_OFFSET, T0); // get object status field
      asm.emitST(T0,  0, SP);                    // *sp := length
      }

   // Generate code for "int VM_Magic.getArrayLength(Object object)".
   //
   static void
   generateGetArrayLength(VM_Assembler asm)
      {
      // On entry the stack looks like this:
      //
      //                     hi-mem
      //            +-------------------------+    \
      //    SP ->   |    (Object object)      |     |- java operand stack
      //            +-------------------------+    /

      asm.emitL (T0,  0, SP);                   // get object pointer
      asm.emitL (T0,  ARRAY_LENGTH_OFFSET, T0); // get array length field
      asm.emitST(T0,  0, SP);                   // *sp := length
      }

   // Generate code for "int VM_Magic.sysCallN(int ip, int toc, int val0, int val1, ..., valN-1)".
   // Taken: number of bytes in parameters (not including JTOC, IP)
   //
   static int
   generateSysCall1(VM_Assembler asm, int rawParametersSize, boolean check_stack )
      {
      // Make sure stack has enough space to run the C function and any calls it makes.
      // We must do this prior to calling the function because there's no way to expand our stack
      // if the C function causes a guard page trap: the C stackframe cannot be relocated and
      // its contents cannot be scanned for object references.
      //
      if ( check_stack )
        asm.emitStackOverflowCheck(STACK_SIZE_NATIVE);
      
      // Create a linkage area that's compatible with RS6000 "C" calling conventions.
      // Just before the call, the stack looks like this:
      //
      //                     hi-mem
      //            +-------------------------+  . . . . . . . .
      //            |          ...            |                  \
      //            +-------------------------+                   |
      //            |          ...            |    \              |
      //            +-------------------------+     |             |
      //            |       (int ip)          |     |             |
      //            +-------------------------+     |             |
      //            |       (int toc)         |     |             |
      //            +-------------------------+     |             |
      //            |       (int val0)        |     |  java       |- java
      //            +-------------------------+     |-  operand   |   stack
      //            |       (int val1)        |     |    stack    |    frame
      //            +-------------------------+     |             |
      //            |          ...            |     |             |
      //            +-------------------------+     |             |
      //  SP ->     |      (int valN-1)       |     |             |
      //            +-------------------------+    /              |
      //            |          ...            |                   |
      //            +-------------------------+                   |
      //            |                         | <-- spot for this frame's callee's return address
      //            +-------------------------+                   |
      //            |          MI             | <-- this frame's method id
      //            +-------------------------+                   |
      //            |       saved FP          | <-- this frame's caller's frame
      //            +-------------------------+  . . . . . . . . /
      //            |      saved JTOC         |
      //            +-------------------------+
      //            |      saved SP           |
      //            +-------------------------+  . . . . . . . . . . . . . .
      //            | parameterN-1 save area  | +  \                         \
      //            +-------------------------+     |                         |
      //            |          ...            | +   |                         |
      //            +-------------------------+     |- register save area for |
      //            |  parameter1 save area   | +   |    use by callee        |
      //            +-------------------------+     |                         |
      //            |  parameter0 save area   | +  /                          |  rs6000
      //            +-------------------------+                               |-  linkage
      //        +20 |       TOC save area     | +                             |    area
      //            +-------------------------+                               |
      //        +16 |       (reserved)        | -    + == used by callee      |
      //            +-------------------------+      - == ignored by callee   |
      //        +12 |       (reserved)        | -                             |
      //            +-------------------------+                               |
      //         +8 |       LR save area      | +                             |
      //            +-------------------------+                               |
      //         +4 |       CR save area      | +                             |
      //            +-------------------------+                               |
      //  FP ->  +0 |       (backlink)        | -                             |
      //            +-------------------------+  . . . . . . . . . . . . . . /
      //
      // Notes:
      // 1. C parameters are passed in registers R3...R10
      // 2. space is also reserved on the stack for use by callee
      //    as parameter save area
      // 3. parameters are pushed on the java operand stack left to right
      //    java conventions) but if callee saves them, they will
      //    appear in the parameter save area right to left (C conventions)
      //
      //
      // generateSysCall1  set ups the call
      // generateSysCall2  branches and cleans up
      // generateSysCallRet_<type> fix stack pushes return values
 
      //int parameterAreaSize = (2 + numValues) * 4;    // ip + toc + value0..valueN-1
      int parameterAreaSize = rawParametersSize + SIZE_IP + SIZE_TOC;
      int ipOffset        = parameterAreaSize - SIZE_IP;		 // offset of ip parameter from SP
      int tocOffset       = ipOffset          - SIZE_TOC;		 // offset of toc parameter from SP
      int endValueOffset  = tocOffset;		 		 		 		 // offset of end of value0 parameter from SP

      //int linkageAreaSize   = (6 + numValues + 2) * 4;    // backlink + cr + lr + res + res + toc + saveSP + saveJTOC + values
      int linkageAreaSize   = rawParametersSize +		 		 // values
		 		 		       (2 * SIZE_TOC) +		 		 // saveJTOC & toc
		 		 		       (6 * 4);		 		 		 // backlink + cr + lr + res + res + saveSP

      asm.emitSTU (FP,  -linkageAreaSize, FP);        // create linkage area
      asm.emitST  (JTOC, linkageAreaSize-4, FP);      // save JTOC
      asm.emitST  (SP,   linkageAreaSize-8, FP);      // save SP

      asm.emitL   (JTOC, tocOffset, SP);              // load new TOC
      asm.emitL   (0,    ipOffset,  SP);              // load new IP

      return endValueOffset;
      }


   static void
   generateSysCall2(VM_Assembler asm, int rawParametersSize)
      {
      int parameterAreaSize = rawParametersSize + SIZE_IP + SIZE_TOC;
      int linkageAreaSize   = rawParametersSize +		 		 // values
		 		 		       (2 * SIZE_TOC) +		 		 // saveJTOC & toc
		 		 		       (6 * 4);		 		 		 // backlink + cr + lr + res + res + saveSP

      asm.emitMTLR(0);                                // call desired...
      asm.emitBLRL();                                 // ...function

      asm.emitL   (JTOC, linkageAreaSize - 4, FP);    // restore JTOC
      asm.emitL   (SP,   linkageAreaSize - 8, FP);    // restore SP
      asm.emitCAL (FP,  +linkageAreaSize, FP);        // remove linkage area
      }

   static void
   generateSysCallRet_I(VM_Assembler asm, int rawParametersSize)
      {
      int parameterAreaSize = rawParametersSize + SIZE_IP + SIZE_TOC;

      asm.emitCAL (SP, parameterAreaSize - 4, SP);    // pop args, push space for return value
      asm.emitST  (3, 0, SP);                         // deposit C return value (R3) on stacktop
      }

   static void
   generateSysCallRet_L(VM_Assembler asm, int rawParametersSize)
      {
      int parameterAreaSize = rawParametersSize + SIZE_IP + SIZE_TOC;

      asm.emitCAL (SP, parameterAreaSize - 8, SP);    // pop args, push space for return value
      asm.emitST  (3, 0, SP);                         // deposit C return value (R3) on stacktop
      asm.emitST  (4, 4, SP);                         // deposit C return value (R4) on stacktop
      }

   }

