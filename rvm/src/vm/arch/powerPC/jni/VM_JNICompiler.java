/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.jni;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

/**
 * @author Ton Ngo 
 * @author Steve Smith
 * modified by Kris Venstermans ( 64-bit port AIX)
 */
public class VM_JNICompiler implements VM_BaselineConstants,
				       VM_AssemblerConstants {

  /**
   * This method creates the stub to link native method.  It will be called
   * from the lazy linker the first time a native method is invoked.  The stub
   * generated will be patched by the lazy linker to link to the native method
   * for all future calls. <p>
   * <pre>
   * The stub performs the following tasks in the prologue:
   *   -Allocate the glue frame
   *   -Save the TI and PR registers in the JNI Environment for reentering Java later
   *   -Shuffle the parameters in the registers to conform to the AIX convention
   *   -Save the nonvolatile registers in a known space in the frame to be used 
   *    for the GC stack map
   *   -Push a new JREF frame on the JNIRefs stack
   *   -Supply the first JNI argument:  the JNI environment pointer
   *   -Supply the second JNI argument:  class object if static, "this" if virtual
   *   -Hardcode the TOC and IP to the corresponding native code
   *
   * The stub performs the following tasks in the epilogue:
   *   -TI and PR registers are AIX nonvolatile, so they should be restored already
   *   -Restore the nonvolatile registers if GC has occurred
   *   -Pop the JREF frame off the JNIRefs stack
   *   -Check for pending exception and deliver to Java caller if present
   *   -Process the return value from native:  push onto caller's Java stack  
   *  
   * The stack frame created by this stub conforms to the AIX convention:
   *   -6-word frame header
   *   -parameter save/spill area
   *   -one word flag to indicate whether GC has occurred during the native execution
   *   -16-word save area for nonvolatile registers
   *  
   *   | fp       | <- native frame
   *   | cr       |
   *   | lr       |
   *   | resv     |
   *   | resv     |
   *   + toc      +
   *   |          |
   *   |          |
   *   |----------|  
   *   | fp       | <- Java to C glue frame
   *   | cr/mid   |
   *   | lr       |
   *   | resv     |
   *   | resv     |
   *   + toc      +
   *   |   0      | spill area (at least 8 words reserved)
   *   |   1      | (also used for saving volatile regs during calls in prolog)
   *   |   2      |
   *   |   3      |
   *   |   4      |
   *   |   5      |
   *   |   6      |
   *   |   7      |
   *   |  ...     | 
   *   |          |
   *   |GC flag   | offset = JNI_SAVE_AREA_OFFSET           <- JNI_GC_FLAG_OFFSET
   *   |affinity  | saved VM_Thread.processorAffinity       <- JNI_AFFINITY_OFFSET
   *   |vol fpr1  | saved AIX volatile fpr during becomeNativeThread
   *   | ...      | 
   *   |vol fpr6  | saved AIX volatile fpr during becomeNativeThread
   *   |vol r4    | saved AIX volatile regs during Yield (to be removed when code moved to Java)   
   *   | ...      | 
   *   |vol r10   | saved AIX volatile regs during Yield    <- JNI_OS_PARAMETER_REGISTER_OFFSET
   *   |ENV       | VM_JNIEnvironment                       <- JNI_ENV_OFFSET
   *   |nonvol 17 | save 15 nonvolatile registers for stack mapper
   *   | ...      |
   *   |nonvol 31 |                                         <- JNI_RVM_NONVOLATILE_OFFSET
   *   |savedJTOC | save RVM JTOC for return                <- JNI_JTOC_OFFSET
   *   |----------|   
   *   |  fp   	  | <- Java caller frame
   *   | mid   	  |
   *   | xxx   	  |
   *   |       	  |
   *   |       	  |
   *   |       	  |
   *   |----------|
   *   |       	  |
   * </pre>
   */
  public static synchronized VM_CompiledMethod compile (VM_NativeMethod method) {
    VM_JNICompiledMethod cm = (VM_JNICompiledMethod)VM_CompiledMethods.createCompiledMethod(method, VM_CompiledMethod.JNI);
    int compiledMethodId = cm.getId();
    VM_Assembler asm	= new VM_Assembler(0);
    int frameSize	= VM_Compiler.getFrameSize(method);
    VM_Class klass	= method.getDeclaringClass();

    /* initialization */
    if (VM.VerifyAssertions) VM._assert(T3 <= LAST_VOLATILE_GPR);           // need 4 gp temps
    if (VM.VerifyAssertions) VM._assert(F3 <= LAST_VOLATILE_FPR);           // need 4 fp temps
    if (VM.VerifyAssertions) VM._assert(S0 < S1 && S1 <= LAST_SCRATCH_GPR); // need 2 scratch

    VM_Address nativeIP  = method.getNativeIP();
    VM_Address nativeTOC = method.getNativeTOC();

    // NOTE:  this must be done before the condition VM_Thread.hasNativeStackFrame() become true
    // so that the first Java to C transition will be allowed to resize the stack
    // (currently, this is true when the JNIRefsTop index has been incremented from 0)
    asm.emitNativeStackOverflowCheck(frameSize + 14);   // add at least 14 for C frame (header + spill)

    int parameterAreaSize = method.getParameterWords() << LOG_BYTES_IN_STACKSLOT;   // number of bytes of arguments

    // save return address in caller frame
    asm.emitMFLR(0);
    asm.emitSTAddr(0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);	
  
    //-#if RVM_FOR_LINUX || RVM_FOR_OSX
    // buy mini frame (2)
    asm.emitSTAddrU   (FP, -JNI_SAVE_AREA_SIZE, FP);
    asm.emitLVAL  (S0, compiledMethodId);                // save jni method id at mini frame (2)
    asm.emitSTW   (S0, STACKFRAME_METHOD_ID_OFFSET, FP);
    // buy mini frame (1), the total size equals to frameSize
    asm.emitSTAddrU   (FP, -frameSize + JNI_SAVE_AREA_SIZE, FP);
    //-#endif
	
    //-#if RVM_FOR_AIX
    asm.emitSTAddrU   (FP,  -frameSize, FP);             // get transition frame on stack
    asm.emitLVAL  (S0, compiledMethodId);                // save jni method id
    asm.emitSTW   (S0, STACKFRAME_METHOD_ID_OFFSET, FP);
    //-#endif

    asm.emitSTAddr (JTOC, frameSize - JNI_JTOC_OFFSET, FP);    // save RVM JTOC in frame

    // establish S1 -> VM_Thread, S0 -> threads JNIEnv structure      
    asm.emitLAddr(S1, VM_Entrypoints.activeThreadField.getOffset(), PROCESSOR_REGISTER);
    asm.emitLAddr(S0, VM_Entrypoints.jniEnvField.getOffset(), S1);

    // save the TI & PR registers in the JNIEnvironment object for possible calls back into Java
    asm.emitSTAddr(TI, VM_Entrypoints.JNIEnvSavedTIField.getOffset(), S0);           
    asm.emitSTAddr(PROCESSOR_REGISTER, VM_Entrypoints.JNIEnvSavedPRField.getOffset(), S0);   
    
    // save the JNIEnvironment in the stack frame so we can use it to acquire the PR
    // when we return from native code.
    asm.emitSTAddr (S0, frameSize - JNI_ENV_OFFSET, FP);  // save PR in frame  

    // save current frame pointer in JNIEnv, JNITopJavaFP, which will be the frame
    // to start scanning this stack during GC, if top of stack is still executing in C
    //-#if RVM_FOR_LINUX || RVM_FOR_OSX
    // for Linux, save mini (2) frame pointer, which has method id
    asm.emitLAddr (PROCESSOR_REGISTER, 0, FP);
    asm.emitSTAddr(PROCESSOR_REGISTER, VM_Entrypoints.JNITopJavaFPField.getOffset(), S0);
    //-#elif RVM_FOR_AIX
    asm.emitSTAddr(FP, VM_Entrypoints.JNITopJavaFPField.getOffset(), S0);           
    //-#endif
	
    // save the RVM nonvolatile registers, to be scanned by GC stack mapper
    // remember to skip past the saved JTOC  by starting with offset = JNI_RVM_NONVOLATILE_OFFSET
    //
    for (int i = LAST_NONVOLATILE_GPR, offset = JNI_RVM_NONVOLATILE_OFFSET;
	 i >= FIRST_NONVOLATILE_GPR; --i, offset+=BYTES_IN_STACKSLOT) {
      asm.emitSTAddr(i, frameSize - offset, FP);
    }

    // clear the GC flag on entry to native code
    asm.emitLVAL(PROCESSOR_REGISTER,0);          // use PR as scratch
    asm.emitSTW(PROCESSOR_REGISTER, frameSize-JNI_GC_FLAG_OFFSET, FP);  

    // generate the code to map the parameters to AIX convention and add the
    // second parameter 2 (either the "this" ptr or class if a static method).
    // The JNI Function ptr first parameter is set before making the call.
    // Opens a new frame in the JNIRefs table to register the references
    // Assumes S0 set to JNIEnv, kills TI, S1 & PROCESSOR_REGISTER
    // On return, S0 is still valid.
    //-#if RVM_FOR_LINUX
    storeParametersForLinux(asm, frameSize, method, klass);
    //-#elif RVM_FOR_OSX
    storeParametersForOSX(asm, frameSize, method, klass);
    //-#elif RVM_FOR_AIX
    storeParametersForAIX(asm, frameSize, method, klass);
    //-#endif
	
    // Get address of out_of_line prolog into S1, before setting TOC reg.
    asm.emitLAddr (S1, VM_Entrypoints.invokeNativeFunctionInstructionsField.getOffset(), JTOC);
    asm.emitMTLR  (S1);

    // set the TOC and IP for branch to out_of_line code
    asm.emitLVALAddr (JTOC,  nativeTOC);      // load TOC for native function into TOC reg
    asm.emitLVALAddr (TI,    nativeIP);	      // load TI with address of native code

    // go to VM_OutOfLineMachineCode.invokeNativeFunctionInstructions
    // It will change the Processor status to "in_native" and transfer to the native code.  
    // On return it will change the state back to "in_java" (waiting if blocked).
    //
    // The native address entrypoint is in register TI
    // The native TOC has been loaded into the TOC register
    // S0 still points to threads JNIEnvironment
    //
    asm.emitBCLRL();

    // check if GC has occurred, If GC did not occur, then 
    // VM NON_VOLATILE regs were restored by AIX and are valid.  If GC did occur
    // objects referenced by these restored regs may have moved, in this case we
    // restore the nonvolatile registers from our savearea,
    // where any object references would have been relocated during GC.
    // use T2 as scratch (not needed any more on return from call)
    //
    asm.emitLWZ(T2, frameSize - JNI_GC_FLAG_OFFSET, FP);
    asm.emitCMPI(T2,0);
    VM_ForwardReference fr1 = asm.emitForwardBC(EQ);
    for (int i = LAST_NONVOLATILE_GPR, offset = JNI_RVM_NONVOLATILE_OFFSET;
	 i >= FIRST_NONVOLATILE_GPR; --i, offset+=BYTES_IN_STACKSLOT) {
      asm.emitLAddr(i, frameSize - offset, FP);
    }
    fr1.resolve(asm);
    asm.emitLAddr(S0, VM_Entrypoints.activeThreadField.getOffset(), PROCESSOR_REGISTER);  // S0 holds thread pointer

    // reestablish S0 to hold pointer to VM_JNIEnvironment
    asm.emitLAddr(S0, VM_Entrypoints.jniEnvField.getOffset(), S0);       

    // pop jrefs frame off the JNIRefs stack, "reopen" the previous top jref frame
    // use S1 as scratch before it's restored, also use T2, T3 for scratch which are no longer needed
    asm.emitLAddr(S1, VM_Entrypoints.JNIRefsField.getOffset(), S0);          // load base of JNIRefs array
    asm.emitLInt (T2, VM_Entrypoints.JNIRefsSavedFPField.getOffset(), S0);   // get saved offset for JNIRefs frame ptr previously pushed onto JNIRefs array
    asm.emitADDI (T3, -BYTES_IN_STACKSLOT, T2);                                    // compute offset for new TOP
    asm.emitSTW  (T3, VM_Entrypoints.JNIRefsTopField.getOffset(), S0);       // store new offset for TOP into JNIEnv
    asm.emitLIntX(T2, S1, T2);                                    // retrieve the previous frame ptr
    asm.emitSTW  (T2, VM_Entrypoints.JNIRefsSavedFPField.getOffset(), S0);   // store new offset for JNIRefs frame ptr into JNIEnv

    // Restore the return value R3-R4 saved in the glue frame spill area before the migration
    if (VM.BuildFor64Addr) {
      asm.emitLD(T0, NATIVE_FRAME_HEADER_SIZE, FP);
    } else {
      asm.emitLWZ(T0, NATIVE_FRAME_HEADER_SIZE, FP);
      asm.emitLWZ(T1, NATIVE_FRAME_HEADER_SIZE+BYTES_IN_STACKSLOT, FP);
    }
      
    // if the the return type is a reference, the native C is returning a jref
    // which is a byte offset from the beginning of the threads JNIRefs stack/array
    // of the corresponding ref.  In this case, emit code to replace the returned
    // offset (in R3) with the ref from the JNIRefs array

    VM_TypeReference returnType = method.getReturnType();
    if (returnType.isReferenceType()) {
      // use returned offset to load ref from JNIRefs into R3
      asm.emitLAddrX (T0, S1, T0);         // S1 is still the base of the JNIRefs array
    }

    // reload TI value saved in JNIEnv
    asm.emitLAddr(TI, VM_Entrypoints.JNIEnvSavedTIField.getOffset(), S0);           

    // pop the glue stack frame, restore the Java caller frame
    asm.emitADDI (FP,  +frameSize, FP);              // remove linkage area

    // C return value is already where caller expected it (R3, R4 or F0)
    // and S1 is a scratch register, so we actually don't have to 
    // restore it and/or pop arguments off it.
    // So, just restore the return address to the link register.

    asm.emitLAddr(0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); 
    asm.emitMTLR (0);                           // restore return address

    // CHECK EXCEPTION AND BRANCH TO ATHROW CODE OR RETURN NORMALLY

    asm.emitLAddr (T2, VM_Entrypoints.JNIPendingExceptionField.getOffset(), S0);   // get pending exception from JNIEnv
    asm.emitLVAL  (T3, 0);                   // get a null value to compare
    asm.emitSTAddr(T3, VM_Entrypoints.JNIPendingExceptionField.getOffset(), S0); // clear the current pending exception
    asm.emitCMPAddr(T2, T3);
    VM_ForwardReference fr3 = asm.emitForwardBC(NE);
    asm.emitBCLR();                             // if no pending exception, proceed to return to caller
    fr3.resolve(asm);

    // An exception is pending, deliver the exception to the caller as if executing an athrow in the caller
    // at the location of the call to the native method
    asm.emitLAddrToc(T3, VM_Entrypoints.athrowMethod.getOffset());
    asm.emitMTCTR(T3);                         // point LR to the exception delivery code
    asm.emitMR (T0, T2);                       // copy the saved exception to T0
    asm.emitBCCTR();                           // then branch to the exception delivery code, does not return

    VM_MachineCode machineCode = asm.makeMachineCode();
    cm.compileComplete(machineCode.getInstructions());
    return cm;
  } 

  //-#if RVM_FOR_LINUX
  // Maps arguments from RVM to SVR4 ABI convention
  /* PRE conditions:
   *   r3 - r13 parameters from caller
   *   f1 - f15 parameters from caller
   *  
   *  spills saved in callers's stack frame
   */
  static void storeParametersForLinux(VM_Assembler asm,
				      int frameSize,
				      VM_Method method,
				      VM_Class klass) {

    int nextAIXArgReg, nextAIXArgFloatReg, nextVMArgReg, nextVMArgFloatReg; 
    
    // offset to the spill area in the callee (Linux frame)
	// remove 
    int spillOffsetAIX = NATIVE_FRAME_HEADER_SIZE; 

    // offset to the spill area in the caller (RVM frame), relative to
    // the callee's FP
    int spillOffsetVM = frameSize + STACKFRAME_HEADER_SIZE;

    // does NOT include implicit this or class ptr
    VM_TypeReference[] types = method.getParameterTypes();
 
    // number of arguments for this method
    int numArguments = types.length; 
    
    // Set up the Reference table for GC
    // PR <- JREFS array base
    asm.emitLAddr(PROCESSOR_REGISTER, VM_Entrypoints.JNIRefsField.getOffset(), S0);

    // TI <- JREFS current top 
    asm.emitLInt(TI, VM_Entrypoints.JNIRefsTopField.getOffset(), S0);   // JREFS offset for current TOP 
    asm.emitADD(TI, PROCESSOR_REGISTER, TI);                // convert into address

    // TODO - count number of refs
    // TODO - emit overflow check for JNIRefs array
    
    // start a new JNIRefs frame on each transition from Java to native C
    // push current SavedFP ptr onto top of JNIRefs stack (use available S1 reg as a temp)
    // and make current TOP the new savedFP
    //
    asm.emitLWZ  (S1, VM_Entrypoints.JNIRefsSavedFPField.getOffset(), S0);
    asm.emitSTWU (S1, 4, TI);                           // push prev frame ptr onto JNIRefs array	
    asm.emitSUBFC(S1, PROCESSOR_REGISTER, TI);           // compute offset for new TOP
    asm.emitSTW  (S1, VM_Entrypoints.JNIRefsSavedFPField.getOffset(), S0);  // save new TOP as new frame ptr in JNIEnv


    // for static methods: caller has placed args in r3,r4,... 
    // for non-static methods:"this" ptr is in r3, and args start in r4,r5,...
    // 
    // for static methods:                for nonstatic methods:       
    //  Java caller     AIX callee         Java caller     AIX callee    
    //  -----------     ----------	    -----------     ----------  
    //  spill = arg11 -> new spill	    spill = arg11 -> new spill  
    //  spill = arg10 -> new spill	    spill = arg10 -> new spill  
    // 				            spill = arg9  -> new spill  
    //    R12 = arg9  -> new spill	                                
    //    R11 = arg8  -> new spill	      R12 = arg8  -> new spill  
    //    R10 = arg7  -> new spill	      R11 = arg7  -> new spill  
    //    R9  = arg6  -> new spill	      R10 = arg6  -> new spill  
    // 								   
    //    R8  = arg5  -> R10                  R9  = arg5  -> R10         
    //    R7  = arg4  -> R9		      R8  = arg4  -> R9          
    //    R6  = arg3  -> R8		      R7  = arg3  -> R8          
    //    R5  = arg2  -> R7		      R6  = arg2  -> R7          
    //    R4  = arg1  -> R6		      R5  = arg1  -> R6          
    //    R3  = arg0  -> R5		      R4  = arg0  -> R5          
    //                   R4 = class           R3  = this  -> R4         
    // 	                 R3 = JNIenv                         R3 = JNIenv
    //
    // if the number of args in GPR does not exceed R11, then we can use R12 as scratch 
    //   to move the args
    // if the number of args in GPR exceed R12, then we need to save R12 first to make 
    //   room for a scratch register
    // if the number of args in FPR does not exceed F12, then we can use F13 as scratch

    nextAIXArgFloatReg = FIRST_OS_PARAMETER_FPR;
    nextVMArgFloatReg  = FIRST_VOLATILE_FPR;
    nextAIXArgReg      = FIRST_OS_PARAMETER_GPR + 2;   // 1st reg = JNIEnv, 2nd reg = class
    if ( method.isStatic() ) {
      nextVMArgReg = FIRST_VOLATILE_GPR;              
    } else {
      nextVMArgReg = FIRST_VOLATILE_GPR+1;            // 1st reg = this, to be processed separately
    }

    // The loop below assumes the following relationship:
    if (VM.VerifyAssertions) VM._assert(FIRST_OS_PARAMETER_FPR==FIRST_VOLATILE_FPR);
    if (VM.VerifyAssertions) VM._assert(LAST_OS_PARAMETER_FPR<=LAST_VOLATILE_FPR);
    if (VM.VerifyAssertions) VM._assert(FIRST_OS_PARAMETER_GPR==FIRST_VOLATILE_GPR);
    if (VM.VerifyAssertions) VM._assert(LAST_OS_PARAMETER_GPR<=LAST_VOLATILE_GPR);


    // create one VM_Assembler object for each argument
    // This is needed for the following reason:
    //   -2 new arguments are added in front for native methods, so the normal arguments
    //    need to be shifted down in addition to being moved
    //   -to avoid overwriting each other, the arguments must be copied in reverse order
    //   -the analysis for mapping however must be done in forward order
    //   -the moving/mapping for each argument may involve a sequence of 1-3 instructions 
    //    which must be kept in the normal order
    // To solve this problem, the instructions for each argument is generated in its
    // own VM_Assembler in the forward pass, then in the reverse pass, each VM_Assembler
    // emist the instruction sequence and copies it into the main VM_Assembler
    VM_Assembler[] asmForArgs = new VM_Assembler[numArguments];

    for (int arg = 0; arg < numArguments; arg++) {
      
      asmForArgs[arg] = new VM_Assembler(0);
      VM_Assembler asmArg = asmForArgs[arg];

      // For 32-bit float arguments, must be converted to
      // double 
      //
      if (types[arg].isFloatType() || types[arg].isDoubleType()) {
	boolean is32bits = types[arg].isFloatType();
	
       	// 1. check the source, the value will be in srcVMArg
	int srcVMArg; // scratch fpr
	if (nextVMArgFloatReg <= LAST_VOLATILE_FPR) {
	  srcVMArg = nextVMArgFloatReg;
	  nextVMArgFloatReg ++;
	} else {
	  srcVMArg = FIRST_SCRATCH_FPR;
	  // VM float reg is in spill area
	  if (is32bits) {
	    asmArg.emitLFS(srcVMArg, spillOffsetVM, FP);
	    spillOffsetVM += 4;
	  } else {
	    asmArg.emitLFD(srcVMArg, spillOffsetVM, FP);
	    spillOffsetVM += 8;
	  }
	}  
		
	// 2. check the destination, 
	if (nextAIXArgFloatReg <= LAST_OS_PARAMETER_FPR) {
	  // leave it there
	  nextAIXArgFloatReg ++;
	} else {
	  // spill it, round the spill address to 8
	  // assuming FP is aligned to 8
	  spillOffsetAIX = (spillOffsetAIX + 7) & -8;	  
	  asmArg.emitSTFD(srcVMArg, spillOffsetAIX, FP);
	  spillOffsetAIX += 8; }
	// for 64-bit long arguments
      } else if (types[arg].isLongType()) {
	// handle AIX first
	boolean dstSpilling;
	int regOrSpilling = -1;  // it is register number or spilling offset
	// 1. check if Linux register > 9
	if (nextAIXArgReg > (LAST_OS_PARAMETER_GPR - 1)) {
	  // goes to spilling area
	  dstSpilling = true;
	  
	  // NOTE: following adjustment is not stated in SVR4 ABI, but 
	  // implemented in GCC
	  // -- Feng
	  nextAIXArgReg = LAST_OS_PARAMETER_GPR + 1;
	  
	  // compute spilling offset
	  spillOffsetAIX = (spillOffsetAIX + 7) & -8;
	  regOrSpilling = spillOffsetAIX;
	  spillOffsetAIX += 8;
	} else {
	  // use registers
	  dstSpilling = false;
	  // rounds to odd
	  nextAIXArgReg += (nextAIXArgReg + 1) & 0x01; // if gpr is even, gpr += 1
	  regOrSpilling = nextAIXArgReg;
	  nextAIXArgReg += 2;
	}
	
	// handle RVM source
	if (nextVMArgReg < LAST_VOLATILE_GPR) {
	  // both parts in registers
	  if (dstSpilling) {
	    asmArg.emitSTW(nextVMArgReg+1, regOrSpilling+4, FP);
	    asmArg.emitSTW(nextVMArgReg, regOrSpilling, FP);
	  } else {
	    asmArg.emitMR(regOrSpilling+1, nextVMArgReg+1);
	    asmArg.emitMR(regOrSpilling, nextVMArgReg);
	  }
	  // advance register counting, Linux register number
	  // already advanced 
	  nextVMArgReg += 2;
	} else if (nextVMArgReg == LAST_VOLATILE_GPR) {
	  // VM striding
	  if (dstSpilling) {
	    asmArg.emitLWZ(0, spillOffsetVM, FP);
	    asmArg.emitSTW(0, regOrSpilling+4, FP);
	    asmArg.emitSTW(nextVMArgReg, regOrSpilling, FP);
	  } else {
	    asmArg.emitLWZ(regOrSpilling + 1, spillOffsetVM, FP);
	    asmArg.emitMR(regOrSpilling, nextVMArgReg);
	  }
	  // advance spillOffsetVM and nextVMArgReg
	  nextVMArgReg ++;
	  spillOffsetVM += 4;
	} else if (nextVMArgReg > LAST_VOLATILE_GPR) {
	  if (dstSpilling) {
	    asmArg.emitLFD(FIRST_SCRATCH_FPR, spillOffsetVM, FP);
	    asmArg.emitSTFD(FIRST_SCRATCH_FPR, regOrSpilling, FP);
	  } else {
	    // this shouldnot happen, VM spills, AIX has registers
	    asmArg.emitLWZ(regOrSpilling + 1, spillOffsetVM+4, FP);
	    asmArg.emitLWZ(regOrSpilling, spillOffsetVM, FP);
	  }	
	  spillOffsetVM += 8;
	}
      } else if (types[arg].isReferenceType() ) {	
	// For reference type, replace with handlers before passing to AIX
	int srcreg, dstreg;
	if (nextVMArgReg <= LAST_VOLATILE_GPR) {
	  srcreg = nextVMArgReg++;
	} else {
	  srcreg = 0;
	  asmArg.emitLWZ(srcreg, spillOffsetVM, FP);
	  spillOffsetVM += 4;
	}
	asmArg.emitSTWU(srcreg, 4, TI);
	
	if (nextAIXArgReg <= LAST_OS_PARAMETER_GPR) {
	  asmArg.emitSUBFC(nextAIXArgReg++, PROCESSOR_REGISTER, TI);
	} else {
	  asmArg.emitSUBFC(0, PROCESSOR_REGISTER, TI);
	  asmArg.emitSTW(0, spillOffsetAIX, FP);
	  spillOffsetAIX += 4;
	}
      } else {
	// For all other types: int, short, char, byte, boolean
	// (1a) fit in AIX register, move the register
	if (nextAIXArgReg<=LAST_OS_PARAMETER_GPR) {
	  asmArg.emitMR(nextAIXArgReg++, nextVMArgReg++);
	}
	// (1b) spill AIX register, but still fit in VM register
	else if (nextVMArgReg<=LAST_VOLATILE_GPR) {
	  asmArg.emitSTW(nextVMArgReg++, spillOffsetAIX, FP);
	  spillOffsetAIX+=4;
	} else {
	  // (1c) spill VM register
	  asmArg.emitLWZ(0,spillOffsetVM, FP);        // retrieve arg from VM spill area
	  asmArg.emitSTW(0, spillOffsetAIX, FP);
	  spillOffsetVM+=4;
	  spillOffsetAIX+=4;
	}
      }
    }
    
    // Append the code sequences for parameter mapping 
    // to the current machine code in reverse order
    // so that the move does not overwrite the parameters
    for (int arg = numArguments-1; arg >= 0; arg--) {
      VM_MachineCode codeForArg = asmForArgs[arg].makeMachineCode();
      asm.appendInstructions(codeForArg.getInstructions());
    }

    // Now add the 2 new JNI parameters:  JNI environment and Class or "this" object
    
    // if static method, append ref for class, else append ref for "this"
    // and pass offset in JNIRefs array in r4 (as second arg to called native code)
    int secondAIXVolatileReg = FIRST_OS_PARAMETER_GPR + 1;
	if ( method.isStatic() ) {
      klass.getClassForType();     // ensure the Java class object is created
      // JTOC saved above in JNIEnv is still valid, used by following emitLWZtoc
      asm.emitLAddrToc(secondAIXVolatileReg, klass.getTibOffset() ); // r4 <- class TIB ptr from jtoc
      asm.emitLWZ  (secondAIXVolatileReg, 0, secondAIXVolatileReg);                  // r4 <- first TIB entry == -> class object
      asm.emitLWZ  (secondAIXVolatileReg, VM_Entrypoints.classForTypeField.getOffset(), secondAIXVolatileReg); // r4 <- java Class for this VM_Class
      asm.emitSTWU (secondAIXVolatileReg, 4, TI );                 // append class ptr to end of JNIRefs array
      asm.emitSUBFC  (secondAIXVolatileReg, PROCESSOR_REGISTER, TI );  // pass offset in bytes
    } else {
      asm.emitSTWU (FIRST_OS_PARAMETER_GPR, 4, TI );                 // append this ptr to end of JNIRefs array
      asm.emitSUBFC  (secondAIXVolatileReg, PROCESSOR_REGISTER, TI );  // pass offset in bytes
    }
    
    // store the new JNIRefs array TOP back into JNIEnv	
    asm.emitSUBFC(TI, PROCESSOR_REGISTER, TI );     // compute offset for the current TOP
    asm.emitSTW(TI, VM_Entrypoints.JNIRefsTopField.getOffset(), S0);
  }
  //-#endif
   

  //-#if RVM_FOR_OSX
  // Maps arguments from RVM to SVR4 ABI convention
  /* PRE conditions:
   *   r3 - r13 parameters from caller
   *   f1 - f15 parameters from caller
   *  
   *  spills saved in callers's stack frame
   */
  static void storeParametersForOSX(VM_Assembler asm,
				      int frameSize,
				      VM_Method method,
                                    VM_Class klass) {

    int nextOSXArgReg, nextOSXArgFloatReg, nextVMArgReg, nextVMArgFloatReg; 
    
    // offset to the spill area in the callee (Osx frame)
    // remove 
    int spillOffsetOSX = NATIVE_FRAME_HEADER_SIZE;
    spillOffsetOSX += 8;        // 1st spill = JNIEnv, 2nd spill = class

    // offset to the spill area in the caller (RVM frame), relative to
    // the callee's FP
    int spillOffsetVM = frameSize + STACKFRAME_HEADER_SIZE;

    // does NOT include implicit this or class ptr
    VM_TypeReference[] types = method.getParameterTypes();
 
    // number of arguments for this method
    int numArguments = types.length; 
    
    // Set up the Reference table for GC
    // PR <- JREFS array base
    asm.emitLAddr(PROCESSOR_REGISTER, VM_Entrypoints.JNIRefsField.getOffset(), S0);

    // TI <- JREFS current top 
    asm.emitLWZ(TI, VM_Entrypoints.JNIRefsTopField.getOffset(), S0);   // JREFS offset for current TOP 
    asm.emitADD(TI, PROCESSOR_REGISTER, TI);                // convert into address

    // TODO - count number of refs
    // TODO - emit overflow check for JNIRefs array
    
    // start a new JNIRefs frame on each transition from Java to native C
    // push current SavedFP ptr onto top of JNIRefs stack (use available S1 reg as a temp)
    // and make current TOP the new savedFP
    //
    asm.emitLWZ  (S1, VM_Entrypoints.JNIRefsSavedFPField.getOffset(), S0);
    asm.emitSTWU (S1, 4, TI);                           // push prev frame ptr onto JNIRefs array	
    asm.emitSUBFC(S1, PROCESSOR_REGISTER, TI);           // compute offset for new TOP
    asm.emitSTW  (S1, VM_Entrypoints.JNIRefsSavedFPField.getOffset(), S0);  // save new TOP as new frame ptr in JNIEnv


    // for static methods: caller has placed args in r3,r4,... 
    // for non-static methods:"this" ptr is in r3, and args start in r4,r5,...
    // 
    // for static methods:                for nonstatic methods:       
    //  Java caller     OSX callee         Java caller     OSX callee    
    //  -----------     ----------	    -----------     ----------  
    //  spill = arg11 -> new spill	    spill = arg11 -> new spill  
    //  spill = arg10 -> new spill	    spill = arg10 -> new spill  
    // 				            spill = arg9  -> new spill  
    //    R12 = arg9  -> new spill	                                
    //    R11 = arg8  -> new spill	      R12 = arg8  -> new spill  
    //    R10 = arg7  -> new spill	      R11 = arg7  -> new spill  
    //    R9  = arg6  -> new spill	      R10 = arg6  -> new spill  
    // 								   
    //    R8  = arg5  -> R10                  R9  = arg5  -> R10         
    //    R7  = arg4  -> R9		      R8  = arg4  -> R9          
    //    R6  = arg3  -> R8		      R7  = arg3  -> R8          
    //    R5  = arg2  -> R7		      R6  = arg2  -> R7          
    //    R4  = arg1  -> R6		      R5  = arg1  -> R6          
    //    R3  = arg0  -> R5		      R4  = arg0  -> R5          
    //                   R4 = class           R3  = this  -> R4         
    // 	                 R3 = JNIenv                         R3 = JNIenv
    //
    // if the number of args in GPR does not exceed R11, then we can use R12 as scratch 
    //   to move the args
    // if the number of args in GPR exceed R12, then we need to save R12 first to make 
    //   room for a scratch register
    // if the number of args in FPR does not exceed F12, then we can use F13 as scratch

    nextOSXArgFloatReg = FIRST_OS_PARAMETER_FPR;
    nextVMArgFloatReg  = FIRST_VOLATILE_FPR;
    nextOSXArgReg      = FIRST_OS_PARAMETER_GPR + 2;   // 1st reg = JNIEnv, 2nd reg = class
    if ( method.isStatic() ) {
      nextVMArgReg = FIRST_VOLATILE_GPR;              
    } else {
      nextVMArgReg = FIRST_VOLATILE_GPR+1;            // 1st reg = this, to be processed separately
    }

    // The loop below assumes the following relationship:
    if (VM.VerifyAssertions) VM._assert(FIRST_OS_PARAMETER_FPR==FIRST_VOLATILE_FPR);
    if (VM.VerifyAssertions) VM._assert(LAST_OS_PARAMETER_FPR<=LAST_VOLATILE_FPR);
    if (VM.VerifyAssertions) VM._assert(FIRST_OS_PARAMETER_GPR==FIRST_VOLATILE_GPR);
    if (VM.VerifyAssertions) VM._assert(LAST_OS_PARAMETER_GPR<=LAST_VOLATILE_GPR);


    // create one VM_Assembler object for each argument
    // This is needed for the following reason:
    //   -2 new arguments are added in front for native methods, so the normal arguments
    //    need to be shifted down in addition to being moved
    //   -to avoid overwriting each other, the arguments must be copied in reverse order
    //   -the analysis for mapping however must be done in forward order
    //   -the moving/mapping for each argument may involve a sequence of 1-3 instructions 
    //    which must be kept in the normal order
    // To solve this problem, the instructions for each argument is generated in its
    // own VM_Assembler in the forward pass, then in the reverse pass, each VM_Assembler
    // emist the instruction sequence and copies it into the main VM_Assembler
    VM_Assembler[] asmForArgs = new VM_Assembler[numArguments];

    for (int arg = 0; arg < numArguments; arg++) {
      int spillSizeOSX = 0;
      int nextOsxGprIncrement = 1;
      
      asmForArgs[arg] = new VM_Assembler(0);
      VM_Assembler asmArg = asmForArgs[arg];

      if (types[arg].isFloatType() || types[arg].isDoubleType()) {
        boolean is32bits = types[arg].isFloatType();

        if (is32bits)
          spillSizeOSX = 4;
        else {
          spillSizeOSX = 8;
          nextOsxGprIncrement = 2;
        }
	
       	// 1. check the source, the value will be in srcVMArg
        int srcVMArg; // scratch fpr
        if (nextVMArgFloatReg <= LAST_VOLATILE_FPR) {
          srcVMArg = nextVMArgFloatReg;
          nextVMArgFloatReg ++;
        } else {
          srcVMArg = FIRST_SCRATCH_FPR;
          // VM float reg is in spill area
          if (is32bits) {
            asmArg.emitLFS(srcVMArg, spillOffsetVM, FP);
            spillOffsetVM += 4;
          } else {
            asmArg.emitLFD(srcVMArg, spillOffsetVM, FP);
            spillOffsetVM += 8;
          }
        }  
		
        // 2. check the destination, 
        if (nextOSXArgFloatReg <= LAST_OS_PARAMETER_FPR) {
          // leave it there
          nextOSXArgFloatReg ++;
        } else {
          if (is32bits) {
            asmArg.emitSTFS(srcVMArg, spillOffsetOSX, FP);
          } else {
            asmArg.emitSTFD(srcVMArg, spillOffsetOSX, FP);
          }
        }
        // for 64-bit long arguments
      } else if (types[arg].isLongType()) {
        spillSizeOSX = 8;
        nextOsxGprIncrement = 2;
        
//         copyWord(asmArg,
//                  nextVMArgReg+1,  spillOffsetVM+4,
//                  nextOSXArgReg+1, spillOffsetOSX+4);

//         copyWord(asmArg,
//                  nextVMArgReg,  spillOffsetVM,
//                  nextOSXArgReg, spillOffsetOSX);
        
//         if (nextVMArgReg == LAST_VOLATILE_GPR)
//           spillOffsetVM += 4;
//         else if (nextVMArgReg > LAST_VOLATILE_GPR)
//           spillOffsetVM += 8;
//         nextVMArgReg  += 2;
        
        // handle OSX first
        boolean dstSpilling;
        int regOrSpilling = -1;  // it is register number or spilling offset
        // 1. check if Osx register > 9
        if (nextOSXArgReg > (LAST_OS_PARAMETER_GPR - 1)) {
          // goes to spilling area
          dstSpilling = true;
	  
//           // NOTE: following adjustment is not stated in SVR4 ABI, but 
//           // implemented in GCC
//           // -- Feng
//           nextOSXArgReg = LAST_OS_PARAMETER_GPR + 1;
	  
          // compute spilling offset
          regOrSpilling = spillOffsetOSX;
        } else {
          // use registers
          dstSpilling = false;
          regOrSpilling = nextOSXArgReg;
          //nextOSXArgReg += 2;
        }
	
        // handle RVM source
        if (nextVMArgReg < LAST_VOLATILE_GPR) {
          // both parts in registers
          if (dstSpilling) {
            asmArg.emitSTW(nextVMArgReg+1, regOrSpilling+4, FP);
            if (nextOSXArgReg == LAST_OS_PARAMETER_GPR) {
              asmArg.emitADDIS(nextOSXArgReg, nextVMArgReg, 0);
            } else {
              asmArg.emitSTW(nextVMArgReg, regOrSpilling, FP);
            }
          } else {
            asmArg.emitADDIS(regOrSpilling+1, nextVMArgReg+1, 0);
            asmArg.emitADDIS(regOrSpilling,   nextVMArgReg, 0);
          }
          // advance register counting, Osx register number
          // already advanced 
          nextVMArgReg += 2;
        } else if (nextVMArgReg == LAST_VOLATILE_GPR) {
          // VM striding
          if (dstSpilling) {
            asmArg.emitLWZ(0, spillOffsetVM, FP);
            asmArg.emitSTW(0, regOrSpilling+4, FP);
            asmArg.emitSTW(nextVMArgReg, regOrSpilling, FP);
          } else {
            asmArg.emitLWZ(regOrSpilling + 1, spillOffsetVM, FP);
            asmArg.emitADDIS(regOrSpilling, nextVMArgReg, 0);
          }
          // advance spillOffsetVM and nextVMArgReg
          nextVMArgReg ++;
          spillOffsetVM += 4;
        } else if (nextVMArgReg > LAST_VOLATILE_GPR) {
          if (dstSpilling) {
             asmArg.emitLFD(FIRST_SCRATCH_FPR, spillOffsetVM, FP);
             asmArg.emitSTFD(FIRST_SCRATCH_FPR, regOrSpilling, FP);
          } else {
            // this shouldnot happen, VM spills, OSX has registers
            asmArg.emitLWZ(regOrSpilling + 1, spillOffsetVM+4, FP);
            asmArg.emitLWZ(regOrSpilling, spillOffsetVM, FP);
          }
          spillOffsetVM += 8;

        }
      } else if (types[arg].isReferenceType() ) {
        spillSizeOSX = 4;
        // For reference type, replace with handlers before passing to OSX
        int srcreg, dstreg;
        if (nextVMArgReg <= LAST_VOLATILE_GPR) {
          srcreg = nextVMArgReg++;
        } else {
          srcreg = 0;
          asmArg.emitLWZ(srcreg, spillOffsetVM, FP);
          spillOffsetVM += 4;
        }
        asmArg.emitSTWU(srcreg, 4, TI);
	
        if (nextOSXArgReg <= LAST_OS_PARAMETER_GPR) {
          asmArg.emitSUBFC(nextOSXArgReg, PROCESSOR_REGISTER, TI);
        } else {
          asmArg.emitSUBFC(0, PROCESSOR_REGISTER, TI);
          asmArg.emitSTW(0, spillOffsetOSX, FP);
        }
      } else {
        spillSizeOSX = 4;
        // For all other types: int, short, char, byte, boolean
        // (1a) fit in OSX register, move the register
        if (nextOSXArgReg<=LAST_OS_PARAMETER_GPR) {
          asmArg.emitADDIS(nextOSXArgReg, nextVMArgReg++, 0);
        }
        // (1b) spill OSX register, but still fit in VM register
        else if (nextVMArgReg<=LAST_VOLATILE_GPR) {
          asmArg.emitSTW(nextVMArgReg++, spillOffsetOSX, FP);
        } else {
          // (1c) spill VM register
          asmArg.emitLWZ(0,spillOffsetVM, FP);        // retrieve arg from VM spill area
          asmArg.emitSTW(0, spillOffsetOSX, FP);
          spillOffsetVM+=4;
        }
      }
      spillOffsetOSX += spillSizeOSX;
      nextOSXArgReg += nextOsxGprIncrement;
    }
    
    // Append the code sequences for parameter mapping 
    // to the current machine code in reverse order
    // so that the move does not overwrite the parameters
    for (int arg = numArguments-1; arg >= 0; arg--) {
      VM_MachineCode codeForArg = asmForArgs[arg].makeMachineCode();
      asm.appendInstructions(codeForArg.getInstructions());
    }

    // Now add the 2 new JNI parameters:  JNI environment and Class or "this" object
    
    // if static method, append ref for class, else append ref for "this"
    // and pass offset in JNIRefs array in r4 (as second arg to called native code)
    int secondOSXVolatileReg = FIRST_OS_PARAMETER_GPR + 1;
    if ( method.isStatic() ) {
      klass.getClassForType();     // ensure the Java class object is created
      // JTOC saved above in JNIEnv is still valid, used by following emitLWZtoc
      asm.emitLAddrToc(secondOSXVolatileReg, klass.getTibOffset() ); // r4 <- class TIB ptr from jtoc
      asm.emitLWZ  (secondOSXVolatileReg, 0, secondOSXVolatileReg);                  // r4 <- first TIB entry == -> class object
      asm.emitLWZ  (secondOSXVolatileReg, VM_Entrypoints.classForTypeField.getOffset(), secondOSXVolatileReg); // r4 <- java Class for this VM_Class
      asm.emitSTWU (secondOSXVolatileReg, 4, TI );                 // append class ptr to end of JNIRefs array
      asm.emitSUBFC  (secondOSXVolatileReg, PROCESSOR_REGISTER, TI );  // pass offset in bytes
    } else {
      asm.emitSTWU (FIRST_OS_PARAMETER_GPR, 4, TI );                 // append this ptr to end of JNIRefs array
      asm.emitSUBFC  (secondOSXVolatileReg, PROCESSOR_REGISTER, TI );  // pass offset in bytes
    }
    
    // store the new JNIRefs array TOP back into JNIEnv	
    asm.emitSUBFC(TI, PROCESSOR_REGISTER, TI );     // compute offset for the current TOP
    asm.emitSTW(TI, VM_Entrypoints.JNIRefsTopField.getOffset(), S0);
  }


  private static void copyWord(VM_Assembler asm,
                               int nextVMReg, int nextVMOffset,
                               int nextOSXReg, int nextOSXOffset) {
    
    if (nextVMReg <= LAST_VOLATILE_GPR) {
      if (nextOSXReg <= LAST_OS_PARAMETER_GPR) {
        // both are in registers
        if (nextVMReg != nextOSXReg) {
          asm.emitMR(nextOSXReg, nextVMReg);
        }
      } else {
        // VM Reg -> OSX spill
        asm.emitSTW(nextVMReg, nextOSXOffset, FP);
      }
    } else {
      if (nextOSXReg <= LAST_OS_PARAMETER_GPR) {
        // VM spill -> OSX reg
        // this should not happen, VM spills, OSX has registers
        asm.emitLWZ(nextOSXReg, nextVMOffset, FP);
      } else {
        // VM spill -> OSX spill
        asm.emitLWZ(FIRST_SCRATCH_GPR, nextVMOffset, FP);
        asm.emitSTW(FIRST_SCRATCH_GPR, nextOSXOffset, FP);
      }
      
    }
  }
  //-#endif
   
  //-#if RVM_FOR_AIX
  // Map the arguments from RVM convention to AIX convention,
  // and replace all references with indexes into JNIRefs array.
  // Assumption on entry:
  // -TI, PROCESSOR_REGISTER and S1 are available for use as scratch register
  // -the frame has been created, FP points to the new callee frame
  // Also update the JNIRefs arra
  private static void storeParametersForAIX(VM_Assembler asm, 
					    int frameSize, 
					    VM_Method method, VM_Class klass) {

    int nextAIXArgReg, nextAIXArgFloatReg, nextVMArgReg, nextVMArgFloatReg; 
    
    // offset to the spill area in the callee (AIX frame):
    // skip past the 2 arguments to be added in front:  JNIenv and class or object pointer
    int spillOffsetAIX = NATIVE_FRAME_HEADER_SIZE + 2*BYTES_IN_STACKSLOT;

    // offset to the spill area in the caller (RVM frame), relative to the callee's FP
    int spillOffsetVM = frameSize + STACKFRAME_HEADER_SIZE;

    VM_TypeReference[] types = method.getParameterTypes();   // does NOT include implicit this or class ptr
    int numArguments = types.length;                // number of arguments for this method
    
    // Set up the Reference table for GC
    // PR <- JREFS array base
    asm.emitLAddr(PROCESSOR_REGISTER, VM_Entrypoints.JNIRefsField.getOffset(), S0);
    // TI <- JREFS current top 
    asm.emitLInt(TI, VM_Entrypoints.JNIRefsTopField.getOffset(), S0);   // JREFS offset for current TOP 
    asm.emitADD(TI, PROCESSOR_REGISTER, TI);                // convert into address

    // TODO - count number of refs
    // TODO - emit overflow check for JNIRefs array
    
    // start a new JNIRefs frame on each transition from Java to native C
    // push current SavedFP ptr onto top of JNIRefs stack (use available S1 reg as a temp)
    // and make current TOP the new savedFP
    //
    asm.emitLInt  (S1, VM_Entrypoints.JNIRefsSavedFPField.getOffset(), S0);
    asm.emitSTWU (S1, BYTES_IN_ADDRESS, TI);                           // push prev frame ptr onto JNIRefs array	
    asm.emitSUBFC(S1, PROCESSOR_REGISTER, TI);          // compute offset for new TOP
    asm.emitSTW  (S1, VM_Entrypoints.JNIRefsSavedFPField.getOffset(), S0);  // save new TOP as new frame ptr in JNIEnv


    // for static methods: caller has placed args in r3,r4,... 
    // for non-static methods:"this" ptr is in r3, and args start in r4,r5,...
    // 
    // for static methods:                for nonstatic methods:       
    //  Java caller     AIX callee         Java caller     AIX callee    
    //  -----------     ----------	    -----------     ----------  
    //  spill = arg11 -> new spill	    spill = arg11 -> new spill  
    //  spill = arg10 -> new spill	    spill = arg10 -> new spill  
    // 				            spill = arg9  -> new spill  
    //    R12 = arg9  -> new spill	                                
    //    R11 = arg8  -> new spill	      R12 = arg8  -> new spill  
    //    R10 = arg7  -> new spill	      R11 = arg7  -> new spill  
    //    R9  = arg6  -> new spill	      R10 = arg6  -> new spill  
    // 								   
    //    R8  = arg5  -> R10                  R9  = arg5  -> R10         
    //    R7  = arg4  -> R9		      R8  = arg4  -> R9          
    //    R6  = arg3  -> R8		      R7  = arg3  -> R8          
    //    R5  = arg2  -> R7		      R6  = arg2  -> R7          
    //    R4  = arg1  -> R6		      R5  = arg1  -> R6          
    //    R3  = arg0  -> R5		      R4  = arg0  -> R5          
    //                   R4 = class           R3  = this  -> R4         
    // 	                 R3 = JNIenv                         R3 = JNIenv
    //
    // if the number of args in GPR does not exceed R11, then we can use R12 as scratch 
    //   to move the args
    // if the number of args in GPR exceed R12, then we need to save R12 first to make 
    //   room for a scratch register
    // if the number of args in FPR does not exceed F12, then we can use F13 as scratch

    nextAIXArgFloatReg = FIRST_OS_PARAMETER_FPR;
    nextVMArgFloatReg = FIRST_VOLATILE_FPR;
    nextAIXArgReg      = FIRST_OS_PARAMETER_GPR + 2;   // 1st reg = JNIEnv, 2nd reg = class
    if ( method.isStatic() ) {
      nextVMArgReg = FIRST_VOLATILE_GPR;              
    } else {
      nextVMArgReg = FIRST_VOLATILE_GPR+1;            // 1st reg = this, to be processed separately
    }

    // The loop below assumes the following relationship:
    if (VM.VerifyAssertions) VM._assert(FIRST_OS_PARAMETER_FPR==FIRST_VOLATILE_FPR);
    if (VM.VerifyAssertions) VM._assert(LAST_OS_PARAMETER_FPR<=LAST_VOLATILE_FPR);
    if (VM.VerifyAssertions) VM._assert(FIRST_OS_PARAMETER_GPR==FIRST_VOLATILE_GPR);
    if (VM.VerifyAssertions) VM._assert(LAST_OS_PARAMETER_GPR<=LAST_VOLATILE_GPR);


    // create one VM_Assembler object for each argument
    // This is needed for the following reason:
    //   -2 new arguments are added in front for native methods, so the normal arguments
    //    need to be shifted down in addition to being moved
    //   -to avoid overwriting each other, the arguments must be copied in reverse order
    //   -the analysis for mapping however must be done in forward order
    //   -the moving/mapping for each argument may involve a sequence of 1-3 instructions 
    //    which must be kept in the normal order
    // To solve this problem, the instructions for each argument is generated in its
    // own VM_Assembler in the forward pass, then in the reverse pass, each VM_Assembler
    // emist the instruction sequence and copies it into the main VM_Assembler
    VM_Assembler[] asmForArgs = new VM_Assembler[numArguments];

    for (int arg = 0; arg < numArguments; arg++) {
      
      boolean mustSaveFloatToSpill;
      asmForArgs[arg] = new VM_Assembler(0);
      VM_Assembler asmArg = asmForArgs[arg];

      // For 32-bit float arguments
      //
      if (types[arg].isFloatType()) {
	// Side effect of float arguments on the GPR's
	// (1a) reserve one GPR for each float if it is available
	if (nextAIXArgReg<=LAST_OS_PARAMETER_GPR) {
	  nextAIXArgReg++;
	  mustSaveFloatToSpill = false;
	} else {
	  // (1b) if GPR has spilled, store the float argument in the callee spill area
	  // regardless of whether the FPR has spilled or not
	  mustSaveFloatToSpill = true;
	}

	// Check if the args need to be moved
	// (2a) leave those in FPR[1:13] as is unless the GPR has spilled
	if (nextVMArgFloatReg<=LAST_OS_PARAMETER_FPR) {
	  if (mustSaveFloatToSpill) {
	    asmArg.emitSTFS(nextVMArgFloatReg, spillOffsetAIX, FP); 
	  }
	  spillOffsetAIX+=BYTES_IN_STACKSLOT;
	  nextAIXArgFloatReg++;
	  nextVMArgFloatReg++;  
	} else if (nextVMArgFloatReg<=LAST_VOLATILE_FPR) {
	  // (2b) run out of FPR in AIX, but still have 2 more FPR in VM,
	  // so FPR[14:15] goes to the callee spill area
	  asmArg.emitSTFS(nextVMArgFloatReg, spillOffsetAIX, FP);
	  nextVMArgFloatReg++;
	  spillOffsetAIX+=BYTES_IN_STACKSLOT;
	} else {
	  // (2c) run out of FPR in VM, now get the remaining args from the caller spill area 
	  // and move them into the callee spill area
	  spillOffsetVM+=BYTES_IN_STACKSLOT;
	  asmArg.emitLFS(0, spillOffsetVM - BYTES_IN_FLOAT, FP); //Kris Venstermans: Attention, different calling convention !!
	  asmArg.emitSTFS(0, spillOffsetAIX, FP);
	  spillOffsetAIX+=BYTES_IN_STACKSLOT;
	}
      } else if (types[arg].isDoubleType()) {
	// For 64-bit float arguments 
        if (VM.BuildFor64Addr) {
	  // Side effect of float arguments on the GPR's
	  // (1a) reserve one GPR for double
	  if (nextAIXArgReg<=LAST_OS_PARAMETER_GPR) {
	    nextAIXArgReg++;
	    mustSaveFloatToSpill = false;
	  } else {
	    // (1b) if GPR has spilled, store the float argument in the callee spill area
	    // regardless of whether the FPR has spilled or not
	    mustSaveFloatToSpill = true;
	  }

	} else {
	  // Side effect of float arguments on the GPR's
	  // (1a) reserve two GPR's for double
	  if (nextAIXArgReg<=LAST_OS_PARAMETER_GPR-1) {
	    nextAIXArgReg+=2;
	    mustSaveFloatToSpill = false;
	  } else {
	    // if only one GPR is left, reserve it anyway although it won't be used
	    if (nextAIXArgReg<=LAST_OS_PARAMETER_GPR)
	      nextAIXArgReg++;
	    mustSaveFloatToSpill = true;
	  }
	}

	// Check if the args need to be moved
	// (2a) leave those in FPR[1:13] as is unless the GPR has spilled
	if (nextVMArgFloatReg<=LAST_OS_PARAMETER_FPR) {
	  if (mustSaveFloatToSpill) {
	    asmArg.emitSTFD(nextVMArgFloatReg, spillOffsetAIX, FP); 
	  }
	  spillOffsetAIX+=BYTES_IN_DOUBLE; //Kris Venstermans: equals 2 slots on 32-bit platforms and 1 slot on 64-bit platform
	  nextAIXArgFloatReg++;
	  nextVMArgFloatReg++;  
	} else if (nextVMArgFloatReg<=LAST_VOLATILE_FPR) {
	  // (2b) run out of FPR in AIX, but still have 2 more FPR in VM,
	  // so FPR[14:15] goes to the callee spill area
	  asmArg.emitSTFD(nextVMArgFloatReg, spillOffsetAIX, FP);
	  nextVMArgFloatReg++;
	  spillOffsetAIX+=BYTES_IN_DOUBLE; 
	} else {
	  // (2c) run out of FPR in VM, now get the remaining args from the caller spill area 
	  // and move them into the callee spill area
	  asmArg.emitLFD(0, spillOffsetVM , FP);
	  asmArg.emitSTFD(0, spillOffsetAIX, FP);
	  spillOffsetAIX+= BYTES_IN_DOUBLE;
	  spillOffsetVM+=BYTES_IN_DOUBLE;
	}
      } else if (VM.BuildFor32Addr && types[arg].isLongType()) {
	// For 64-bit int arguments on 32-bit platforms
	//
	// (1a) fit in AIX register, move the pair
	if (nextAIXArgReg<=LAST_OS_PARAMETER_GPR-1) {
	  asmArg.emitMR(nextAIXArgReg+1, nextVMArgReg+1);  // move lo-word first
	  asmArg.emitMR(nextAIXArgReg, nextVMArgReg);      // so it doesn't overwritten
	  nextAIXArgReg+=2;
	  nextVMArgReg+=2;
	  spillOffsetAIX+=2*BYTES_IN_STACKSLOT;
	} else if (nextAIXArgReg==LAST_OS_PARAMETER_GPR &&
	  nextVMArgReg<=LAST_VOLATILE_GPR-1) {
	  // (1b) fit in VM register but straddle across AIX register/spill
	  spillOffsetAIX+=BYTES_IN_STACKSLOT;
	  asmArg.emitSTW(nextVMArgReg+1, spillOffsetAIX, FP);   // move lo-word first
	  spillOffsetAIX+=BYTES_IN_STACKSLOT;                    // so it doesn't overwritten
	  asmArg.emitMR(nextAIXArgReg, nextVMArgReg);
	  nextAIXArgReg+=2;
	  nextVMArgReg+=2;	  
	} else if (nextAIXArgReg>LAST_OS_PARAMETER_GPR &&
		   nextVMArgReg<=LAST_VOLATILE_GPR-1) {
	  // (1c) fit in VM register, spill in AIX without straddling register/spill
	  asmArg.emitSTW(nextVMArgReg++, spillOffsetAIX, FP);
	  spillOffsetAIX+=BYTES_IN_STACKSLOT;
	  asmArg.emitSTW(nextVMArgReg++, spillOffsetAIX, FP);
	  spillOffsetAIX+=BYTES_IN_STACKSLOT;
	} else if (nextVMArgReg==LAST_VOLATILE_GPR) {
	  // (1d) split across VM/spill, spill in AIX
	  asmArg.emitSTW(nextVMArgReg++, spillOffsetAIX, FP);
	  spillOffsetAIX+=BYTES_IN_STACKSLOT;
	  asmArg.emitLWZ(0, spillOffsetVM , FP);
	  asmArg.emitSTW(0, spillOffsetAIX, FP);
	  spillOffsetAIX+=BYTES_IN_STACKSLOT;
	  spillOffsetVM+=BYTES_IN_STACKSLOT;
	} else {
	  // (1e) spill both in VM and AIX
	  asmArg.emitLFD(0, spillOffsetVM, FP);
	  asmArg.emitSTFD(0, spillOffsetAIX, FP);
	  spillOffsetAIX+=2*BYTES_IN_STACKSLOT;
	  spillOffsetVM+=2*BYTES_IN_STACKSLOT;
	}
      } else if (types[arg].isReferenceType() ) {	
	// For reference type, replace with handlers before passing to AIX
	//
	
	// (1a) fit in AIX register, move the register
	if (nextAIXArgReg<=LAST_OS_PARAMETER_GPR) {
	  asmArg.emitSTAddrU(nextVMArgReg++, BYTES_IN_ADDRESS, TI );          // append ref to end of JNIRefs array
	  asmArg.emitSUBFC(nextAIXArgReg++, PROCESSOR_REGISTER, TI );  // pass offset in bytes of jref
	  spillOffsetAIX+=BYTES_IN_STACKSLOT;
	} else if (nextVMArgReg<=LAST_VOLATILE_GPR) {
	  // (1b) spill AIX register, but still fit in VM register
	  asmArg.emitSTAddrU(nextVMArgReg++, BYTES_IN_ADDRESS, TI );    // append ref to end of JNIRefs array
	  asmArg.emitSUBFC(0, PROCESSOR_REGISTER, TI );  // compute offset in bytes for jref
	  asmArg.emitSTAddr(0, spillOffsetAIX, FP);       // spill into AIX frame
	  spillOffsetAIX+=BYTES_IN_STACKSLOT;
	} else {
	  // (1c) spill VM register
	  asmArg.emitLAddr(0, spillOffsetVM , FP);        // retrieve arg from VM spill area
	  asmArg.emitSTAddrU(0, BYTES_IN_ADDRESS, TI );  // append ref to end of JNIRefs array
	  asmArg.emitSUBFC(0, PROCESSOR_REGISTER, TI );  // compute offset in bytes for jref
	  asmArg.emitSTAddr(0, spillOffsetAIX, FP);       // spill into AIX frame
	  spillOffsetAIX+=BYTES_IN_STACKSLOT;
	  spillOffsetVM+=BYTES_IN_STACKSLOT;
	}
      } else { 
	// For all other types: int, short, char, byte, boolean ( and long on 64-bit platform)

	// (1a) fit in AIX register, move the register
	if (nextAIXArgReg<=LAST_OS_PARAMETER_GPR) {
	  asmArg.emitMR(nextAIXArgReg++, nextVMArgReg++);
	  spillOffsetAIX+=BYTES_IN_STACKSLOT;
	}

	// (1b) spill AIX register, but still fit in VM register
	else if (nextVMArgReg<=LAST_VOLATILE_GPR) {
	  asmArg.emitSTAddr(nextVMArgReg++, spillOffsetAIX, FP);
	  spillOffsetAIX+=BYTES_IN_STACKSLOT;
	} else {
	  // (1c) spill VM register
	  asmArg.emitLAddr(0,spillOffsetVM, FP);        // retrieve arg from VM spill area
	  asmArg.emitSTAddr(0, spillOffsetAIX, FP);
	  spillOffsetAIX+=BYTES_IN_STACKSLOT;
	  spillOffsetVM+=BYTES_IN_STACKSLOT;
	}
      }
    }
    

    // Append the code sequences for parameter mapping 
    // to the current machine code in reverse order
    // so that the move does not overwrite the parameters
    for (int arg = numArguments-1; arg >= 0; arg--) {
      VM_MachineCode codeForArg = asmForArgs[arg].makeMachineCode();
      asm.appendInstructions(codeForArg.getInstructions());
    }

    // Now add the 2 new JNI parameters:  JNI environment and Class or "this" object
    
    // if static method, append ref for class, else append ref for "this"
    // and pass offset in JNIRefs array in r4 (as second arg to called native code)
    if ( method.isStatic() ) {
      klass.getClassForType();     // ensure the Java class object is created
      // JTOC saved above in JNIEnv is still valid, used by following emitLWZtoc
      asm.emitLAddrToc( 4, klass.getTibOffset() ); // r4 <- class TIB ptr from jtoc
      asm.emitLAddr( 4, 0, 4 );                  // r4 <- first TIB entry == -> class object
      asm.emitLAddr( 4, VM_Entrypoints.classForTypeField.getOffset(), 4 ); // r4 <- java Class for this VM_Class
      asm.emitSTAddrU ( 4, BYTES_IN_ADDRESS, TI );                 // append class ptr to end of JNIRefs array
      asm.emitSUBFC( 4, PROCESSOR_REGISTER, TI );  // pass offset in bytes
    } else {
      asm.emitSTAddrU ( 3, BYTES_IN_ADDRESS, TI );                 // append this ptr to end of JNIRefs array
      asm.emitSUBFC( 4, PROCESSOR_REGISTER, TI );  // pass offset in bytes
    }
    
    // store the new JNIRefs array TOP back into JNIEnv	
    asm.emitSUBFC(TI, PROCESSOR_REGISTER, TI );     // compute offset for the current TOP
    asm.emitSTW(TI, VM_Entrypoints.JNIRefsTopField.getOffset(), S0);
  }
  //-#endif


  //-#if RVM_FOR_OSX
  static void generateReturnCodeForJNIMethod(VM_Assembler asm, VM_Method mth) {
    VM_TypeReference t = mth.getReturnType();
    if (t.isLongType()) {
      asm.emitMR(S0, T0);
      asm.emitMR(T0, T1);
      asm.emitMR(T1, S0);
    }
  }
  //-#endif


  /**
   * Emit code to do the C to Java transition:  JNI methods in VM_JNIFunctions.java
   */ 
  public static void generateGlueCodeForJNIMethod(VM_Assembler asm, VM_Method mth) {
    int offset;

    asm.emitSTAddrU(FP,-JNI_GLUE_FRAME_SIZE,FP);     // buy the glue frame

    // we may need to save CR in the previous frame also if CR will be used
    // CR is to be saved at FP+4 in the previous frame

    // Here we check if this is a JNI function that takes the vararg in the ... style
    // This includes CallStatic<type>Method, Call<type>Method, CallNonVirtual<type>Method
    // For these calls, the vararg starts at the 4th or 5th argument (GPR 6 or 7)
    // So, we save the GPR 6-10 and FPR 1-3 in a volatile register save area 
    // in the glue stack frame so that the JNI function can later repackage the arguments
    // based on the parameter types of target method to be invoked.
    // (For long argument lists, the additional arguments, have been saved in
    // the spill area of the AIX caller, and will be retrieved from there.)
    //
    // If we are compiling such a JNI Function, then emit the code to store
    // GPR 4-10 and FPR 1-6 into the volatile save area.

    String mthName = mth.getName().toString();
    if ((mthName.startsWith("Call") && mthName.endsWith("Method")) ||
	mthName.equals("NewObject")) {

      //-#if RVM_FOR_AIX
      offset = STACKFRAME_HEADER_SIZE + 3*BYTES_IN_STACKSLOT;   // skip over slots for GPR 3-5
      for (int i = 6; i <= 10; i++ ) {
	asm.emitSTAddr(i, offset, FP);
	offset+=BYTES_IN_ADDRESS;
      }
      // store FPRs 1-3 in first 3 slots of volatile FPR save area
      for (int i = 1; i <= 3; i++) {
	asm.emitSTFD (i, offset, FP);
	offset+=BYTES_IN_DOUBLE;
      }
      //-#elif RVM_FOR_LINUX || RVM_FOR_OSX
      // save all parameter registers
      offset = STACKFRAME_HEADER_SIZE + 0;
      for (int i=FIRST_OS_PARAMETER_GPR; i<=LAST_OS_PARAMETER_GPR; i++) {
	asm.emitSTAddr(i, offset, FP);
	offset += BYTES_IN_ADDRESS;
      }
      for (int i =FIRST_OS_PARAMETER_FPR; i<=LAST_OS_PARAMETER_FPR; i++) {
	asm.emitSTFD(i, offset, FP);
	offset += BYTES_IN_DOUBLE;
      }
      //-#endif
    } else {
      //-#if RVM_FOR_LINUX || RVM_FOR_OSX
      // adjust register contents (following SVR4 ABI) for normal JNI functions
      // especially dealing with long, spills
      // number of parameters of normal JNI functions should fix in
      // r3 - r12, f1 - f15, + 24 words, 
      convertParameterFromSVR4ToJava(asm, mth);
      //-#endif
    }

    // Save AIX non-volatile GRPs and FPRs that will not be saved and restored
    // by RVM. These are GPR 13-16 & FPR 14-15.
    //
    offset = STACKFRAME_HEADER_SIZE + JNI_GLUE_SAVED_VOL_SIZE;   // skip 20 word volatile reg save area
    for (int i = FIRST_OS_NONVOLATILE_GPR; i < FIRST_NONVOLATILE_GPR; i++) {
      asm.emitSTAddr(i, offset, FP);
      offset += BYTES_IN_ADDRESS;
    }
    for (int i = FIRST_OS_NONVOLATILE_FPR; i < FIRST_NONVOLATILE_FPR; i++) {
      asm.emitSTFD (i, offset, FP);
      offset +=BYTES_IN_DOUBLE;
    }
 
    // set the method ID for the glue frame
    // and save the return address in the previous frame
    //
    asm.emitLVAL(S0, INVISIBLE_METHOD_ID);
    asm.emitMFLR(0);
    asm.emitSTW (S0, STACKFRAME_METHOD_ID_OFFSET, FP);
    asm.emitSTAddr (0, JNI_GLUE_FRAME_SIZE + STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);

    // Attempt to change the vpStatus of the current Processor to IN_JAVA
    // 
    // on entry T0 = JNIEnv* which is an interior pointer to this thread's VM_JNIEnvironment.
    // We first adjust this in place to be a pointer to a VM_JNIEnvironment and then use
    // it to acquire PROCESSOR_REGISTER, and TI (and JTOC on OSX/Linux).
    //
    // AIX non volatile gprs 13-16 have been saved & are available (also gprs 11-13 can be used).
    // S0=13, S1=14, TI=15, PROCESSOR_REGISTER=16 are available (&have labels) for changing state.
    // we leave the passed arguments untouched, unless we are blocked and have to call sysVirtualProcessorYield

    // Map from JNIEnv* to VM_JNIEnvironment.
    // Must do this outside the loop as we need to do it exactly once.
    asm.emitADDI (T0, -VM_Entrypoints.JNIExternalFunctionsField.getOffset(), T0);

    int retryLoop  = asm.getMachineCodeIndex();
    // acquire Jikes RVM PROCESSOR_REGISTER, TI, and JTOC (OSX/Linux only).
    asm.emitLAddr(PROCESSOR_REGISTER, VM_Entrypoints.JNIEnvSavedPRField.getOffset(), T0);
    asm.emitLAddr(TI, VM_Entrypoints.JNIEnvSavedTIField.getOffset(), T0);  
    //-#if RVM_FOR_LINUX || RVM_FOR_OSX
    // on AIX JTOC is part of AIX Linkage triplet and this already set by our caller.
    // Thus, we only need this load on non-AIX platforms
    asm.emitLAddr(JTOC, VM_Entrypoints.JNIEnvSavedJTOCField.getOffset(), T0);
    //-#endif

    asm.emitLVAL  (S1, VM_Entrypoints.vpStatusField.getOffset());
    asm.emitLWARX (S0, S1, PROCESSOR_REGISTER);               // get status for processor
    asm.emitCMPI  (S0, VM_Processor.BLOCKED_IN_NATIVE);       // check if GC in progress, blocked in native mode
    VM_ForwardReference frBlocked = asm.emitForwardBC(EQ);

    asm.emitLVAL  (S0,  VM_Processor.IN_JAVA);                // S0  <- new state value
    asm.emitSTWCXr(S0,  S1, PROCESSOR_REGISTER);              // attempt to change state to IN_JAVA
    asm.emitBC    (NE, retryLoop);                            // br if failure -retry lwarx by jumping to label0
    VM_ForwardReference frInJava = asm.emitForwardB();        // branch around code to call sysYield

    // branch to here if blocked in native, call sysVirtulaProcessorYield (AIX pthread yield)
    // must save volatile gprs & fprs before the call and restore after
    //
    frBlocked.resolve(asm);
    offset = STACKFRAME_HEADER_SIZE;

    // save volatile GPRS 3-10
    for (int i = FIRST_OS_PARAMETER_GPR; i <= LAST_OS_PARAMETER_GPR; i++) {
      asm.emitSTAddr(i, offset, FP);
      offset+=BYTES_IN_ADDRESS;
    }

    // save volatile FPRS 1-6
    for (int i = FIRST_OS_PARAMETER_FPR; i <= LAST_OS_VARARG_PARAMETER_FPR; i++) {
      asm.emitSTFD (i, offset, FP);
      offset+=BYTES_IN_DOUBLE;
    }

    asm.emitLAddr(S1, VM_Entrypoints.the_boot_recordField.getOffset(), JTOC); // get boot record address
    asm.emitMR   (PROCESSOR_REGISTER, JTOC);                                  // save JTOC so we can restore below
    asm.emitLAddr(JTOC, VM_Entrypoints.sysTOCField.getOffset(), S1);          // load TOC for syscalls from bootrecord
    asm.emitLAddr(TI,   VM_Entrypoints.sysVirtualProcessorYieldIPField.getOffset(), S1);  // load addr of function
    asm.emitMTLR (TI);
    asm.emitBCLRL();                                                          // call sysVirtualProcessorYield in sys.C
    asm.emitMR   (JTOC, PROCESSOR_REGISTER);                                  // restore JTOC

    // restore the saved volatile GPRs 3-10 and FPRs 1-6
    offset = STACKFRAME_HEADER_SIZE;

    // restore volatile GPRS 3-10 
    for (int i = FIRST_OS_PARAMETER_GPR; i <= LAST_OS_PARAMETER_GPR; i++) {
      asm.emitLAddr (i, offset, FP);
      offset+=BYTES_IN_ADDRESS;
    }

    // restore volatile FPRS 1-6  
    for (int i = FIRST_OS_PARAMETER_FPR; i <= LAST_OS_VARARG_PARAMETER_FPR; i++) {
      asm.emitLFD (i, offset, FP);
      offset+=BYTES_IN_DOUBLE;
    }

    asm.emitB (retryLoop);  // br back to label0 to try lwarx again

    // NOW_IN_JAVA:
    // JTOC, PR, and TI are all as Jikes RVM expects them;
    // params are where the Jikes RVM calling conventions expects them.
    // 
    frInJava.resolve(asm);

    // get pointer to top java frame from JNIEnv, compute offset from current
    // frame pointer (offset to avoid more interior pointers) and save offset
    // in this glue frame
    //
    asm.emitLAddr (S0, VM_Entrypoints.JNITopJavaFPField.getOffset(), T0);       // get addr of top java frame from JNIEnv
    asm.emitSUBFC (S0, FP, S0);                                                 // S0 <- offset from current FP
    // AIX -4, LINUX - 8
    asm.emitSTW(S0, JNI_GLUE_FRAME_SIZE + JNI_GLUE_OFFSET_TO_PREV_JFRAME, FP);  // store offset at end of glue frame

    // BRANCH TO THE PROLOG FOR THE JNI FUNCTION
    VM_ForwardReference frNormalPrologue = asm.emitForwardBL();

    // relative branch and link past the following epilog, to the normal prolog of the method
    // the normal epilog of the method will return to the epilog here to pop the glue stack frame

    // RETURN TO HERE FROM EPILOG OF JNI FUNCTION
    // CAUTION:  START OF EPILOG OF GLUE CODE
    // The section of code from here to "END OF EPILOG OF GLUE CODE" is nestled between
    // the glue code prolog and the real body of the JNI method.
    // T0 & T1 (R3 & R4) or F1 contain the return value from the function - DO NOT USE

    // assume: JTOC, TI, and PROCESSOR_REG are valid, and all RVM non-volatile 
    // GPRs and FPRs have been restored.  Our processor state will be  IN_JAVA.

    // establish T2 -> current thread's VM_JNIEnvironment, from activeThread field
    // of current processor      
    asm.emitLAddr(T2, VM_Entrypoints.activeThreadField.getOffset(), PROCESSOR_REGISTER);   // T2 <- activeThread of PR
    asm.emitLAddr(T2, VM_Entrypoints.jniEnvField.getOffset(), T2);                         // T2 <- JNIEnvironment 

    // before returning to C, set pointer to top java frame in JNIEnv, using offset
    // saved in this glue frame during transition from C to Java.  GC will use this saved
    // frame pointer if it is necessary to do GC with a processors active thread
    // stuck (and blocked) in native C, ie. GC starts scanning the threads stack at that frame.
    
    // AIX -4, LINUX -8
    asm.emitLInt (T3, JNI_GLUE_FRAME_SIZE + JNI_GLUE_OFFSET_TO_PREV_JFRAME, FP); // load offset from FP to top java frame
    asm.emitADD  (T3, FP, T3);                                    // T3 <- address of top java frame
    asm.emitSTAddr(T3, VM_Entrypoints.JNITopJavaFPField.getOffset(), T2);     // store TopJavaFP back into JNIEnv

    // check to see if this frame address is the sentinel since there may be no further Java frame below
    asm.emitCMPAddr(T3, VM_Constants.STACKFRAME_SENTINEL_FP.toInt());
    VM_ForwardReference fr4 = asm.emitForwardBC(EQ);
    asm.emitLAddr(S0, 0, T3);                   // get fp for caller of prev J to C transition frame
    fr4.resolve(asm);

    // store current PR into VM_JNIEnvironment; we may have switched PRs while in Java mode.
    asm.emitSTAddr(PROCESSOR_REGISTER, VM_Entrypoints.JNIEnvSavedPRField.getOffset(), T2);

    // change the state of the VP to IN_NATIVE.
    //
    asm.emitLVAL (S0,  VM_Processor.IN_NATIVE);
    asm.emitSTW  (S0,  VM_Entrypoints.vpStatusField.getOffset(), PROCESSOR_REGISTER);

    // Restore those AIX nonvolatile registers saved in the prolog above
    // Here we only save & restore ONLY those registers not restored by RVM
    //
    offset = STACKFRAME_HEADER_SIZE + JNI_GLUE_SAVED_VOL_SIZE;   // skip 20 word volatile reg save area
    for (int i = FIRST_OS_NONVOLATILE_GPR; i < FIRST_NONVOLATILE_GPR; i++) {
      asm.emitLAddr (i, offset, FP);                     // 4 instructions
      offset += BYTES_IN_ADDRESS;
    }
    for (int i = FIRST_OS_NONVOLATILE_FPR; i < FIRST_NONVOLATILE_FPR; i++) {
      asm.emitLFD  (i, offset, FP);                   // 2 instructions
      offset +=BYTES_IN_DOUBLE;
    }

    // pop frame
    asm.emitADDI(FP, JNI_GLUE_FRAME_SIZE, FP);

    // load return address & return to caller
    // T0 & T1 (or F1) should still contain the return value
    //
    asm.emitLAddr(T2, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);
    asm.emitMTLR(T2);
    asm.emitBCLR (); // branch always, through link register

    // END OF EPILOG OF GLUE CODE; rest of method generated by VM_Compiler from bytecodes of method in VM_JNIFunctions
    frNormalPrologue.resolve(asm);
  } 

  //-#if RVM_FOR_LINUX
  // SVR4 rounds gprs to odd for longs, but rvm convention uses all
  // we only process JNI functions that uses parameters directly
  // so only handle parameters in gprs now
  static void convertParameterFromSVR4ToJava(VM_Assembler asm, VM_Method meth) {
    VM_TypeReference[] argTypes = meth.getParameterTypes();
    int argCount = argTypes.length;
    int nextVMReg = FIRST_VOLATILE_GPR;
    int nextAIXReg = FIRST_OS_PARAMETER_GPR;
    
    for (int i=0; i<argCount; i++) {
      if (argTypes[i].isFloatType() || argTypes[i].isDoubleType()) {
	// skip over
      } else {
	if (argTypes[i].isLongType()) {
	  nextAIXReg += (nextAIXReg + 1) & 0x01;
	  if (nextAIXReg != nextVMReg) {
	    // Native and Java reg do not match
	    asm.emitMR(nextVMReg, nextAIXReg);
	    asm.emitMR(nextVMReg + 1, nextAIXReg + 1);
	  }
	  nextAIXReg += 2;
	  nextVMReg += 2;
	} else {
	  if (nextAIXReg != nextVMReg) {
	    asm.emitMR(nextVMReg, nextAIXReg);
	  }
	  nextAIXReg ++;
	  nextVMReg ++;
	}
      }

      if (nextAIXReg > LAST_OS_PARAMETER_GPR + 1) {
	VM.sysWrite("ERROR: "+meth+" has too many int or long parameters\n");
	VM.sysExit(-1);
      }
    }
  }
  //-#endif RVM_FOR_LINUX

  //-#if RVM_FOR_OSX
  // SVR4 rounds gprs to odd for longs, but rvm convention uses all
  // we only process JNI functions that uses parameters directly
  // so only handle parameters in gprs now
  static void convertParameterFromSVR4ToJava(VM_Assembler asm, VM_Method meth) {
    VM_TypeReference[] argTypes = meth.getParameterTypes();
    int argCount = argTypes.length;
    int nextVMReg = FIRST_VOLATILE_GPR;
    int nextOSXReg = FIRST_OS_PARAMETER_GPR;
    
    for (int i=0; i<argCount; i++) {
      if (argTypes[i].isFloatType()) {
        // skip over
      } else if  (argTypes[i].isDoubleType()) {
        nextOSXReg ++;
      } else {
        if (argTypes[i].isLongType()) {
          if (nextOSXReg != nextVMReg) {
            asm.emitMR(nextVMReg, nextOSXReg);
            asm.emitMR(nextVMReg + 1, nextOSXReg +1);
          }
          nextOSXReg += 2;
          nextVMReg += 2;
        } else {
          if (nextOSXReg != nextVMReg) {
            asm.emitMR(nextVMReg, nextOSXReg);
          }
          nextOSXReg ++;
          nextVMReg ++;
        }
      }

      if (nextOSXReg > LAST_OS_PARAMETER_GPR + 1) {
        VM.sysWrite("ERROR: "+meth+" has too many int or long parameters\n");
        VM.sysExit(-1);
      }
    }
  }
  //-#endif RVM_FOR_OSX
}
