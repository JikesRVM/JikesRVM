/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.jni;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * @author Ton Ngo 
 * @author Steve Smith
 * @modified Dave Grove
 * @modified by Kris Venstermans (64-bit port AIX)
 */
public class VM_JNICompiler implements VM_BaselineConstants,
                                       VM_AssemblerConstants,
                                       VM_JNIStackframeLayoutConstants {

  /**
   * This method creates the stub to link native method.  It will be called
   * from the lazy linker the first time a native method is invoked.  The stub
   * generated will be patched by the lazy linker to link to the native method
   * for all future calls. <p>
   * <pre>
   * The stub performs the following tasks in the prologue:
   *   -Allocate the glue frame
   *   -Save the PR register in the JNI Environment for reentering Java later
   *   -Shuffle the parameters in the registers to conform to the AIX convention
   *   -Save the nonvolatile registers in a known space in the frame to be used 
   *    for the GC stack map
   *   -Push a new JREF frame on the JNIRefs stack
   *   -Supply the first JNI argument:  the JNI environment pointer
   *   -Supply the second JNI argument:  class object if static, "this" if virtual
   *   -Setup the TOC (AIX only) and IP to the corresponding native code
   *
   * The stub performs the following tasks in the epilogue:
   *   -PR register is AIX nonvolatile, so it should be restored already
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
   *   |vol fpr1  | saved AIX volatile fpr during becomeNativeThread
   *   | ...      | 
   *   |vol fpr6  | saved AIX volatile fpr during becomeNativeThread
   *   |vol r4    | saved AIX volatile regs during Yield (to be removed when code moved to Java)   
   *   | ...      | 
   *   |vol r10   | saved AIX volatile regs during Yield    <- JNI_OS_PARAMETER_REGISTER_OFFSET
   *   |ENV       | VM_JNIEnvironment                       <- JNI_ENV_OFFSET
   *   |nonvol 17 | save 15 nonvolatile GPRs for GC stack mapper
   *   | ...      |
   *   |nonvol 31 |                                         <- JNI_RVM_NONVOLATILE_OFFSET
   *   |----------|   
   *   |  fp      | <- Java caller frame
   *   | mid      |
   *   | xxx      |
   *   |          |
   *   |          |
   *   |          |
   *   |----------|
   *   |          |
   * </pre>
   * 
   * Linux (and OSX) uses different transition scheme: the Java-to-Native transition
   * stackframe consists of two mini frames: frame 1 has RVM's stack header with
   * compiled method ID, and frame 2 has C (SVR4)'s stackframe layout. 
   * Comparing to AIX transition frame,
   * Linux version inserts a RVM frame header right above JNI_SAVE_AREA. <p>
   * 
   * <pre>
   *   |------------|
   *   | fp         | <- Java to C glue frame (2)
   *   | lr         |
   *   | 0          | <- spill area, see VM_Compiler.getFrameSize
   *   | 1          |
   *   |.......     |
   *   |------------| 
   *   | fp         | <- Java to C glue frame (1)
   *   | cmid       | 
   *   | lr         |
   *   | padding    |
   *   | GC flag    |
   *   | Affinity   |
   *   | ........   |
   *   |------------| 
   *   | fp         | <- Java caller frame
   *   | mid        |
   * </pre>
   * 
   * VM_Runtime.unwindNativeStackFrame will return a pointer to glue frame (1).
   * The lr slot of frame (2) holds the address of out-of-line machine code 
   * which should be in bootimage, and GC shouldn't move this code. 
   * The VM_JNIGCIterator returns the lr of frame (2) as the result of 
   * getReturnAddressAddress.
   */
  public static synchronized VM_CompiledMethod compile (VM_NativeMethod method) {
    VM_JNICompiledMethod cm = (VM_JNICompiledMethod)VM_CompiledMethods.createCompiledMethod(method, VM_CompiledMethod.JNI);
    int compiledMethodId = cm.getId();
    VM_Assembler asm    = new VM_Assembler(0);
    int frameSize       = VM_Compiler.getFrameSize(method);
    VM_Class klass      = method.getDeclaringClass();

    /* initialization */
    if (VM.VerifyAssertions) VM._assert(T3 <= LAST_VOLATILE_GPR);           // need 4 gp temps
    if (VM.VerifyAssertions) VM._assert(F3 <= LAST_VOLATILE_FPR);           // need 4 fp temps
    if (VM.VerifyAssertions) VM._assert(S0 < S1 && S1 <= LAST_SCRATCH_GPR); // need 2 scratch

    Address nativeIP  = method.getNativeIP();
    Address nativeTOC = method.getNativeTOC();

    // NOTE:  this must be done before the condition VM_Thread.hasNativeStackFrame() become true
    // so that the first Java to C transition will be allowed to resize the stack
    // (currently, this is true when the JNIRefsTop index has been incremented from 0)
    asm.emitNativeStackOverflowCheck(frameSize + 14);   // add at least 14 for C frame (header + spill)

    int parameterAreaSize = method.getParameterWords() << LOG_BYTES_IN_STACKSLOT;   // number of bytes of arguments

    // save return address in caller frame
    asm.emitMFLR(REGISTER_ZERO);
    asm.emitSTAddr(REGISTER_ZERO, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);      
  
    //-#if RVM_WITH_SVR4_ABI || RVM_WITH_MACH_O_ABI
    // buy mini frame (2)
    asm.emitSTAddrU   (FP, -JNI_SAVE_AREA_SIZE, FP);
    asm.emitLVAL  (S0, compiledMethodId);                // save jni method id at mini frame (2)
    asm.emitSTW(S0, STACKFRAME_METHOD_ID_OFFSET, FP);
    // buy mini frame (1), the total size equals to frameSize
    asm.emitSTAddrU   (FP, -frameSize + JNI_SAVE_AREA_SIZE, FP);
    //-#endif
        
    //-#if RVM_WITH_POWEROPEN_ABI
    asm.emitSTAddrU   (FP,  -frameSize, FP);             // get transition frame on stack
    asm.emitLVAL  (S0, compiledMethodId);                // save jni method id
    asm.emitSTW(S0, STACKFRAME_METHOD_ID_OFFSET, FP);
    //-#endif

    // establish S1 -> VM_Thread, S0 -> threads JNIEnv structure      
    asm.emitLAddrOffset(S1, PROCESSOR_REGISTER, VM_Entrypoints.activeThreadField.getOffset());
    asm.emitLAddrOffset(S0, S1, VM_Entrypoints.jniEnvField.getOffset());

    // save the PR register in the JNIEnvironment object for possible calls back into Java
    asm.emitSTAddrOffset(PROCESSOR_REGISTER, S0, VM_Entrypoints.JNIEnvSavedPRField.getOffset());   
    
    // save the JNIEnvironment in the stack frame so we can use it to acquire the PR
    // when we return from native code.
    asm.emitSTAddr (S0, frameSize - JNI_ENV_OFFSET, FP);  // save PR in frame  

    // save current frame pointer in JNIEnv, JNITopJavaFP, which will be the frame
    // to start scanning this stack during GC, if top of stack is still executing in C
    //-#if RVM_WITH_SVR4_ABI || RVM_WITH_MACH_O_ABI
    // for Linux, save mini (2) frame pointer, which has method id
    asm.emitLAddr (PROCESSOR_REGISTER, 0, FP);
    asm.emitSTAddrOffset(PROCESSOR_REGISTER, S0, VM_Entrypoints.JNITopJavaFPField.getOffset());
    //-#elif RVM_WITH_POWEROPEN_ABI
    asm.emitSTAddrOffset(FP, S0, VM_Entrypoints.JNITopJavaFPField.getOffset());           
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

    // generate the code to map the parameters to OS convention and add the
    // second parameter (either the "this" ptr or class if a static method).
    // The JNI Function ptr first parameter is set before making the call
    // by the out of line machine code we invoke below.
    // Opens a new frame in the JNIRefs table to register the references.
    // Assumes S0 set to JNIEnv, kills KLUDGE_TI_REG, S1 & PROCESSOR_REGISTER
    // On return, S0 still contains JNIEnv
    storeParameters(asm, frameSize, method, klass);
        
    // Get address of out_of_line prolog into S1, before setting TOC reg.
    asm.emitLAddrOffset(S1, JTOC, VM_Entrypoints.invokeNativeFunctionInstructionsField.getOffset());
    asm.emitMTCTR (S1);

    // set the TOC and IP for branch to out_of_line code
    asm.emitLVALAddr (JTOC,  nativeTOC);
    asm.emitLVALAddr (S1,    nativeIP);

    // go to VM_OutOfLineMachineCode.invokeNativeFunctionInstructions
    // It will change the Processor status to "in_native" and transfer to the native code.  
    // On return it will change the state back to "in_java" (waiting if blocked).
    //
    // The native address entrypoint is in register S1
    // The native TOC has been loaded into the TOC register
    // S0 still points to threads JNIEnvironment
    //
    asm.emitBCCTRL();

    // check if GC has occurred, If GC did not occur, then 
    // VM NON_VOLATILE regs were restored by OS and are valid.  If GC did occur
    // objects referenced by these restored regs may have moved, in this case we
    // restore the nonvolatile registers from our save area,
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
    asm.emitLAddrOffset(S0, PROCESSOR_REGISTER, VM_Entrypoints.activeThreadField.getOffset());  // S0 holds thread pointer

    // reestablish S0 to hold pointer to VM_JNIEnvironment
    asm.emitLAddrOffset(S0, S0, VM_Entrypoints.jniEnvField.getOffset());       

    // pop jrefs frame off the JNIRefs stack, "reopen" the previous top jref frame
    // use S1 as scratch, also use T2, T3 for scratch which are no longer needed
    asm.emitLAddrOffset(S1, S0, VM_Entrypoints.JNIRefsField.getOffset());          // load base of JNIRefs array
    asm.emitLIntOffset (T2, S0, VM_Entrypoints.JNIRefsSavedFPField.getOffset());   // get saved offset for JNIRefs frame ptr previously pushed onto JNIRefs array
    asm.emitADDI (T3, -BYTES_IN_STACKSLOT, T2);                                    // compute offset for new TOP
    asm.emitSTWoffset  (T3, S0, VM_Entrypoints.JNIRefsTopField.getOffset());       // store new offset for TOP into JNIEnv
    asm.emitLIntX(T2, S1, T2);                                    // retrieve the previous frame ptr
    asm.emitSTWoffset  (T2, S0, VM_Entrypoints.JNIRefsSavedFPField.getOffset());   // store new offset for JNIRefs frame ptr into JNIEnv

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

    // pop the glue stack frame, restore the Java caller frame
    asm.emitADDI (FP,  +frameSize, FP);              // remove linkage area

    // C return value is already where caller expected it (T0/T1 or F0)
    // So, just restore the return address to the link register.

    asm.emitLAddr(REGISTER_ZERO, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); 
    asm.emitMTLR (REGISTER_ZERO);                           // restore return address

    // CHECK EXCEPTION AND BRANCH TO ATHROW CODE OR RETURN NORMALLY

    asm.emitLAddrOffset(T2, S0, VM_Entrypoints.JNIPendingExceptionField.getOffset());   // get pending exception from JNIEnv
    asm.emitLVAL  (T3, 0);                   // get a null value to compare
    asm.emitSTAddrOffset(T3, S0, VM_Entrypoints.JNIPendingExceptionField.getOffset()); // clear the current pending exception
    asm.emitCMPAddr(T2, T3);
    VM_ForwardReference fr3 = asm.emitForwardBC(NE);
    asm.emitBCLR();                             // if no pending exception, proceed to return to caller
    fr3.resolve(asm);

    // An exception is pending, deliver the exception to the caller
    // as if executing an athrow in the caller
    // at the location of the call to the native method
    asm.emitLAddrToc(T3, VM_Entrypoints.athrowMethod.getOffset());
    asm.emitMTCTR(T3);                         // point LR to the exception delivery code
    asm.emitMR (T0, T2);                       // copy the saved exception to T0
    asm.emitBCCTR();                           // then branch to the exception delivery code, does not return

    VM_MachineCode machineCode = asm.makeMachineCode();
    cm.compileComplete(machineCode.getInstructions());
    return cm;
  } 

  
  /**
   * Map the arguments from RVM convention to OS convention,
   * and replace all references with indexes into JNIRefs array.
   * Assumption on entry:
   * -KLUDGE_TI_REG, PROCESSOR_REGISTER and S1 are available for use as scratch register
   * -the frame has been created, FP points to the new callee frame
   * Also update the JNIRefs array
   */
  private static void storeParameters(VM_Assembler asm, int frameSize, 
                                      VM_Method method, VM_Class klass) {

    int nextOSArgReg, nextOSArgFloatReg, nextVMArgReg, nextVMArgFloatReg; 
    
    // offset to the spill area in the callee (OS frame):
    int spillOffsetOS;
    if (VM.BuildForPowerOpenABI || VM.BuildForMachOABI) {
      // 1st spill = JNIEnv, 2nd spill = class
      spillOffsetOS = NATIVE_FRAME_HEADER_SIZE + 2*BYTES_IN_STACKSLOT;
    } else if (VM.BuildForSVR4ABI) {
      spillOffsetOS = NATIVE_FRAME_HEADER_SIZE;
    } else {
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
    

    // offset to the spill area in the caller (RVM frame), relative to the callee's FP
    int spillOffsetVM = frameSize + STACKFRAME_HEADER_SIZE;

    // does NOT include implicit this or class ptr    
    VM_TypeReference[] types = method.getParameterTypes();
    
    // Set up the Reference table for GC
    // PR <- JREFS array base
    asm.emitLAddrOffset(PROCESSOR_REGISTER, S0, VM_Entrypoints.JNIRefsField.getOffset());
    // TI <- JREFS current top 
    asm.emitLIntOffset(KLUDGE_TI_REG, S0, VM_Entrypoints.JNIRefsTopField.getOffset());   // JREFS offset for current TOP 
    asm.emitADD(KLUDGE_TI_REG, PROCESSOR_REGISTER, KLUDGE_TI_REG);                // convert into address

    // TODO - count number of refs
    // TODO - emit overflow check for JNIRefs array
    
    // start a new JNIRefs frame on each transition from Java to native C
    // push current SavedFP ptr onto top of JNIRefs stack (use available S1 reg as a temp)
    // and make current TOP the new savedFP
    //
    asm.emitLIntOffset  (S1, S0, VM_Entrypoints.JNIRefsSavedFPField.getOffset());
    asm.emitSTWU (S1, BYTES_IN_ADDRESS, KLUDGE_TI_REG);                           // push prev frame ptr onto JNIRefs array     
    asm.emitSUBFC(S1, PROCESSOR_REGISTER, KLUDGE_TI_REG);          // compute offset for new TOP
    asm.emitSTWoffset  (S1, S0, VM_Entrypoints.JNIRefsSavedFPField.getOffset());  // save new TOP as new frame ptr in JNIEnv


    // for static methods: caller has placed args in r3,r4,... 
    // for non-static methods:"this" ptr is in r3, and args start in r4,r5,...
    // 
    // for static methods:                for nonstatic methods:       
    //  Java caller     OS callee         Java caller     OS callee    
    //  -----------     ----------          -----------     ----------  
    //  spill = arg11 -> new spill          spill = arg11 -> new spill  
    //  spill = arg10 -> new spill          spill = arg10 -> new spill  
    //                                      spill = arg9  -> new spill  
    //  spill = arg9  -> new spill                                      
    //  spill = arg8  -> new spill          spill = arg8  -> new spill  
    //    R10 = arg7  -> new spill          spill = arg7  -> new spill  
    //    R9  = arg6  -> new spill            R10 = arg6  -> new spill  
    //                                                             
    //    R8  = arg5  -> R10                  R9  = arg5  -> R10         
    //    R7  = arg4  -> R9                   R8  = arg4  -> R9          
    //    R6  = arg3  -> R8                   R7  = arg3  -> R8          
    //    R5  = arg2  -> R7                   R6  = arg2  -> R7          
    //    R4  = arg1  -> R6                   R5  = arg1  -> R6          
    //    R3  = arg0  -> R5                   R4  = arg0  -> R5          
    //                   R4 = class           R3  = this  -> R4         
    //                   R3 = JNIenv                         R3 = JNIenv
    //

    nextOSArgFloatReg = FIRST_OS_PARAMETER_FPR;
    nextVMArgFloatReg = FIRST_VOLATILE_FPR;
    nextOSArgReg      = FIRST_OS_PARAMETER_GPR + 2;   // 1st reg = JNIEnv, 2nd reg = class/this
    if (method.isStatic()) {
      nextVMArgReg = FIRST_VOLATILE_GPR;              
    } else {
      nextVMArgReg = FIRST_VOLATILE_GPR+1; // 1st reg = this, to be processed separately
    }

    // The loop below assumes the following relationship:
    if (VM.VerifyAssertions) VM._assert(FIRST_OS_PARAMETER_FPR==FIRST_VOLATILE_FPR);
    if (VM.VerifyAssertions) VM._assert(LAST_OS_PARAMETER_FPR<=LAST_VOLATILE_FPR);
    if (VM.VerifyAssertions) VM._assert(FIRST_OS_PARAMETER_GPR==FIRST_VOLATILE_GPR);
    if (VM.VerifyAssertions) VM._assert(LAST_OS_PARAMETER_GPR<=LAST_VOLATILE_GPR);

    generateParameterPassingCode(asm, types,
                                 nextVMArgReg, nextVMArgFloatReg, spillOffsetVM,
                                 nextOSArgReg, nextOSArgFloatReg, spillOffsetOS
                                 );

    // Now add the 2 JNI parameters:  JNI environment and Class or "this" object
    
    // if static method, append ref for class, else append ref for "this"
    // and pass offset in JNIRefs array in r4 (as second arg to called native code)
    int SECOND_OS_PARAMETER_GPR = FIRST_OS_PARAMETER_GPR+1;
    if (method.isStatic()) {
      klass.getClassForType();     // ensure the Java class object is created
      // ASSMPTION: JTOC saved above in JNIEnv is still valid,
      // used by following emitLAddrToc
      asm.emitLAddrToc(SECOND_OS_PARAMETER_GPR, klass.getTibOffset());  // r4 <= TIB
      asm.emitLAddr(SECOND_OS_PARAMETER_GPR, TIB_TYPE_INDEX, SECOND_OS_PARAMETER_GPR); // r4 <= VM_Type
      asm.emitLAddrOffset(SECOND_OS_PARAMETER_GPR, SECOND_OS_PARAMETER_GPR, VM_Entrypoints.classForTypeField.getOffset()); // r4 <- java.lang.Class
      asm.emitSTAddrU (SECOND_OS_PARAMETER_GPR, BYTES_IN_ADDRESS, KLUDGE_TI_REG);                 // append class ptr to end of JNIRefs array
      asm.emitSUBFC(SECOND_OS_PARAMETER_GPR, PROCESSOR_REGISTER, KLUDGE_TI_REG);  // pass offset in bytes
    } else {
      asm.emitSTAddrU(T0, BYTES_IN_ADDRESS, KLUDGE_TI_REG);                 // append this ptr to end of JNIRefs array
      asm.emitSUBFC(SECOND_OS_PARAMETER_GPR, PROCESSOR_REGISTER, KLUDGE_TI_REG);  // pass offset in bytes
    }
    
    // store the new JNIRefs array TOP back into JNIEnv 
    asm.emitSUBFC(KLUDGE_TI_REG, PROCESSOR_REGISTER, KLUDGE_TI_REG);     // compute offset for the current TOP
    asm.emitSTWoffset(KLUDGE_TI_REG, S0, VM_Entrypoints.JNIRefsTopField.getOffset());
  }
  
  //-#if RVM_WITH_SVR4_ABI || RVM_WITH_MACH_O_ABI
  /**
   * Generates instructions to copy parameters from RVM convention to OS convention.
   * @param asm         The {@link VM_Assembler} object
   * @param types       The parameter types
   * @param nextVMArgReg   The first parameter GPR in RVM convention,
   *                      the last parameter GPR is defined as LAST_VOLATILE_GPR.
   * @param nextVMArgFloatReg The first parameter FPR in RVM convention,
   *                           the last parameter FPR is defined as LAST_VOLATILE_FPR.
   * @param spillOffsetVM  The spill offset (related to FP) in RVM convention
   * @param nextOSArgReg  the first parameter GPR in OS convention,
   *                      the last parameter GPR is defined as LAST_OS_PARAMETER_GPR.
   * @param nextOSArgFloatReg  The first parameter FPR in OS convention,
   *                           the last parameter FPR is defined as LAST_OS_PARAMETER_FPR.
   * @param spillOffsetOS  The spill offset (related to FP) in OS convention
   */   
  private static void generateParameterPassingCode(VM_Assembler asm,
                                                   VM_TypeReference[] types,
                                                   int nextVMArgReg,
                                                   int nextVMArgFloatReg,
                                                   int spillOffsetVM,
                                                   int nextOSArgReg,
                                                   int nextOSArgFloatReg,
                                                   int spillOffsetOS) {
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
    int numArguments = types.length;
    VM_Assembler[] asmForArgs = new VM_Assembler[numArguments];
    
    for (int arg = 0; arg < numArguments; arg++) {
      //-#if RVM_FOR_OSX
      int spillSizeOSX = 0;
      int nextOsxGprIncrement = 1;
      //-#endif
      
      asmForArgs[arg] = new VM_Assembler(0);
      VM_Assembler asmArg = asmForArgs[arg];

      // For 32-bit float arguments, must be converted to
      // double 
      //
      if (types[arg].isFloatType() || types[arg].isDoubleType()) {
        boolean is32bits = types[arg].isFloatType();

        //-#if RVM_FOR_OSX
        if (is32bits)
          spillSizeOSX = 4;
        else {
          spillSizeOSX = 8;
          nextOsxGprIncrement = 2;
        }
        //-#endif
        
        // 1. check the source, the value will be in srcVMArg
        int srcVMArg; // scratch fpr
        if (nextVMArgFloatReg <= LAST_VOLATILE_FPR) {
          srcVMArg = nextVMArgFloatReg;
          nextVMArgFloatReg ++;
        } else {
          srcVMArg = FIRST_SCRATCH_FPR;
          // VM float reg is in spill area
          if (is32bits) {
            spillOffsetVM += BYTES_IN_STACKSLOT;
            asmArg.emitLFS(srcVMArg, spillOffsetVM - BYTES_IN_FLOAT, FP);
          } else {
            asmArg.emitLFD(srcVMArg, spillOffsetVM, FP);
            spillOffsetVM += BYTES_IN_DOUBLE;
          }
        }  
                
        // 2. check the destination, 
        if (nextOSArgFloatReg <= LAST_OS_PARAMETER_FPR) {
          // leave it there
          nextOSArgFloatReg ++;
        } else {
          //-#if RVM_FOR_LINUX          
          if (is32bits) {
            asmArg.emitSTFS(srcVMArg, spillOffsetOS, FP);
            spillOffsetOS += BYTES_IN_ADDRESS;
          } else {
            // spill it, round the spill address to 8
            // assuming FP is aligned to 8
            spillOffsetOS = (spillOffsetOS + 7) & -8;       
            asmArg.emitSTFD(srcVMArg, spillOffsetOS, FP);
            spillOffsetOS += BYTES_IN_DOUBLE;
          }
          //-#elif RVM_FOR_OSX
          if (is32bits) {
            asmArg.emitSTFS(srcVMArg, spillOffsetOS, FP);
          } else {
            asmArg.emitSTFD(srcVMArg, spillOffsetOS, FP);
          }
          //-#endif
        }
        // for 64-bit long arguments
      } else if (types[arg].isLongType() && VM.BuildFor32Addr) {
        //-#if RVM_FOR_OSX
        spillSizeOSX = 8;
        nextOsxGprIncrement = 2;
        //-#endif
        
        // handle OS first
        boolean dstSpilling;
        int regOrSpilling = -1;  // it is register number or spilling offset
        // 1. check if Linux register > 9
        if (nextOSArgReg > (LAST_OS_PARAMETER_GPR - 1)) {
          // goes to spilling area
          dstSpilling = true;

          //-#if RVM_FOR_LINUX
          /* NOTE: following adjustment is not stated in SVR4 ABI, but 
           * was implemented in GCC.
           * -- Feng
           */
          nextOSArgReg = LAST_OS_PARAMETER_GPR + 1;
          
          // do alignment and compute spilling offset
          spillOffsetOS = (spillOffsetOS + 7) & -8;
          regOrSpilling = spillOffsetOS;
          spillOffsetOS += BYTES_IN_LONG;

          //-#elif RVM_FOR_OSX
          
          regOrSpilling = spillOffsetOS;          
          //-#endif
        } else {
          // use registers
          dstSpilling = false;

          //-#if RVM_FOR_LINUX
          // rounds to odd
          nextOSArgReg += (nextOSArgReg + 1) & 0x01; // if gpr is even, gpr += 1
          regOrSpilling = nextOSArgReg;
          nextOSArgReg += 2;
          //-#elif RVM_FOR_OSX
          regOrSpilling = nextOSArgReg;
          //-#endif
        }
        
        // handle RVM source
        if (nextVMArgReg < LAST_VOLATILE_GPR) {
          // both parts in registers
          if (dstSpilling) {
            asmArg.emitSTW(nextVMArgReg+1, regOrSpilling+4, FP);

            //-#if RVM_FOR_LINUX
            asmArg.emitSTW(nextVMArgReg, regOrSpilling, FP);
            //-#elif RVM_FOR_OSX
            if (nextOSArgReg == LAST_OS_PARAMETER_GPR) {
              asmArg.emitMR(nextOSArgReg, nextVMArgReg); 
            } else {
              asmArg.emitSTW(nextVMArgReg, regOrSpilling, FP);
            }
            //-#endif
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
            asmArg.emitLWZ(REGISTER_ZERO, spillOffsetVM, FP);
            asmArg.emitSTW(REGISTER_ZERO, regOrSpilling+4, FP);
            asmArg.emitSTW(nextVMArgReg, regOrSpilling, FP);
          } else {
            asmArg.emitLWZ(regOrSpilling + 1, spillOffsetVM, FP);
            asmArg.emitMR(regOrSpilling, nextVMArgReg);
          }
          // advance spillOffsetVM and nextVMArgReg
          nextVMArgReg ++;
          spillOffsetVM += BYTES_IN_STACKSLOT;
        } else if (nextVMArgReg > LAST_VOLATILE_GPR) {
          if (dstSpilling) {
            asmArg.emitLFD(FIRST_SCRATCH_FPR, spillOffsetVM, FP);
            asmArg.emitSTFD(FIRST_SCRATCH_FPR, regOrSpilling, FP);
          } else {
            // this shouldnot happen, VM spills, OS has registers
            asmArg.emitLWZ(regOrSpilling + 1, spillOffsetVM+4, FP);
            asmArg.emitLWZ(regOrSpilling, spillOffsetVM, FP);
          }     
          spillOffsetVM += BYTES_IN_LONG;
        }
      } else if (types[arg].isLongType() && VM.BuildFor64Addr) {
        
        // handle OS first
        boolean dstSpilling;
        int regOrSpilling = -1;  // it is register number or spilling offset
        // 1. check if Linux register > 9
        if (nextOSArgReg > LAST_OS_PARAMETER_GPR) {
          // goes to spilling area
          dstSpilling = true;

          /* NOTE: following adjustment is not stated in SVR4 ABI, but 
           * was implemented in GCC.
           * -- Feng
           */
          nextOSArgReg = LAST_OS_PARAMETER_GPR + 1;
          
          // do alignment and compute spilling offset
          spillOffsetOS = (spillOffsetOS + 7) & -8;
          regOrSpilling = spillOffsetOS;
          spillOffsetOS += BYTES_IN_LONG;
          
        } else {
          // use registers
          dstSpilling = false;

          // rounds to odd
          regOrSpilling = nextOSArgReg;
          nextOSArgReg += 1;
        }
        
        // handle RVM source
        if (nextVMArgReg <= LAST_VOLATILE_GPR) {
          // both parts in registers
          if (dstSpilling) {
            asmArg.emitSTD(nextVMArgReg, regOrSpilling, FP);
          } else {
            asmArg.emitMR(regOrSpilling, nextVMArgReg);
          }
          // advance register counting, Linux register number
          // already advanced 
          nextVMArgReg += 1;
        } else if (nextVMArgReg > LAST_VOLATILE_GPR) {
          if (dstSpilling) {
            asmArg.emitLFD(FIRST_SCRATCH_FPR, spillOffsetVM, FP);
            asmArg.emitSTFD(FIRST_SCRATCH_FPR, regOrSpilling, FP);
          } else {
            // this shouldnot happen, VM spills, OS has registers;
            asmArg.emitLD(regOrSpilling, spillOffsetVM, FP);
          }     
          spillOffsetVM += BYTES_IN_LONG;
        }
      } else if (types[arg].isReferenceType() ) {
        //-#if RVM_FOR_OSX
        spillSizeOSX = 4;
        //-#endif
        
        // For reference type, replace with handles before passing to native
        int srcreg, dstreg;
        if (nextVMArgReg <= LAST_VOLATILE_GPR) {
          srcreg = nextVMArgReg++;
        } else {
          srcreg = REGISTER_ZERO;
          asmArg.emitLAddr(srcreg, spillOffsetVM, FP);
          spillOffsetVM += BYTES_IN_ADDRESS;
        }

        // Are we passing NULL?
        asmArg.emitCMPI(srcreg, 0);
        VM_ForwardReference isNull = asmArg.emitForwardBC(EQ);
        
        // NO: put it in the JNIRefs array and pass offset
        asmArg.emitSTAddrU(srcreg, BYTES_IN_ADDRESS, KLUDGE_TI_REG);
        if (nextOSArgReg <= LAST_OS_PARAMETER_GPR) {
          asmArg.emitSUBFC(nextOSArgReg, PROCESSOR_REGISTER, KLUDGE_TI_REG);
        } else {
          asmArg.emitSUBFC(REGISTER_ZERO, PROCESSOR_REGISTER, KLUDGE_TI_REG);
          asmArg.emitSTAddr(REGISTER_ZERO, spillOffsetOS, FP);
        }
        VM_ForwardReference done = asmArg.emitForwardB();
        
        // YES: pass NULL (0)
        isNull.resolve(asmArg);
        if (nextOSArgReg <= LAST_OS_PARAMETER_GPR) {
          asmArg.emitLVAL(nextOSArgReg, 0);
        } else {
          asmArg.emitSTAddr(srcreg, spillOffsetOS, FP);
        }

        // JOIN PATHS
        done.resolve(asmArg);
        
        //-#if RVM_FOR_LINUX
        if (nextOSArgReg <= LAST_OS_PARAMETER_GPR) {
          nextOSArgReg++;
        } else {
          spillOffsetOS += BYTES_IN_ADDRESS;
        }
        //-#endif
        
      } else {
        //-#if RVM_FOR_OSX
        spillSizeOSX = 4;
        //-#endif
        
        // For all other types: int, short, char, byte, boolean
        // (1a) fit in OS register, move the register
        if (nextOSArgReg<=LAST_OS_PARAMETER_GPR) {
          //-#if RVM_FOR_LINUX
          asmArg.emitMR(nextOSArgReg++, nextVMArgReg++);
          //-#else
          asmArg.emitMR(nextOSArgReg, nextVMArgReg++);
          //-#endif
        }
        // (1b) spill OS register, but still fit in VM register
        else if (nextVMArgReg<=LAST_VOLATILE_GPR) {
          asmArg.emitSTAddr(nextVMArgReg++, spillOffsetOS, FP);
          //-#if RVM_FOR_LINUX
          spillOffsetOS += BYTES_IN_ADDRESS;
          //-#endif
        } else {
          // (1c) spill VM register
          spillOffsetVM+=BYTES_IN_STACKSLOT;
          asmArg.emitLInt(REGISTER_ZERO, spillOffsetVM - BYTES_IN_INT, FP);        // retrieve arg from VM spill area
          asmArg.emitSTAddr(REGISTER_ZERO, spillOffsetOS, FP);

          //-#if RVM_FOR_LINUX
          spillOffsetOS+= BYTES_IN_ADDRESS;
          //-#endif
        }
      }

      //-#if RVM_FOR_OSX
      spillOffsetOS += spillSizeOSX;
      nextOSArgReg += nextOsxGprIncrement;
      //-#endif
    }

    // Append the code sequences for parameter mapping 
    // to the current machine code in reverse order
    // so that the move does not overwrite the parameters
    for (int arg = asmForArgs.length-1; arg >= 0; arg--) {
      VM_MachineCode codeForArg = asmForArgs[arg].makeMachineCode();
      asm.appendInstructions(codeForArg.getInstructions());
    }
  }
  //-#endif

  //-#if RVM_WITH_POWEROPEN_ABI
  /**
   * Generates instructions to copy parameters from RVM convention to OS convention.
   * @param asm   The VM_Assembler object
   * @param types The parameter types
   * @param nextVMArgReg  The first parameter GPR in RVM convention,
   *                      the last parameter GPR is defined as LAST_VOLATILE_GPR.
   * @param nextVMArgFloatReg  The first parameter FPR in RVM convention,
   *                           the last parameter FPR is defined as LAST_VOLATILE_FPR.
   * @param spillOffsetVM  The spill offset (related to FP) in RVM convention
   * @param nextOSArgReg  The first parameter GPR in OS convention,
   *                      the last parameter GPR is defined as LAST_OS_PARAMETER_GPR.
   * @param nextOSArgFloatReg  The first parameter FPR in OS convention,
   *                           the last parameter FPR is defined as LAST_OS_PARAMETER_FPR.
   * @param spillOffsetOS  The spill offset (related to FP) in OS convention
   */   
  private static void generateParameterPassingCode(VM_Assembler asm,
                                                   VM_TypeReference[] types,
                                                   int nextVMArgReg,
                                                   int nextVMArgFloatReg,
                                                   int spillOffsetVM,
                                                   int nextOSArgReg,
                                                   int nextOSArgFloatReg,
                                                   int spillOffsetOS) {
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
    int numArguments = types.length;
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
        if (nextOSArgReg<=LAST_OS_PARAMETER_GPR) {
          nextOSArgReg++;
          mustSaveFloatToSpill = false;
        } else {
          // (1b) if GPR has spilled, store the float argument in the callee spill area
          // regardless of whether the FPR has spilled or not
          mustSaveFloatToSpill = true;
        }
        
        spillOffsetOS+=BYTES_IN_STACKSLOT;
        // Check if the args need to be moved
        // (2a) leave those in FPR[1:13] as is unless the GPR has spilled
        if (nextVMArgFloatReg<=LAST_OS_PARAMETER_FPR) {
          if (mustSaveFloatToSpill) {
            asmArg.emitSTFS(nextVMArgFloatReg, spillOffsetOS - BYTES_IN_FLOAT, FP); 
          }
          nextOSArgFloatReg++;
          nextVMArgFloatReg++;  
        } else if (nextVMArgFloatReg<=LAST_VOLATILE_FPR) {
          // (2b) run out of FPR in OS, but still have 2 more FPR in VM,
          // so FPR[14:15] goes to the callee spill area
          asmArg.emitSTFS(nextVMArgFloatReg, spillOffsetOS - BYTES_IN_FLOAT, FP);
          nextVMArgFloatReg++;
        } else {
          // (2c) run out of FPR in VM, now get the remaining args from the caller spill area 
          // and move them into the callee spill area
          //Kris Venstermans: Attention, different calling convention !!
          spillOffsetVM+=BYTES_IN_STACKSLOT;
          asmArg.emitLFS(FIRST_SCRATCH_FPR, spillOffsetVM - BYTES_IN_FLOAT, FP);
          asmArg.emitSTFS(FIRST_SCRATCH_FPR, spillOffsetOS - BYTES_IN_FLOAT, FP);
        }
      } else if (types[arg].isDoubleType()) {
        // For 64-bit float arguments 
        if (VM.BuildFor64Addr) {
          // Side effect of float arguments on the GPR's
          // (1a) reserve one GPR for double
          if (nextOSArgReg<=LAST_OS_PARAMETER_GPR) {
            nextOSArgReg++;
            mustSaveFloatToSpill = false;
          } else {
            // (1b) if GPR has spilled, store the float argument in the callee spill area
            // regardless of whether the FPR has spilled or not
            mustSaveFloatToSpill = true;
          }

        } else {
          // Side effect of float arguments on the GPR's
          // (1a) reserve two GPR's for double
          if (nextOSArgReg<=LAST_OS_PARAMETER_GPR-1) {
            nextOSArgReg+=2;
            mustSaveFloatToSpill = false;
          } else {
            // if only one GPR is left, reserve it anyway although it won't be used
            if (nextOSArgReg<=LAST_OS_PARAMETER_GPR)
              nextOSArgReg++;
            mustSaveFloatToSpill = true;
          }
        }

        spillOffsetOS+=BYTES_IN_DOUBLE; //Kris Venstermans: equals 2 slots on 32-bit platforms and 1 slot on 64-bit platform
        // Check if the args need to be moved
        // (2a) leave those in FPR[1:13] as is unless the GPR has spilled
        if (nextVMArgFloatReg<=LAST_OS_PARAMETER_FPR) {
          if (mustSaveFloatToSpill) {
            asmArg.emitSTFD(nextVMArgFloatReg, spillOffsetOS - BYTES_IN_DOUBLE, FP); 
          }
          nextOSArgFloatReg++;
          nextVMArgFloatReg++;  
        } else if (nextVMArgFloatReg<=LAST_VOLATILE_FPR) {
          // (2b) run out of FPR in OS, but still have 2 more FPR in VM,
          // so FPR[14:15] goes to the callee spill area
          asmArg.emitSTFD(nextVMArgFloatReg, spillOffsetOS - BYTES_IN_DOUBLE, FP);
          nextVMArgFloatReg++;
        } else {
          // (2c) run out of FPR in VM, now get the remaining args from the caller spill area 
          // and move them into the callee spill area
          spillOffsetVM+=BYTES_IN_DOUBLE;
          asmArg.emitLFD(FIRST_SCRATCH_FPR, spillOffsetVM - BYTES_IN_DOUBLE, FP);
          asmArg.emitSTFD(FIRST_SCRATCH_FPR, spillOffsetOS - BYTES_IN_DOUBLE, FP);
        }
      } else if (VM.BuildFor32Addr && types[arg].isLongType()) {
        // For 64-bit int arguments on 32-bit platforms
        //
        spillOffsetOS+=BYTES_IN_LONG;
        // (1a) fit in OS register, move the pair
        if (nextOSArgReg<=LAST_OS_PARAMETER_GPR-1) {
          asmArg.emitMR(nextOSArgReg+1, nextVMArgReg+1);  // move lo-word first
          asmArg.emitMR(nextOSArgReg, nextVMArgReg);      // so it doesn't overwritten
          nextOSArgReg+=2;
          nextVMArgReg+=2;
        } else if (nextOSArgReg==LAST_OS_PARAMETER_GPR &&
          nextVMArgReg<=LAST_VOLATILE_GPR-1) {
          // (1b) fit in VM register but straddle across OS register/spill
          asmArg.emitSTW(nextVMArgReg+1, spillOffsetOS - BYTES_IN_STACKSLOT, FP);   // move lo-word first, so it doesn't overwritten
          asmArg.emitMR(nextOSArgReg, nextVMArgReg);
          nextOSArgReg+=2;
          nextVMArgReg+=2;        
        } else if (nextOSArgReg>LAST_OS_PARAMETER_GPR &&
                   nextVMArgReg<=LAST_VOLATILE_GPR-1) {
          // (1c) fit in VM register, spill in OS without straddling register/spill
          asmArg.emitSTW(nextVMArgReg++, spillOffsetOS - 2*BYTES_IN_STACKSLOT, FP);
          asmArg.emitSTW(nextVMArgReg++, spillOffsetOS - BYTES_IN_STACKSLOT, FP);
        } else if (nextVMArgReg==LAST_VOLATILE_GPR) {
          // (1d) split across VM/spill, spill in OS
          spillOffsetVM+=BYTES_IN_STACKSLOT;
          asmArg.emitSTW(nextVMArgReg++, spillOffsetOS - 2*BYTES_IN_STACKSLOT, FP);
          asmArg.emitLWZ(REGISTER_ZERO, spillOffsetVM - BYTES_IN_STACKSLOT, FP);
          asmArg.emitSTW(REGISTER_ZERO, spillOffsetOS - BYTES_IN_STACKSLOT, FP);
        } else {
          // (1e) spill both in VM and OS
          spillOffsetVM+=BYTES_IN_LONG;
          asmArg.emitLFD(FIRST_SCRATCH_FPR, spillOffsetVM - BYTES_IN_LONG, FP);
          asmArg.emitSTFD(FIRST_SCRATCH_FPR, spillOffsetOS - BYTES_IN_LONG, FP);
        }
      } else if (VM.BuildFor64Addr && types[arg].isLongType()) {
        // For 64-bit int arguments on 64-bit platforms
        //
        spillOffsetOS+=BYTES_IN_LONG;
        // (1a) fit in OS register, move the register
        if (nextOSArgReg<=LAST_OS_PARAMETER_GPR) {
          asmArg.emitMR(nextOSArgReg++, nextVMArgReg++);
        // (1b) spill OS register, but still fit in VM register
        } else if (nextVMArgReg<=LAST_VOLATILE_GPR) {
          asmArg.emitSTAddr(nextVMArgReg++, spillOffsetOS - BYTES_IN_LONG, FP);
        } else {
          // (1c) spill VM register
          spillOffsetVM+=BYTES_IN_LONG;
          asmArg.emitLAddr(REGISTER_ZERO,spillOffsetVM - BYTES_IN_LONG, FP);        // retrieve arg from VM spill area
          asmArg.emitSTAddr(REGISTER_ZERO, spillOffsetOS - BYTES_IN_LONG, FP);
        }
      } else if (types[arg].isReferenceType() ) {
        // For reference type, replace with handles before passing to OS
        //
        spillOffsetOS+=BYTES_IN_ADDRESS;
        
        // (1a) fit in OS register, move the register
        if (nextOSArgReg<=LAST_OS_PARAMETER_GPR) {
          // Are we passing NULL?
          asmArg.emitCMPI(nextVMArgReg, 0);
          VM_ForwardReference isNull = asmArg.emitForwardBC(EQ);
          // NO: put it in the JNIRefs array and pass offset
          asmArg.emitSTAddrU(nextVMArgReg, BYTES_IN_ADDRESS, KLUDGE_TI_REG);    // append ref to end of JNIRefs array
          asmArg.emitSUBFC(nextOSArgReg, PROCESSOR_REGISTER, KLUDGE_TI_REG);    // pass offset in bytes of jref
          VM_ForwardReference done = asmArg.emitForwardB();
          // YES: pass NULL (0)
          isNull.resolve(asmArg);
          asmArg.emitMR(nextOSArgReg, nextVMArgReg);
          // JOIN PATHS
          done.resolve(asmArg);
          nextVMArgReg++;
          nextOSArgReg++;
        } else if (nextVMArgReg<=LAST_VOLATILE_GPR) {
          // (1b) spill OS register, but still fit in VM register
          // Are we passing NULL?
          asmArg.emitCMPI(nextVMArgReg, 0);
          VM_ForwardReference isNull = asmArg.emitForwardBC(EQ);
          // NO: put it in the JNIRefs array and pass offset
          asmArg.emitSTAddrU(nextVMArgReg, BYTES_IN_ADDRESS, KLUDGE_TI_REG);    // append ref to end of JNIRefs array
          asmArg.emitSUBFC(REGISTER_ZERO, PROCESSOR_REGISTER, KLUDGE_TI_REG);     // compute offset in bytes for jref
          VM_ForwardReference done = asmArg.emitForwardB();
          // YES: pass NULL (0)
          isNull.resolve(asmArg);
          asmArg.emitLVAL(REGISTER_ZERO, 0);
          // JOIN PATHS
          done.resolve(asmArg);
          asmArg.emitSTAddr(REGISTER_ZERO, spillOffsetOS - BYTES_IN_ADDRESS, FP); // spill into OS frame
          nextVMArgReg++;
        } else {
          // (1c) spill VM register
          spillOffsetVM+=BYTES_IN_STACKSLOT;
          asmArg.emitLAddr(REGISTER_ZERO, spillOffsetVM - BYTES_IN_ADDRESS , FP); // retrieve arg from VM spill area
          // Are we passing NULL?
          asmArg.emitCMPI(REGISTER_ZERO, 0);
          VM_ForwardReference isNull = asmArg.emitForwardBC(EQ);
          // NO: put it in the JNIRefs array and pass offset
          asmArg.emitSTAddrU(REGISTER_ZERO, BYTES_IN_ADDRESS, KLUDGE_TI_REG);     // append ref to end of JNIRefs array
          asmArg.emitSUBFC(REGISTER_ZERO, PROCESSOR_REGISTER, KLUDGE_TI_REG);     // compute offset in bytes for jref
          VM_ForwardReference done = asmArg.emitForwardB();
          // YES: pass NULL (0)
          isNull.resolve(asmArg);
          asmArg.emitLVAL(REGISTER_ZERO, 0);
          // JOIN PATHS
          done.resolve(asmArg);
          asmArg.emitSTAddr(REGISTER_ZERO, spillOffsetOS - BYTES_IN_ADDRESS, FP); // spill into OS frame
        }
      } else { 
        // For all other types: int, short, char, byte, boolean 
        spillOffsetOS+=BYTES_IN_STACKSLOT;

        // (1a) fit in OS register, move the register
        if (nextOSArgReg<=LAST_OS_PARAMETER_GPR) {
          asmArg.emitMR(nextOSArgReg++, nextVMArgReg++);
        }

        // (1b) spill OS register, but still fit in VM register
        else if (nextVMArgReg<=LAST_VOLATILE_GPR) {
          asmArg.emitSTAddr(nextVMArgReg++, spillOffsetOS - BYTES_IN_ADDRESS, FP);
        } else {
          // (1c) spill VM register
          spillOffsetVM+=BYTES_IN_STACKSLOT;
          asmArg.emitLInt(REGISTER_ZERO,spillOffsetVM - BYTES_IN_INT, FP);        // retrieve arg from VM spill area
          asmArg.emitSTAddr(REGISTER_ZERO, spillOffsetOS - BYTES_IN_ADDRESS, FP);
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
  }
  //-#endif // RVM_WITH_POWEROPEN_ABI


  //-#if RVM_FOR_OSX
  static void generateReturnCodeForJNIMethod(VM_Assembler asm, VM_Method mth) {
    VM_TypeReference t = mth.getReturnType();
    if (VM.BuildFor32Addr && t.isLongType()) {
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
    boolean usesVarargs;
    int varargAmount = 0;
    
    String mthName = mth.getName().toString();
    if ((mthName.startsWith("Call") && mthName.endsWith("Method")) ||
        mthName.equals("NewObject"))
      usesVarargs = true;
    else
      usesVarargs = false;

    //-#if RVM_WITH_MACH_O_ABI
    // Find extra amount of space that needs to be added to the frame
    //to hold copies of the vararg values. This calculation is
    //overkill since some of these values will be in registers and
    //already stored. But then either 3 or 4 of the parameters don't
    //show up in the signature anyway (JNIEnvironment, class, method
    //id, instance object).
    if (usesVarargs) {
      VM_TypeReference[] argTypes = mth.getParameterTypes();
      int argCount = argTypes.length;
    
      for (int i=0; i<argCount; i++) {
        if (argTypes[i].isLongType() || argTypes[i].isDoubleType()) 
          varargAmount += 2 * BYTES_IN_ADDRESS;
        else
          varargAmount += BYTES_IN_ADDRESS;
      }
    }
    //-#endif
      
    int glueFrameSize = JNI_GLUE_FRAME_SIZE + varargAmount;
      
    asm.emitSTAddrU(FP,-glueFrameSize,FP);     // buy the glue frame

    // we may need to save CR in the previous frame also if CR will be used
    // CR is to be saved at FP+4 in the previous frame

    // Here we check if this is a JNI function that takes the vararg in the ... style
    // This includes CallStatic<type>Method, Call<type>Method, CallNonVirtual<type>Method
    // For these calls, the vararg starts at the 4th or 5th argument (GPR 6 or 7)
    // So, we save the GPR 6-10 and FPR 1-3 in a volatile register save area 
    // in the glue stack frame so that the JNI function can later repackage the arguments
    // based on the parameter types of target method to be invoked.
    // (For long argument lists, the additional arguments, have been saved in
    // the spill area of the OS caller, and will be retrieved from there.)
    //
    // If we are compiling such a JNI Function, then emit the code to store
    // GPR 4-10 and FPR 1-6 into the volatile save area.

    if (usesVarargs) {

      //-#if RVM_WITH_POWEROPEN_ABI
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
      //-#elif RVM_WITH_SVR4_ABI 
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
      //-#elif RVM_WITH_MACH_O_ABI
      // save all gpr parameter registers
      offset = STACKFRAME_HEADER_SIZE + 0;
      for (int i=FIRST_OS_PARAMETER_GPR; i<=LAST_OS_PARAMETER_GPR; i++) {
        asm.emitSTAddr(i, offset, FP);
        offset += BYTES_IN_ADDRESS;
      }
      //-#endif
    } else {
      //-#if RVM_WITH_SVR4_ABI || RVM_WITH_MACH_O_ABI
      // adjust register contents (following SVR4 ABI) for normal JNI functions
      // especially dealing with long, spills
      // number of parameters of normal JNI functions should fix in
      // r3 - r12, f1 - f15, + 24 words, 
      convertParametersFromSVR4ToJava(asm, mth);
      //-#endif
    }

    // Save AIX non-volatile GPRs that will not be saved and restored by RVM.
    //
    offset = STACKFRAME_HEADER_SIZE + JNI_GLUE_SAVED_VOL_SIZE;   // skip 20 word volatile reg save area
    for (int i = FIRST_RVM_RESERVED_NV_GPR; i <=LAST_RVM_RESERVED_NV_GPR; i++) {
      asm.emitSTAddr(i, offset, FP);
      offset += BYTES_IN_ADDRESS;
    }
 
    // set the method ID for the glue frame
    // and save the return address in the previous frame
    //
    asm.emitLVAL(S0, INVISIBLE_METHOD_ID);
    asm.emitMFLR(REGISTER_ZERO);
    asm.emitSTW(S0, STACKFRAME_METHOD_ID_OFFSET, FP);
    asm.emitSTAddr(REGISTER_ZERO, glueFrameSize + STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);

    // Attempt to change the vpStatus of the current Processor to IN_JAVA
    // 
    // on entry T0 = JNIEnv* which is an interior pointer to this thread's VM_JNIEnvironment.
    // We first adjust this in place to be a pointer to a VM_JNIEnvironment and then use
    // it to acquire PROCESSOR_REGISTER (and JTOC on OSX/Linux).
    //
    // AIX non volatile gprs 13-16 have been saved & are available (also gprs 11-13 can be used).
    // S0=13, S1=14, TI=15, PROCESSOR_REGISTER=16 are available (&have labels) for changing state.
    // we leave the passed arguments untouched, unless we are blocked and have to call sysVirtualProcessorYield

    // Map from JNIEnv* to VM_JNIEnvironment.
    // Must do this outside the loop as we need to do it exactly once.
    asm.emitADDI (T0, -VM_Entrypoints.JNIExternalFunctionsField.getOffsetAsInt(), T0);

    int retryLoop  = asm.getMachineCodeIndex();
    // acquire Jikes RVM PROCESSOR_REGISTER (and JTOC OSX/Linux only).
    asm.emitLAddrOffset(PROCESSOR_REGISTER, T0, VM_Entrypoints.JNIEnvSavedPRField.getOffset());
    //-#if RVM_WITH_SVR4_ABI || RVM_WITH_MACH_O_ABI
    // on AIX JTOC is part of AIX Linkage triplet and this already set by our caller.
    // Thus, we only need this load on non-AIX platforms
    asm.emitLAddrOffset(JTOC, T0, VM_Entrypoints.JNIEnvSavedJTOCField.getOffset());
    //-#endif

    asm.emitLVALAddr  (S1, VM_Entrypoints.vpStatusField.getOffset());
    asm.emitLWARX (S0, S1, PROCESSOR_REGISTER);               // get status for processor
    asm.emitCMPI  (S0, VM_Processor.BLOCKED_IN_NATIVE);       // check if GC in progress, blocked in native mode
    VM_ForwardReference frBlocked = asm.emitForwardBC(EQ);

    asm.emitLVAL  (S0,  VM_Processor.IN_JAVA);                // S0  <- new state value
    asm.emitSTWCXr(S0,  S1, PROCESSOR_REGISTER);              // attempt to change state to IN_JAVA
    asm.emitBC    (NE, retryLoop);                            // br if failure -retry lwarx by jumping to label0
    VM_ForwardReference frInJava = asm.emitForwardB();        // branch around code to call sysYield

    // branch to here if blocked in native, call sysVirtualProcessorYield (pthread yield)
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

    asm.emitLAddrToc(S1, VM_Entrypoints.the_boot_recordField.getOffset()); // get boot record address
    asm.emitMR   (PROCESSOR_REGISTER, JTOC);                                  // save JTOC so we can restore below
    asm.emitLAddrOffset(JTOC, S1, VM_Entrypoints.sysTOCField.getOffset());          // load TOC for syscalls from bootrecord
    asm.emitLAddrOffset(KLUDGE_TI_REG, S1, VM_Entrypoints.sysVirtualProcessorYieldIPField.getOffset());  // load addr of function
    asm.emitMTLR (KLUDGE_TI_REG);
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
    // JTOC, and PR are all as Jikes RVM expects them;
    // params are where the Jikes RVM calling conventions expects them.
    // 
    frInJava.resolve(asm);

    // get pointer to top java frame from JNIEnv, compute offset from current
    // frame pointer (offset to avoid more interior pointers) and save offset
    // in this glue frame
    //
    asm.emitLAddrOffset (S0, T0, VM_Entrypoints.JNITopJavaFPField.getOffset());       // get addr of top java frame from JNIEnv
    asm.emitSUBFC (S0, FP, S0);                                                 // S0 <- offset from current FP
    // AIX -4, LINUX - 8
    asm.emitSTW(S0, glueFrameSize + JNI_GLUE_OFFSET_TO_PREV_JFRAME, FP);  // store offset at end of glue frame

    // BRANCH TO THE PROLOG FOR THE JNI FUNCTION
    VM_ForwardReference frNormalPrologue = asm.emitForwardBL();

    // relative branch and link past the following epilog, to the normal prolog of the method
    // the normal epilog of the method will return to the epilog here to pop the glue stack frame

    // RETURN TO HERE FROM EPILOG OF JNI FUNCTION
    // CAUTION:  START OF EPILOG OF GLUE CODE
    // The section of code from here to "END OF EPILOG OF GLUE CODE" is nestled between
    // the glue code prolog and the real body of the JNI method.
    // T0 & T1 (R3 & R4) or F1 contain the return value from the function - DO NOT USE

    // assume: JTOC and PROCESSOR_REG are valid, and all RVM non-volatile 
    // GPRs and FPRs have been restored.  Our processor state will be  IN_JAVA.

    // establish T2 -> current thread's VM_JNIEnvironment, from activeThread field
    // of current processor      
    asm.emitLAddrOffset(T2, PROCESSOR_REGISTER, VM_Entrypoints.activeThreadField.getOffset());   // T2 <- activeThread of PR
    asm.emitLAddrOffset(T2, T2, VM_Entrypoints.jniEnvField.getOffset());                         // T2 <- JNIEnvironment 

    // before returning to C, set pointer to top java frame in JNIEnv, using offset
    // saved in this glue frame during transition from C to Java.  GC will use this saved
    // frame pointer if it is necessary to do GC with a processors active thread
    // stuck (and blocked) in native C, ie. GC starts scanning the threads stack at that frame.
    
    // AIX -4, LINUX -8
    asm.emitLInt (T3, glueFrameSize + JNI_GLUE_OFFSET_TO_PREV_JFRAME, FP); // load offset from FP to top java frame
    asm.emitADD  (T3, FP, T3);                                    // T3 <- address of top java frame
    asm.emitSTAddrOffset(T3, T2, VM_Entrypoints.JNITopJavaFPField.getOffset());     // store TopJavaFP back into JNIEnv

    // check to see if this frame address is the sentinel since there
    // may be no further Java frame below
    asm.emitCMPAddrI(T3, VM_Constants.STACKFRAME_SENTINEL_FP.toInt());
    VM_ForwardReference fr4 = asm.emitForwardBC(EQ);
    asm.emitLAddr(S0, 0, T3);                   // get fp for caller of prev J to C transition frame
    fr4.resolve(asm);

    // store current PR into VM_JNIEnvironment; we may have switched PRs while in Java mode.
    asm.emitSTAddrOffset(PROCESSOR_REGISTER, T2, VM_Entrypoints.JNIEnvSavedPRField.getOffset());

    // change the state of the VP to IN_NATIVE.
    //
    asm.emitLVAL (S0,  VM_Processor.IN_NATIVE);
    asm.emitSTWoffset  (S0, PROCESSOR_REGISTER, VM_Entrypoints.vpStatusField.getOffset());

    // Restore those AIX nonvolatile registers saved in the prolog above
    // Here we only save & restore ONLY those registers not restored by RVM
    //
    offset = STACKFRAME_HEADER_SIZE + JNI_GLUE_SAVED_VOL_SIZE;   // skip 20 word volatile reg save area
    for (int i = FIRST_RVM_RESERVED_NV_GPR; i <=LAST_RVM_RESERVED_NV_GPR; i++) {
      asm.emitLAddr (i, offset, FP);                     // 4 instructions
      offset += BYTES_IN_ADDRESS;
    }

    // pop frame
    asm.emitADDI(FP, glueFrameSize, FP);

    // load return address & return to caller
    // T0 & T1 (or F1) should still contain the return value
    //
    asm.emitLAddr(T2, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);
    asm.emitMTLR(T2);
    asm.emitBCLR (); // branch always, through link register

    // END OF EPILOG OF GLUE CODE; rest of method generated by VM_Compiler from bytecodes of method in VM_JNIFunctions
    frNormalPrologue.resolve(asm);
  } 

  //-#if RVM_WITH_SVR4_ABI || RVM_WITH_MACH_O_ABI
  // SVR4 rounds gprs to odd for longs, but rvm convention uses all
  // we only process JNI functions that uses parameters directly
  // so only handle parameters in gprs now
  static void convertParametersFromSVR4ToJava(VM_Assembler asm, VM_Method meth) {
    VM_TypeReference[] argTypes = meth.getParameterTypes();
    int argCount = argTypes.length;
    int nextVMReg = FIRST_VOLATILE_GPR;
    int nextOSReg = FIRST_OS_PARAMETER_GPR;
    
    for (int i=0; i<argCount; i++) {
      if (argTypes[i].isFloatType()) {
        // skip over
      } else if  (argTypes[i].isDoubleType()) {
        //-#if RVM_FOR_OSX
        nextOSReg ++;
        //-#elseif RVM_FOR_LINUX
        // skip over
        //-#endif
      } else {
        if (argTypes[i].isLongType() && VM.BuildFor32Addr) {
          //-#if RVM_FOR_LINUX
          nextOSReg += (nextOSReg + 1) & 0x01;  // round up to odd for linux
          //-#endif
          if (nextOSReg != nextVMReg) {
            asm.emitMR(nextVMReg, nextOSReg);
            asm.emitMR(nextVMReg + 1, nextOSReg +1);
          }
          nextOSReg += 2;
          nextVMReg += 2;
        } else {
          if (nextOSReg != nextVMReg) {
            asm.emitMR(nextVMReg, nextOSReg);
          }
          nextOSReg ++;
          nextVMReg ++;
        }
      }

      if (nextOSReg > LAST_OS_PARAMETER_GPR + 1) {
        VM.sysWrite("ERROR: "+meth+" has too many int or long parameters\n");
        VM.sysExit(VM.EXIT_STATUS_JNI_COMPILER_FAILED);
      }
    }
  }
  //-#endif RVM_WITH_SVR4_ABI || RVM_WITH_MACH_O_ABI
}
