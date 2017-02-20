/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.jni.ppc;

import static org.jikesrvm.compilers.common.assembler.ppc.AssemblerConstants.EQ;
import static org.jikesrvm.compilers.common.assembler.ppc.AssemblerConstants.LT;
import static org.jikesrvm.compilers.common.assembler.ppc.AssemblerConstants.NE;
import static org.jikesrvm.jni.ppc.JNIStackframeLayoutConstants.JNI_ENV_OFFSET;
import static org.jikesrvm.jni.ppc.JNIStackframeLayoutConstants.JNI_GC_FLAG_OFFSET;
import static org.jikesrvm.jni.ppc.JNIStackframeLayoutConstants.JNI_GLUE_FRAME_SIZE;
import static org.jikesrvm.jni.ppc.JNIStackframeLayoutConstants.JNI_GLUE_OFFSET_TO_PREV_JFRAME;
import static org.jikesrvm.jni.ppc.JNIStackframeLayoutConstants.JNI_GLUE_SAVED_VOL_SIZE;
import static org.jikesrvm.jni.ppc.JNIStackframeLayoutConstants.JNI_RVM_NONVOLATILE_OFFSET;
import static org.jikesrvm.jni.ppc.JNIStackframeLayoutConstants.JNI_SAVE_AREA_SIZE;
import static org.jikesrvm.jni.ppc.JNIStackframeLayoutConstants.NATIVE_FRAME_HEADER_SIZE;
import static org.jikesrvm.ppc.BaselineConstants.F3;
import static org.jikesrvm.ppc.BaselineConstants.FP;
import static org.jikesrvm.ppc.BaselineConstants.JTOC;
import static org.jikesrvm.ppc.BaselineConstants.S0;
import static org.jikesrvm.ppc.BaselineConstants.S1;
import static org.jikesrvm.ppc.BaselineConstants.T0;
import static org.jikesrvm.ppc.BaselineConstants.T1;
import static org.jikesrvm.ppc.BaselineConstants.T2;
import static org.jikesrvm.ppc.BaselineConstants.T3;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_NONVOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_OS_PARAMETER_FPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_OS_PARAMETER_GPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_RVM_RESERVED_NV_GPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_SCRATCH_FPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_VOLATILE_FPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_VOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.KLUDGE_TI_REG;
import static org.jikesrvm.ppc.RegisterConstants.LAST_NONVOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_OS_PARAMETER_FPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_OS_PARAMETER_GPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_OS_VARARG_PARAMETER_FPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_RVM_RESERVED_NV_GPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_SCRATCH_GPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_VOLATILE_FPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_VOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.REGISTER_ZERO;
import static org.jikesrvm.ppc.RegisterConstants.THREAD_REGISTER;
import static org.jikesrvm.ppc.StackframeLayoutConstants.BYTES_IN_STACKSLOT;
import static org.jikesrvm.ppc.StackframeLayoutConstants.INVISIBLE_METHOD_ID;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_ALIGNMENT;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_HEADER_SIZE;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_METHOD_ID_OFFSET;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_RETURN_ADDRESS_OFFSET;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP;
import static org.jikesrvm.runtime.ExitStatus.EXIT_STATUS_JNI_COMPILER_FAILED;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_DOUBLE;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_FLOAT;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_INT;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_LONG;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;
import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.NativeMethod;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.common.assembler.ForwardReference;
import org.jikesrvm.compilers.common.assembler.ppc.Assembler;
import org.jikesrvm.jni.JNICompiledMethod;
import org.jikesrvm.jni.JNIGlobalRefTable;
import org.jikesrvm.ppc.RegisterConstants.FPR;
import org.jikesrvm.ppc.RegisterConstants.GPR;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 *
 * TODO: This class is a disaster.
 *       Refactor into an abstract parent with subclasses for target ABIs.
 *       Problem: can't risk doing that until we get access to 32-Bit PPC
 *       for testing again, so we can actually test that the refactors
 *       are correct.
 */
public abstract class JNICompiler {

  /**
   * This method creates the stub to link native method.  It will be called
   * from the lazy linker the first time a native method is invoked.  The stub
   * generated will be patched by the lazy linker to link to the native method
   * for all future calls. <p>
   * <pre>
   * The stub performs the following tasks in the prologue:
   * <ol>
   *  <li>Allocate the glue frame
   *  <li>Save the TR and JTOC registers in the JNI Environment for reentering Java later
   *  <li>Shuffle the parameters in the registers to conform to the OS calling convention
   *  <li>Save the nonvolatile registers in a known space in the frame to be used
   *    for the GC stack map
   *  <li>Push a new JREF frame on the JNIRefs stack
   *  <li>Supply the first JNI argument:  the JNI environment pointer
   *  <li>Supply the second JNI argument:  class object if static, "this" if virtual
   * </ol>
   * <p>
   * The stub performs the following tasks in the epilogue:
   * <ol>
   *  <li>Restore TR and JTOC registers saved in JNI Environment
   *  <li>Restore the nonvolatile registers if GC has occurred
   *  <li>Pop the JREF frame off the JNIRefs stack
   *  <li>Check for pending exception and deliver to Java caller if present
   *  <li>Process the return value from native:  push onto caller's Java stack
   * </ol>
   * <p>
   * Within the stackframe, we have two frames.
   * The "main" frame exactly follows the OS native ABI and is therefore
   * different for each ABI.
   * The "mini-frame" is identical on all platforms and is stores RVM-specific fields.
   * The picture below shows the frames for 64-bit PowerPC ELF ABI.
   * <pre>
   *
   *   | fp       | <- native frame
   *   | cr       |
   *   | lr       |
   *   | resv     |
   *   | resv     |
   *   + toc      +
   *   |          |
   *   |          |
   *   |----------| <- Java to C glue frame using native calling conventions
   *   | fp       | saved fp of mini-frame
   *   | cr       |
   *   | lr       | native caller saves return address of native method here
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
   *   |----------| <- mini-frame for use by RVM stackwalkers
   *   |  fp      | saved fp of Java caller                 <- JNI_SAVE_AREA_OFFSET
   *   | mid      | cmid of native method
   *   | xxx (lr) | lr slot not used in mini frame
   *   |GC flag   | did GC happen while thread in native?   <- JNI_GC_FLAG_OFFSET
   *   |ENV       | JNIEnvironment                       <- JNI_ENV_OFFSET
   *   |RVM nonvol| save RVM nonvolatile GPRs for updating by GC stack mapper
   *   | ...      |
   *   |RVM nonvol|                                         <- JNI_RVM_NONVOLATILE_OFFSET
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
   * <p>
   * Runtime.unwindNativeStackFrame will return a pointer to the mini-frame
   * because none of our stack walkers need to do anything with the main frame.
   */
  public static synchronized CompiledMethod compile(NativeMethod method) {
    JNICompiledMethod cm = (JNICompiledMethod)CompiledMethods.createCompiledMethod(method, CompiledMethod.JNI);
    int compiledMethodId = cm.getId();
    Assembler asm = new Assembler(0);
    int frameSize = getFrameSize(method);
    RVMClass klass = method.getDeclaringClass();

    /* initialization */
    if (VM.VerifyAssertions) VM._assert(T3.value() <= LAST_VOLATILE_GPR.value()); // need 4 gp temps
    if (VM.VerifyAssertions) VM._assert(F3.value() <= LAST_VOLATILE_FPR.value()); // need 4 fp temps
    if (VM.VerifyAssertions) VM._assert(S0.value() < S1.value() &&
                                        S1.value() <= LAST_SCRATCH_GPR.value());  // need 2 scratch

    Address nativeIP = method.getNativeIP();
    Address nativeTOC = method.getNativeTOC();

    // NOTE:  this must be done before the condition Thread.hasNativeStackFrame() become true
    // so that the first Java to C transition will be allowed to resize the stack
    // (currently, this is true when the JNIRefsTop index has been incremented from 0)
    asm.emitNativeStackOverflowCheck(frameSize + 14);   // add at least 14 for C frame (header + spill)

    // save return address in caller frame
    asm.emitMFLR(REGISTER_ZERO);
    asm.emitSTAddr(REGISTER_ZERO, STACKFRAME_RETURN_ADDRESS_OFFSET.toInt(), FP);

    // buy mini frame
    asm.emitSTAddrU(FP, -JNI_SAVE_AREA_SIZE, FP);

    // store CMID for native method in mini-frame
    asm.emitLVAL(S0, compiledMethodId);
    asm.emitSTW(S0, STACKFRAME_METHOD_ID_OFFSET.toInt(), FP);

    // buy main frame, the total size equals to frameSize
    asm.emitSTAddrU(FP, -frameSize + JNI_SAVE_AREA_SIZE, FP);

    // establish S0 -> threads JNIEnv structure
    asm.emitLAddrOffset(S0, THREAD_REGISTER, Entrypoints.jniEnvField.getOffset());

    // save the TR register in the JNIEnvironment object for possible calls back into Java
    asm.emitSTAddrOffset(THREAD_REGISTER, S0, Entrypoints.JNIEnvSavedTRField.getOffset());

    // save the JNIEnvironment in the stack frame so we can use it to acquire the TR
    // when we return from native code.
    asm.emitSTAddr(S0, frameSize - JNI_ENV_OFFSET, FP);  // save TR in frame

    // save mini-frame frame pointer in JNIEnv, JNITopJavaFP, which will be the frame
    // to start scanning this stack during GC, if top of stack is still executing in C
    asm.emitLAddr(THREAD_REGISTER, 0, FP);
    asm.emitSTAddrOffset(THREAD_REGISTER, S0, Entrypoints.JNITopJavaFPField.getOffset());

    // save the RVM nonvolatile GPRs, to be scanned by GC stack mapper
    for (int i = LAST_NONVOLATILE_GPR.value(), offset = JNI_RVM_NONVOLATILE_OFFSET;
         i >= FIRST_NONVOLATILE_GPR.value();
         --i, offset += BYTES_IN_STACKSLOT) {
      asm.emitSTAddr(GPR.lookup(i), frameSize - offset, FP);
    }

    // clear the GC flag on entry to native code
    asm.emitLVAL(THREAD_REGISTER, 0);          // use TR as scratch
    asm.emitSTW(THREAD_REGISTER, frameSize - JNI_GC_FLAG_OFFSET, FP);

    // generate the code to map the parameters to OS convention and add the
    // second parameter (either the "this" ptr or class if a static method).
    // The JNI Function ptr first parameter is set before making the call
    // by the out of line machine code we invoke below.
    // Opens a new frame in the JNIRefs table to register the references.
    // Assumes S0 set to JNIEnv, kills KLUDGE_TI_REG, S1 & THREAD_REGISTER
    // On return, S0 still contains JNIEnv
    storeParameters(asm, frameSize, method, klass);

    //
    // Load required JNI function ptr into first parameter reg (GPR3/T0)
    // This pointer is an interior pointer to the JNIEnvironment which is
    // currently in S0.
    //
    asm.emitADDI(T0, Entrypoints.JNIExternalFunctionsField.getOffset(), S0);

    //
    // change the status of the thread to IN_JNI
    //
    asm.emitLAddrOffset(THREAD_REGISTER, S0, Entrypoints.JNIEnvSavedTRField.getOffset());

    asm.emitLVALAddr(S1, Entrypoints.execStatusField.getOffset());
    asm.emitLWARX(S0, S1, THREAD_REGISTER);         // get status for thread
    asm.emitCMPI(S0, RVMThread.IN_JAVA + (RVMThread.ALWAYS_LOCK_ON_STATE_TRANSITION ? 100 : 0));            // we should be in java code?
    ForwardReference notInJava = asm.emitForwardBC(NE);
    asm.emitLVAL(S0, RVMThread.IN_JNI);             // S0  <- new state value
    asm.emitSTWCXr(S0, S1, THREAD_REGISTER);        // attempt to change state to IN_JNI
    ForwardReference enteredJNIRef = asm.emitForwardBC(EQ); // branch if success over slow path

    notInJava.resolve(asm);

    asm.emitLAddrOffset(S0, THREAD_REGISTER, Entrypoints.threadContextRegistersField.getOffset());
    asm.emitLAddrOffset(S1, JTOC, ArchEntrypoints.saveVolatilesInstructionsField.getOffset());
    asm.emitMTLR(S1);
    asm.emitBCLRL();

    // NOTE: THREAD_REGISTER should still have the thread
    // pointer, since up to this point we would have saved it but not
    // overwritten it.
    // call into our friendly slow path function.  note that this should
    // work because:
    // 1) we're not calling from C so we don't care what registers are
    //    considered non-volatile in C
    // 2) all Java non-volatiles have been saved
    // 3) the only other registers we need - TR and S0 are taken care
    //    of (see above)
    // 4) the prologue and epilogue will take care of the frame pointer
    //    accordingly (it will just save it on the stack and then restore
    //    it - so we don't even have to know what its value is here)
    // the only thing we have to make sure of is that MMTk ignores the
    // framePointer field in RVMThread and uses the one in the JNI
    // environment instead (see Collection.prepareMutator)...
    asm.emitLAddrOffset(S1, JTOC, Entrypoints.enterJNIBlockedFromCallIntoNativeMethod.getOffset()); // T1 gets address of function
    asm.emitMTLR(S1);
    asm.emitBCLRL();   // call RVMThread.enterJNIBlocked

    asm.emitLAddrOffset(S0, THREAD_REGISTER, Entrypoints.threadContextRegistersField.getOffset());
    asm.emitLAddrOffset(S1, JTOC, ArchEntrypoints.restoreVolatilesInstructionsField.getOffset());
    asm.emitMTLR(S1);
    asm.emitBCLRL();

    // come here when we're done
    enteredJNIRef.resolve(asm);

    // set the TOC and IP for branch to out_of_line code
    asm.emitLVALAddr(JTOC, nativeTOC);
    asm.emitLVALAddr(S1, nativeIP);
    // move native code address to CTR reg;
    // do this early so that S1 will be available as a scratch.
    asm.emitMTCTR(S1);

    //
    // CALL NATIVE METHOD
    //
    asm.emitBCCTRL();

    // save the return value in R3-R4 in the glue frame spill area since they may be overwritten
    // if we have to call sysVirtualProcessorYield because we are locked in native.
    if (VM.BuildFor64Addr) {
      asm.emitSTD(T0, NATIVE_FRAME_HEADER_SIZE, FP);
    } else {
      asm.emitSTW(T0, NATIVE_FRAME_HEADER_SIZE, FP);
      asm.emitSTW(T1, NATIVE_FRAME_HEADER_SIZE + BYTES_IN_ADDRESS, FP);
    }

    //
    // try to return thread status to IN_JAVA
    //
    int label1 = asm.getMachineCodeIndex();

    //TODO: we can do this directly from FP because we know framesize at compiletime
    //      (the same way we stored the JNI Env above)
    asm.emitLAddr(S0, 0, FP);           // get mini-frame
    asm.emitLAddr(S0, 0, S0);           // get Java caller FP
    asm.emitLAddr(THREAD_REGISTER, -JNI_ENV_OFFSET, S0);   // load JNIEnvironment into TR

    // Restore JTOC and TR
    asm.emitLAddrOffset(JTOC, THREAD_REGISTER, Entrypoints.JNIEnvSavedJTOCField.getOffset());
    asm.emitLAddrOffset(THREAD_REGISTER, THREAD_REGISTER, Entrypoints.JNIEnvSavedTRField.getOffset());
    asm.emitLVALAddr(S1, Entrypoints.execStatusField.getOffset());
    asm.emitLWARX(S0, S1, THREAD_REGISTER);     // get status for processor
    asm.emitCMPI(S0, RVMThread.IN_JNI + (RVMThread.ALWAYS_LOCK_ON_STATE_TRANSITION ? 100 : 0));         // are we IN_JNI code?
    ForwardReference blocked = asm.emitForwardBC(NE);

    asm.emitLVAL(S0, RVMThread.IN_JAVA);               // S0  <- new state value
    asm.emitSTWCXr(S0, S1, THREAD_REGISTER);           // attempt to change state to java
    ForwardReference fr = asm.emitForwardBC(EQ);       // branch over blocked call if state change successful

    blocked.resolve(asm);
    // if not IN_JNI call RVMThread.leaveJNIBlockedFromCallIntoNative
    asm.emitLAddrOffset(T1, JTOC, Entrypoints.leaveJNIBlockedFromCallIntoNativeMethod.getOffset()); // T1 gets address of function
    asm.emitMTLR(T1);
    asm.emitBCLRL();   // call RVMThread.leaveJNIBlockedFromCallIntoNative

    fr.resolve(asm);

    // check if GC has occurred, If GC did not occur, then
    // VM NON_VOLATILE regs were restored by OS and are valid.  If GC did occur
    // objects referenced by these restored regs may have moved, in this case we
    // restore the nonvolatile registers from our save area,
    // where any object references would have been relocated during GC.
    // use T2 as scratch (not needed any more on return from call)
    //
    asm.emitLWZ(T2, frameSize - JNI_GC_FLAG_OFFSET, FP);
    asm.emitCMPI(T2, 0);
    ForwardReference fr1 = asm.emitForwardBC(EQ);
    for (int i = LAST_NONVOLATILE_GPR.value(), offset = JNI_RVM_NONVOLATILE_OFFSET;
         i >= FIRST_NONVOLATILE_GPR.value();
         --i, offset += BYTES_IN_STACKSLOT) {
      asm.emitLAddr(GPR.lookup(i), frameSize - offset, FP);
    }
    fr1.resolve(asm);

    // reestablish S0 to hold pointer to JNIEnvironment
    asm.emitLAddrOffset(S0, THREAD_REGISTER, Entrypoints.jniEnvField.getOffset());

    // pop jrefs frame off the JNIRefs stack, "reopen" the previous top jref frame
    // use S1 as scratch, also use T2, T3 for scratch which are no longer needed
    asm.emitLAddrOffset(S1, S0, Entrypoints.JNIRefsField.getOffset());          // load base of JNIRefs array
    asm.emitLIntOffset(T2,
                       S0,
                       Entrypoints.JNIRefsSavedFPField.getOffset());   // get saved offset for JNIRefs frame ptr previously pushed onto JNIRefs array
    asm.emitADDI(T3, -BYTES_IN_STACKSLOT, T2);                                    // compute offset for new TOP
    asm.emitSTWoffset(T3, S0, Entrypoints.JNIRefsTopField.getOffset());       // store new offset for TOP into JNIEnv
    asm.emitLIntX(T2, S1, T2);                                    // retrieve the previous frame ptr
    asm.emitSTWoffset(T2,
                      S0,
                      Entrypoints.JNIRefsSavedFPField.getOffset());   // store new offset for JNIRefs frame ptr into JNIEnv

    // Restore the return value R3-R4 saved in the glue frame spill area before the migration
    if (VM.BuildFor64Addr) {
      asm.emitLD(T0, NATIVE_FRAME_HEADER_SIZE, FP);
    } else {
      asm.emitLWZ(T0, NATIVE_FRAME_HEADER_SIZE, FP);
      asm.emitLWZ(T1, NATIVE_FRAME_HEADER_SIZE + BYTES_IN_STACKSLOT, FP);
    }

    // if the the return type is a reference, the native C is returning a jref
    // which is a byte offset from the beginning of the threads JNIRefs stack/array
    // of the corresponding ref.  In this case, emit code to replace the returned
    // offset (in R3) with the ref from the JNIRefs array

    TypeReference returnType = method.getReturnType();
    if (returnType.isReferenceType()) {
      asm.emitCMPI(T0, 0);
      ForwardReference globalRef = asm.emitForwardBC(LT);

      // Local ref - load from JNIRefs
      asm.emitLAddrX(T0, S1, T0);         // S1 is still the base of the JNIRefs array
      ForwardReference afterGlobalRef = asm.emitForwardB();

      // Deal with global references
      globalRef.resolve(asm);
      asm.emitLVAL(T3, JNIGlobalRefTable.STRONG_REF_BIT);
      asm.emitAND(T1, T0, T3);
      asm.emitLAddrOffset(T2, JTOC, Entrypoints.JNIGlobalRefsField.getOffset());
      asm.emitCMPI(T1, 0);
      ForwardReference weakGlobalRef = asm.emitForwardBC(EQ);

      // Strong global references
      asm.emitNEG(T0, T0);
      asm.emitSLWI(T0, T0, LOG_BYTES_IN_ADDRESS);  // convert index to offset
      asm.emitLAddrX(T0, T2, T0);
      ForwardReference afterWeakGlobalRef = asm.emitForwardB();

      // Weak global references
      weakGlobalRef.resolve(asm);
      asm.emitOR(T0, T0, T3); // STRONG_REF_BIT
      asm.emitNEG(T0, T0);
      asm.emitSLWI(T0, T0, LOG_BYTES_IN_ADDRESS);  // convert index to offset
      asm.emitLAddrX(T0, T2, T0);
      asm.emitLAddrOffset(T0, T0, Entrypoints.referenceReferentField.getOffset());
      afterWeakGlobalRef.resolve(asm);
      afterGlobalRef.resolve(asm);
    }

    // pop the whole stack frame (main & mini), restore the Java caller frame
    asm.emitADDI(FP, +frameSize, FP);

    // C return value is already where caller expected it (T0/T1 or F0)
    // So, just restore the return address to the link register.

    asm.emitLAddr(REGISTER_ZERO, STACKFRAME_RETURN_ADDRESS_OFFSET.toInt(), FP);
    asm.emitMTLR(REGISTER_ZERO);                           // restore return address

    // CHECK EXCEPTION AND BRANCH TO ATHROW CODE OR RETURN NORMALLY

    asm.emitLIntOffset(T2, S0, Entrypoints.JNIHasPendingExceptionField.getOffset());
    asm.emitLVAL(T3, 0); // get a zero value to compare

    asm.emitCMP(T2, T3);
    ForwardReference fr3 = asm.emitForwardBC(NE);
    asm.emitBCLR();                             // if no pending exception, proceed to return to caller
    fr3.resolve(asm);

    asm.emitLAddrToc(T1, Entrypoints.jniThrowPendingException.getOffset()); // T1 gets address of function
    asm.emitMTCTR(T1);                         // point LR to the exception delivery code
    asm.emitBCCTR();                           // then branch to the exception delivery code, does not return

    cm.compileComplete(asm.getMachineCodes());
    return cm;
  }

  public static int getFrameSize(NativeMethod m) {
    // space for:
    //   -NATIVE header (LINUX 2 words)
    //   -parameters and 2 extra JNI parameters (jnienv + obj), minimum 8 words
    //   -JNI_SAVE_AREA_OFFSET; see JNIStackframeLayoutConstants
    int argSpace = BYTES_IN_STACKSLOT * (m.getParameterWords() + 2);
    if (argSpace < 32) {
      argSpace = 32;
    }
    int size = NATIVE_FRAME_HEADER_SIZE + argSpace + JNI_SAVE_AREA_SIZE;
    if (VM.BuildFor32Addr) {
      size = Memory.alignUp(size, STACKFRAME_ALIGNMENT);
    }
    return size;
  }

  /**
   * Map the arguments from RVM convention to OS convention,
   * and replace all references with indexes into JNIRefs array.
   * <p>
   * Assumption on entry:
   * <ul>
   *   <li>KLUDGE_TI_REG, THREAD_REGISTER and S1 are available for use as scratch register
   *   <li>the frame has been created, FP points to the new callee frame
   * </ul>
   * <p>
   * Also update the JNIRefs array
   */
  private static void storeParameters(Assembler asm, int frameSize, RVMMethod method, RVMClass klass) {

    int nextOSArgReg, nextOSArgFloatReg, nextVMArgReg, nextVMArgFloatReg;

    // offset to the spill area in the callee (OS frame):
    int spillOffsetOS;
    if (VM.BuildForPower64ELF_ABI) {
      // 1st spill = JNIEnv, 2nd spill = class
      spillOffsetOS = NATIVE_FRAME_HEADER_SIZE + 2 * BYTES_IN_STACKSLOT;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForSVR4ABI);
      spillOffsetOS = NATIVE_FRAME_HEADER_SIZE;
    }

    // offset to the spill area in the caller (RVM frame), relative to the callee's FP
    int spillOffsetVM = frameSize + STACKFRAME_HEADER_SIZE;

    // does NOT include implicit this or class ptr
    TypeReference[] types = method.getParameterTypes();

    // Set up the Reference table for GC
    // TR <- JREFS array base
    asm.emitLAddrOffset(THREAD_REGISTER, S0, Entrypoints.JNIRefsField.getOffset());
    // TI <- JREFS current top
    asm.emitLIntOffset(KLUDGE_TI_REG, S0, Entrypoints.JNIRefsTopField.getOffset());   // JREFS offset for current TOP
    asm.emitADD(KLUDGE_TI_REG, THREAD_REGISTER, KLUDGE_TI_REG);                // convert into address

    // TODO - count number of refs
    // TODO - emit overflow check for JNIRefs array

    // start a new JNIRefs frame on each transition from Java to native C
    // push current SavedFP ptr onto top of JNIRefs stack (use available S1 reg as a temp)
    // and make current TOP the new savedFP
    //
    asm.emitLIntOffset(S1, S0, Entrypoints.JNIRefsSavedFPField.getOffset());
    asm.emitSTWU(S1,
                 BYTES_IN_ADDRESS,
                 KLUDGE_TI_REG);                           // push prev frame ptr onto JNIRefs array
    asm.emitSUBFC(S1, THREAD_REGISTER, KLUDGE_TI_REG);          // compute offset for new TOP
    asm.emitSTWoffset(S1,
                      S0,
                      Entrypoints.JNIRefsSavedFPField.getOffset());  // save new TOP as new frame ptr in JNIEnv

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

    nextOSArgFloatReg = FIRST_OS_PARAMETER_FPR.value();
    nextVMArgFloatReg = FIRST_VOLATILE_FPR.value();
    nextOSArgReg = FIRST_OS_PARAMETER_GPR.value() + 2;   // 1st reg = JNIEnv, 2nd reg = class/this
    if (method.isStatic()) {
      nextVMArgReg = FIRST_VOLATILE_GPR.value();
    } else {
      nextVMArgReg = FIRST_VOLATILE_GPR.value() + 1; // 1st reg = this, to be processed separately
    }

    // The loop below assumes the following relationship:
    if (VM.VerifyAssertions) VM._assert(FIRST_OS_PARAMETER_FPR == FIRST_VOLATILE_FPR);
    if (VM.VerifyAssertions) VM._assert(LAST_OS_PARAMETER_FPR.value() <= LAST_VOLATILE_FPR.value());
    if (VM.VerifyAssertions) VM._assert(FIRST_OS_PARAMETER_GPR == FIRST_VOLATILE_GPR);
    if (VM.VerifyAssertions) VM._assert(LAST_OS_PARAMETER_GPR.value() <= LAST_VOLATILE_GPR.value());

    generateParameterPassingCode(asm,
                                 types,
                                 nextVMArgReg,
                                 nextVMArgFloatReg,
                                 spillOffsetVM,
                                 nextOSArgReg,
                                 nextOSArgFloatReg,
                                 spillOffsetOS);

    // Now add the 2 JNI parameters:  JNI environment and Class or "this" object

    // if static method, append ref for class, else append ref for "this"
    // and pass offset in JNIRefs array in r4 (as second arg to called native code)
    GPR SECOND_OS_PARAMETER_GPR = GPR.lookup(FIRST_OS_PARAMETER_GPR.value() + 1);
    if (method.isStatic()) {
      // ASSMPTION: JTOC saved above in JNIEnv is still valid,
      // used by following emitLAddrToc
      Offset klassOffset = Offset.fromIntSignExtend(Statics.findOrCreateObjectLiteral(klass.getClassForType()));
      asm.emitLAddrToc(SECOND_OS_PARAMETER_GPR, klassOffset);
      asm.emitSTAddrU(SECOND_OS_PARAMETER_GPR,
                      BYTES_IN_ADDRESS,
                      KLUDGE_TI_REG);                 // append class ptr to end of JNIRefs array
      asm.emitSUBFC(SECOND_OS_PARAMETER_GPR, THREAD_REGISTER, KLUDGE_TI_REG);  // pass offset in bytes
    } else {
      asm.emitSTAddrU(T0, BYTES_IN_ADDRESS, KLUDGE_TI_REG);                 // append this ptr to end of JNIRefs array
      asm.emitSUBFC(SECOND_OS_PARAMETER_GPR, THREAD_REGISTER, KLUDGE_TI_REG);  // pass offset in bytes
    }

    // store the new JNIRefs array TOP back into JNIEnv
    asm.emitSUBFC(KLUDGE_TI_REG, THREAD_REGISTER, KLUDGE_TI_REG);     // compute offset for the current TOP
    asm.emitSTWoffset(KLUDGE_TI_REG, S0, Entrypoints.JNIRefsTopField.getOffset());
  }

  /**
   * Generates instructions to copy parameters from RVM convention to OS convention.
   * @param asm         The {@link Assembler} object
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
  private static void generateParameterPassingCode(Assembler asm, TypeReference[] types, int nextVMArgReg,
                                                   int nextVMArgFloatReg, int spillOffsetVM, int nextOSArgReg,
                                                   int nextOSArgFloatReg, int spillOffsetOS) {
    // TODO: The callee methods are prime candidates for being moved to ABI-specific subclasses.
    if (VM.BuildForSVR4ABI) {
      genSVR4ParameterPassingCode(asm,
                                         types,
                                         nextVMArgReg,
                                         nextVMArgFloatReg,
                                         spillOffsetVM,
                                         nextOSArgReg,
                                         nextOSArgFloatReg,
                                         spillOffsetOS);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPower64ELF_ABI);
      gen64BitPowerPC_ELF_ParameterPassingCode(asm,
                                       types,
                                       nextVMArgReg,
                                       nextVMArgFloatReg,
                                       spillOffsetVM,
                                       nextOSArgReg,
                                       nextOSArgFloatReg,
                                       spillOffsetOS);
    }
  }

  /**
   * Generates instructions to copy parameters from RVM convention to OS convention.
   * @param asm         The {@link Assembler} object
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
  private static void genSVR4ParameterPassingCode(Assembler asm, TypeReference[] types, int nextVMArgReg,
                                                         int nextVMArgFloatReg, int spillOffsetVM, int nextOSArgReg,
                                                         int nextOSArgFloatReg, int spillOffsetOS) {
    if (VM.BuildForSVR4ABI) {
      // create one Assembler object for each argument
      // This is needed for the following reason:
      //   -2 new arguments are added in front for native methods, so the normal arguments
      //    need to be shifted down in addition to being moved
      //   -to avoid overwriting each other, the arguments must be copied in reverse order
      //   -the analysis for mapping however must be done in forward order
      //   -the moving/mapping for each argument may involve a sequence of 1-3 instructions
      //    which must be kept in the normal order
      // To solve this problem, the instructions for each argument is generated in its
      // own Assembler in the forward pass, then in the reverse pass, each Assembler
      // emist the instruction sequence and copies it into the main Assembler
      int numArguments = types.length;
      Assembler[] asmForArgs = new Assembler[numArguments];

      for (int arg = 0; arg < numArguments; arg++) {
        asmForArgs[arg] = new Assembler(0);
        Assembler asmArg = asmForArgs[arg];

        // For 32-bit float arguments, must be converted to
        // double
        //
        if (types[arg].isFloatingPointType()) {
          boolean is32bits = types[arg].isFloatType();

          // 1. check the source, the value will be in srcVMArg
          FPR srcVMArg; // scratch fpr
          if (nextVMArgFloatReg <= LAST_VOLATILE_FPR.value()) {
            srcVMArg = FPR.lookup(nextVMArgFloatReg);
            nextVMArgFloatReg++;
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
          if (nextOSArgFloatReg <= LAST_OS_PARAMETER_FPR.value()) {
            // leave it there
            nextOSArgFloatReg++;
          } else {
            if (VM.BuildForSVR4ABI) {
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
            }
          }
          // for 64-bit long arguments
        } else if (types[arg].isLongType() && VM.BuildFor32Addr) {
          // handle OS first
          boolean dstSpilling;
          int regOrSpilling = -1;  // it is register number or spilling offset
          // 1. check if Linux register > 9
          if (nextOSArgReg > (LAST_OS_PARAMETER_GPR.value() - 1)) {
            // goes to spilling area
            dstSpilling = true;

            if (VM.BuildForSVR4ABI) {
              /* NOTE: following adjustment is not stated in SVR4 ABI, but
               * was implemented in GCC.
               * -- Feng
               */
              nextOSArgReg = LAST_OS_PARAMETER_GPR.value() + 1;

              // do alignment and compute spilling offset
              spillOffsetOS = (spillOffsetOS + 7) & -8;
              regOrSpilling = spillOffsetOS;
              spillOffsetOS += BYTES_IN_LONG;
            }
          } else {
            // use registers
            dstSpilling = false;

            if (VM.BuildForSVR4ABI) {
              // rounds to odd
              nextOSArgReg += (nextOSArgReg + 1) & 0x01; // if gpr is even, gpr += 1
              regOrSpilling = nextOSArgReg;
              nextOSArgReg += 2;
            }
          }

          // handle RVM source
          if (nextVMArgReg < LAST_VOLATILE_GPR.value()) {
            // both parts in registers
            if (dstSpilling) {
              asmArg.emitSTW(GPR.lookup(nextVMArgReg + 1), regOrSpilling + 4, FP);

              if (VM.BuildForSVR4ABI) {
                asmArg.emitSTW(GPR.lookup(nextVMArgReg), regOrSpilling, FP);
              }
            } else {
              asmArg.emitMR(GPR.lookup(regOrSpilling + 1), GPR.lookup(nextVMArgReg + 1));
              asmArg.emitMR(GPR.lookup(regOrSpilling), GPR.lookup(nextVMArgReg));
            }
            // advance register counting, Linux register number
            // already advanced
            nextVMArgReg += 2;
          } else if (nextVMArgReg == LAST_VOLATILE_GPR.value()) {
            // VM striding
            if (dstSpilling) {
              asmArg.emitLWZ(REGISTER_ZERO, spillOffsetVM, FP);
              asmArg.emitSTW(REGISTER_ZERO, regOrSpilling + 4, FP);
              asmArg.emitSTW(GPR.lookup(nextVMArgReg), regOrSpilling, FP);
            } else {
              asmArg.emitLWZ(GPR.lookup(regOrSpilling + 1), spillOffsetVM, FP);
              asmArg.emitMR(GPR.lookup(regOrSpilling), GPR.lookup(nextVMArgReg));
            }
            // advance spillOffsetVM and nextVMArgReg
            nextVMArgReg++;
            spillOffsetVM += BYTES_IN_STACKSLOT;
          } else if (nextVMArgReg > LAST_VOLATILE_GPR.value()) {
            if (dstSpilling) {
              asmArg.emitLFD(FIRST_SCRATCH_FPR, spillOffsetVM, FP);
              asmArg.emitSTFD(FIRST_SCRATCH_FPR, regOrSpilling, FP);
            } else {
              // this shouldnot happen, VM spills, OS has registers
              asmArg.emitLWZ(GPR.lookup(regOrSpilling + 1), spillOffsetVM + 4, FP);
              asmArg.emitLWZ(GPR.lookup(regOrSpilling), spillOffsetVM, FP);
            }
            spillOffsetVM += BYTES_IN_LONG;
          }
        } else if (types[arg].isLongType() && VM.BuildFor64Addr) {

          // handle OS first
          boolean dstSpilling;
          int regOrSpilling = -1;  // it is register number or spilling offset
          // 1. check if Linux register > 9
          if (nextOSArgReg > LAST_OS_PARAMETER_GPR.value()) {
            // goes to spilling area
            dstSpilling = true;

            /* NOTE: following adjustment is not stated in SVR4 ABI, but
             * was implemented in GCC.
             * -- Feng
             */
            nextOSArgReg = LAST_OS_PARAMETER_GPR.value() + 1;

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
          if (nextVMArgReg <= LAST_VOLATILE_GPR.value()) {
            // both parts in registers
            if (dstSpilling) {
              asmArg.emitSTD(GPR.lookup(nextVMArgReg), regOrSpilling, FP);
            } else {
              asmArg.emitMR(GPR.lookup(regOrSpilling), GPR.lookup(nextVMArgReg));
            }
            // advance register counting, Linux register number
            // already advanced
            nextVMArgReg += 1;
          } else if (nextVMArgReg > LAST_VOLATILE_GPR.value()) {
            if (dstSpilling) {
              asmArg.emitLFD(FIRST_SCRATCH_FPR, spillOffsetVM, FP);
              asmArg.emitSTFD(FIRST_SCRATCH_FPR, regOrSpilling, FP);
            } else {
              // this shouldnot happen, VM spills, OS has registers;
              asmArg.emitLD(GPR.lookup(regOrSpilling), spillOffsetVM, FP);
            }
            spillOffsetVM += BYTES_IN_LONG;
          }
        } else if (types[arg].isReferenceType()) {
          // For reference type, replace with handles before passing to native
          GPR srcreg;
          if (nextVMArgReg <= LAST_VOLATILE_GPR.value()) {
            srcreg = GPR.lookup(nextVMArgReg++);
          } else {
            srcreg = REGISTER_ZERO;
            asmArg.emitLAddr(srcreg, spillOffsetVM, FP);
            spillOffsetVM += BYTES_IN_ADDRESS;
          }

          // Are we passing NULL?
          asmArg.emitCMPI(srcreg, 0);
          ForwardReference isNull = asmArg.emitForwardBC(EQ);

          // NO: put it in the JNIRefs array and pass offset
          asmArg.emitSTAddrU(srcreg, BYTES_IN_ADDRESS, KLUDGE_TI_REG);
          if (nextOSArgReg <= LAST_OS_PARAMETER_GPR.value()) {
            asmArg.emitSUBFC(GPR.lookup(nextOSArgReg), THREAD_REGISTER, KLUDGE_TI_REG);
          } else {
            asmArg.emitSUBFC(REGISTER_ZERO, THREAD_REGISTER, KLUDGE_TI_REG);
            asmArg.emitSTAddr(REGISTER_ZERO, spillOffsetOS, FP);
          }
          ForwardReference done = asmArg.emitForwardB();

          // YES: pass NULL (0)
          isNull.resolve(asmArg);
          if (nextOSArgReg <= LAST_OS_PARAMETER_GPR.value()) {
            asmArg.emitLVAL(GPR.lookup(nextOSArgReg), 0);
          } else {
            asmArg.emitSTAddr(srcreg, spillOffsetOS, FP);
          }

          // JOIN PATHS
          done.resolve(asmArg);

          if (VM.BuildForSVR4ABI) {
            if (nextOSArgReg <= LAST_OS_PARAMETER_GPR.value()) {
              nextOSArgReg++;
            } else {
              spillOffsetOS += BYTES_IN_ADDRESS;
            }
          }

        } else {
          // For all other types: int, short, char, byte, boolean
          // (1a) fit in OS register, move the register
          if (nextOSArgReg <= LAST_OS_PARAMETER_GPR.value()) {
            if (VM.BuildForSVR4ABI) {
              asmArg.emitMR(GPR.lookup(nextOSArgReg++), GPR.lookup(nextVMArgReg++));
            } else {
              asmArg.emitMR(GPR.lookup(nextOSArgReg), GPR.lookup(nextVMArgReg++));
            }
          } else if (nextVMArgReg <= LAST_VOLATILE_GPR.value()) {
            // (1b) spill OS register, but still fit in VM register
            asmArg.emitSTAddr(GPR.lookup(nextVMArgReg++), spillOffsetOS, FP);
            if (VM.BuildForSVR4ABI) {
              spillOffsetOS += BYTES_IN_ADDRESS;
            }
          } else {
            // (1c) spill VM register
            spillOffsetVM += BYTES_IN_STACKSLOT;
            asmArg.emitLInt(REGISTER_ZERO, spillOffsetVM - BYTES_IN_INT, FP);        // retrieve arg from VM spill area
            asmArg.emitSTAddr(REGISTER_ZERO, spillOffsetOS, FP);

            if (VM.BuildForSVR4ABI) {
              spillOffsetOS += BYTES_IN_ADDRESS;
            }
          }
        }

      }

      // Append the code sequences for parameter mapping
      // to the current machine code in reverse order
      // so that the move does not overwrite the parameters
      for (int arg = asmForArgs.length - 1; arg >= 0; arg--) {
        asm.appendInstructions(asmForArgs[arg].getMachineCodes());
      }
    }
  }

  /**
   * Generates instructions to copy parameters from RVM convention to OS convention.
   * @param asm   The Assembler object
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
  private static void gen64BitPowerPC_ELF_ParameterPassingCode(Assembler asm, TypeReference[] types, int nextVMArgReg,
                                                       int nextVMArgFloatReg, int spillOffsetVM, int nextOSArgReg,
                                                       int nextOSArgFloatReg, int spillOffsetOS) {
    if (VM.BuildForPower64ELF_ABI) {
      // create one Assembler object for each argument
      // This is needed for the following reason:
      //   -2 new arguments are added in front for native methods, so the normal arguments
      //    need to be shifted down in addition to being moved
      //   -to avoid overwriting each other, the arguments must be copied in reverse order
      //   -the analysis for mapping however must be done in forward order
      //   -the moving/mapping for each argument may involve a sequence of 1-3 instructions
      //    which must be kept in the normal order
      // To solve this problem, the instructions for each argument is generated in its
      // own Assembler in the forward pass, then in the reverse pass, each Assembler
      // emist the instruction sequence and copies it into the main Assembler
      int numArguments = types.length;
      Assembler[] asmForArgs = new Assembler[numArguments];

      for (int arg = 0; arg < numArguments; arg++) {
        boolean mustSaveFloatToSpill;
        asmForArgs[arg] = new Assembler(0);
        Assembler asmArg = asmForArgs[arg];

        // For 32-bit float arguments
        //
        if (types[arg].isFloatType()) {
          // Side effect of float arguments on the GPR's
          // (1a) reserve one GPR for each float if it is available
          if (nextOSArgReg <= LAST_OS_PARAMETER_GPR.value()) {
            nextOSArgReg++;
            mustSaveFloatToSpill = false;
          } else {
            // (1b) if GPR has spilled, store the float argument in the callee spill area
            // regardless of whether the FPR has spilled or not
            mustSaveFloatToSpill = true;
          }

          spillOffsetOS += BYTES_IN_STACKSLOT;
          // Check if the args need to be moved
          // (2a) leave those in FPR[1:13] as is unless the GPR has spilled
          if (nextVMArgFloatReg <= LAST_OS_PARAMETER_FPR.value()) {
            if (mustSaveFloatToSpill) {
              asmArg.emitSTFS(FPR.lookup(nextVMArgFloatReg), spillOffsetOS - BYTES_IN_FLOAT, FP);
            }
            nextOSArgFloatReg++;
            nextVMArgFloatReg++;
          } else if (nextVMArgFloatReg <= LAST_VOLATILE_FPR.value()) {
            // (2b) run out of FPR in OS, but still have 2 more FPR in VM,
            // so FPR[14:15] goes to the callee spill area
            asmArg.emitSTFS(FPR.lookup(nextVMArgFloatReg), spillOffsetOS - BYTES_IN_FLOAT, FP);
            nextVMArgFloatReg++;
          } else {
            // (2c) run out of FPR in VM, now get the remaining args from the caller spill area
            // and move them into the callee spill area
            //Kris Venstermans: Attention, different calling convention !!
            spillOffsetVM += BYTES_IN_STACKSLOT;
            asmArg.emitLFS(FIRST_SCRATCH_FPR, spillOffsetVM - BYTES_IN_FLOAT, FP);
            asmArg.emitSTFS(FIRST_SCRATCH_FPR, spillOffsetOS - BYTES_IN_FLOAT, FP);
          }
        } else if (types[arg].isDoubleType()) {
          // For 64-bit float arguments
          if (VM.BuildFor64Addr) {
            // Side effect of float arguments on the GPR's
            // (1a) reserve one GPR for double
            if (nextOSArgReg <= LAST_OS_PARAMETER_GPR.value()) {
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
            if (nextOSArgReg <= LAST_OS_PARAMETER_GPR.value() - 1) {
              nextOSArgReg += 2;
              mustSaveFloatToSpill = false;
            } else {
              // if only one GPR is left, reserve it anyway although it won't be used
              if (nextOSArgReg <= LAST_OS_PARAMETER_GPR.value()) {
                nextOSArgReg++;
              }
              mustSaveFloatToSpill = true;
            }
          }

          spillOffsetOS +=
              BYTES_IN_DOUBLE; //Kris Venstermans: equals 2 slots on 32-bit platforms and 1 slot on 64-bit platform
          // Check if the args need to be moved
          // (2a) leave those in FPR[1:13] as is unless the GPR has spilled
          if (nextVMArgFloatReg <= LAST_OS_PARAMETER_FPR.value()) {
            if (mustSaveFloatToSpill) {
              asmArg.emitSTFD(FPR.lookup(nextVMArgFloatReg), spillOffsetOS - BYTES_IN_DOUBLE, FP);
            }
            nextOSArgFloatReg++;
            nextVMArgFloatReg++;
          } else if (nextVMArgFloatReg <= LAST_VOLATILE_FPR.value()) {
            // (2b) run out of FPR in OS, but still have 2 more FPR in VM,
            // so FPR[14:15] goes to the callee spill area
            asmArg.emitSTFD(FPR.lookup(nextVMArgFloatReg), spillOffsetOS - BYTES_IN_DOUBLE, FP);
            nextVMArgFloatReg++;
          } else {
            // (2c) run out of FPR in VM, now get the remaining args from the caller spill area
            // and move them into the callee spill area
            spillOffsetVM += BYTES_IN_DOUBLE;
            asmArg.emitLFD(FIRST_SCRATCH_FPR, spillOffsetVM - BYTES_IN_DOUBLE, FP);
            asmArg.emitSTFD(FIRST_SCRATCH_FPR, spillOffsetOS - BYTES_IN_DOUBLE, FP);
          }
        } else if (VM.BuildFor32Addr && types[arg].isLongType()) {
          // For 64-bit int arguments on 32-bit platforms
          //
          spillOffsetOS += BYTES_IN_LONG;
          // (1a) fit in OS register, move the pair
          if (nextOSArgReg <= LAST_OS_PARAMETER_GPR.value() - 1) {
            asmArg.emitMR(GPR.lookup(nextOSArgReg + 1), GPR.lookup(nextVMArgReg + 1));  // move lo-word first
            asmArg.emitMR(GPR.lookup(nextOSArgReg), GPR.lookup(nextVMArgReg));      // so it doesn't overwritten
            nextOSArgReg += 2;
            nextVMArgReg += 2;
          } else if (nextOSArgReg == LAST_OS_PARAMETER_GPR.value() && nextVMArgReg <= LAST_VOLATILE_GPR.value() - 1) {
            // (1b) fit in VM register but straddle across OS register/spill
            asmArg.emitSTW(GPR.lookup(nextVMArgReg + 1),
                           spillOffsetOS - BYTES_IN_STACKSLOT,
                           FP);   // move lo-word first, so it doesn't overwritten
            asmArg.emitMR(GPR.lookup(nextOSArgReg), GPR.lookup(nextVMArgReg));
            nextOSArgReg += 2;
            nextVMArgReg += 2;
          } else if (nextOSArgReg > LAST_OS_PARAMETER_GPR.value() && nextVMArgReg <= LAST_VOLATILE_GPR.value() - 1) {
            // (1c) fit in VM register, spill in OS without straddling register/spill
            asmArg.emitSTW(GPR.lookup(nextVMArgReg++), spillOffsetOS - 2 * BYTES_IN_STACKSLOT, FP);
            asmArg.emitSTW(GPR.lookup(nextVMArgReg++), spillOffsetOS - BYTES_IN_STACKSLOT, FP);
          } else if (nextVMArgReg == LAST_VOLATILE_GPR.value()) {
            // (1d) split across VM/spill, spill in OS
            spillOffsetVM += BYTES_IN_STACKSLOT;
            asmArg.emitSTW(GPR.lookup(nextVMArgReg++), spillOffsetOS - 2 * BYTES_IN_STACKSLOT, FP);
            asmArg.emitLWZ(REGISTER_ZERO, spillOffsetVM - BYTES_IN_STACKSLOT, FP);
            asmArg.emitSTW(REGISTER_ZERO, spillOffsetOS - BYTES_IN_STACKSLOT, FP);
          } else {
            // (1e) spill both in VM and OS
            spillOffsetVM += BYTES_IN_LONG;
            asmArg.emitLFD(FIRST_SCRATCH_FPR, spillOffsetVM - BYTES_IN_LONG, FP);
            asmArg.emitSTFD(FIRST_SCRATCH_FPR, spillOffsetOS - BYTES_IN_LONG, FP);
          }
        } else if (VM.BuildFor64Addr && types[arg].isLongType()) {
          // For 64-bit int arguments on 64-bit platforms
          //
          spillOffsetOS += BYTES_IN_LONG;
          // (1a) fit in OS register, move the register
          if (nextOSArgReg <= LAST_OS_PARAMETER_GPR.value()) {
            asmArg.emitMR(GPR.lookup(nextOSArgReg++), GPR.lookup(nextVMArgReg++));
            // (1b) spill OS register, but still fit in VM register
          } else if (nextVMArgReg <= LAST_VOLATILE_GPR.value()) {
            asmArg.emitSTAddr(GPR.lookup(nextVMArgReg++), spillOffsetOS - BYTES_IN_LONG, FP);
          } else {
            // (1c) spill VM register
            spillOffsetVM += BYTES_IN_LONG;
            asmArg.emitLAddr(REGISTER_ZERO,
                             spillOffsetVM - BYTES_IN_LONG,
                             FP);        // retrieve arg from VM spill area
            asmArg.emitSTAddr(REGISTER_ZERO, spillOffsetOS - BYTES_IN_LONG, FP);
          }
        } else if (types[arg].isReferenceType()) {
          // For reference type, replace with handles before passing to OS
          //
          spillOffsetOS += BYTES_IN_ADDRESS;

          // (1a) fit in OS register, move the register
          if (nextOSArgReg <= LAST_OS_PARAMETER_GPR.value()) {
            // Are we passing NULL?
            asmArg.emitCMPI(GPR.lookup(nextVMArgReg), 0);
            ForwardReference isNull = asmArg.emitForwardBC(EQ);
            // NO: put it in the JNIRefs array and pass offset
            asmArg.emitSTAddrU(GPR.lookup(nextVMArgReg), BYTES_IN_ADDRESS, KLUDGE_TI_REG);    // append ref to end of JNIRefs array
            asmArg.emitSUBFC(GPR.lookup(nextOSArgReg), THREAD_REGISTER, KLUDGE_TI_REG);    // pass offset in bytes of jref
            ForwardReference done = asmArg.emitForwardB();
            // YES: pass NULL (0)
            isNull.resolve(asmArg);
            asmArg.emitMR(GPR.lookup(nextOSArgReg), GPR.lookup(nextVMArgReg));
            // JOIN PATHS
            done.resolve(asmArg);
            nextVMArgReg++;
            nextOSArgReg++;
          } else if (nextVMArgReg <= LAST_VOLATILE_GPR.value()) {
            // (1b) spill OS register, but still fit in VM register
            // Are we passing NULL?
            asmArg.emitCMPI(GPR.lookup(nextVMArgReg), 0);
            ForwardReference isNull = asmArg.emitForwardBC(EQ);
            // NO: put it in the JNIRefs array and pass offset
            asmArg.emitSTAddrU(GPR.lookup(nextVMArgReg), BYTES_IN_ADDRESS, KLUDGE_TI_REG);    // append ref to end of JNIRefs array
            asmArg.emitSUBFC(REGISTER_ZERO, THREAD_REGISTER, KLUDGE_TI_REG);     // compute offset in bytes for jref
            ForwardReference done = asmArg.emitForwardB();
            // YES: pass NULL (0)
            isNull.resolve(asmArg);
            asmArg.emitLVAL(REGISTER_ZERO, 0);
            // JOIN PATHS
            done.resolve(asmArg);
            asmArg.emitSTAddr(REGISTER_ZERO, spillOffsetOS - BYTES_IN_ADDRESS, FP); // spill into OS frame
            nextVMArgReg++;
          } else {
            // (1c) spill VM register
            spillOffsetVM += BYTES_IN_STACKSLOT;
            asmArg.emitLAddr(REGISTER_ZERO, spillOffsetVM - BYTES_IN_ADDRESS, FP); // retrieve arg from VM spill area
            // Are we passing NULL?
            asmArg.emitCMPI(REGISTER_ZERO, 0);
            ForwardReference isNull = asmArg.emitForwardBC(EQ);
            // NO: put it in the JNIRefs array and pass offset
            asmArg.emitSTAddrU(REGISTER_ZERO,
                               BYTES_IN_ADDRESS,
                               KLUDGE_TI_REG);     // append ref to end of JNIRefs array
            asmArg.emitSUBFC(REGISTER_ZERO, THREAD_REGISTER, KLUDGE_TI_REG);     // compute offset in bytes for jref
            ForwardReference done = asmArg.emitForwardB();
            // YES: pass NULL (0)
            isNull.resolve(asmArg);
            asmArg.emitLVAL(REGISTER_ZERO, 0);
            // JOIN PATHS
            done.resolve(asmArg);
            asmArg.emitSTAddr(REGISTER_ZERO, spillOffsetOS - BYTES_IN_ADDRESS, FP); // spill into OS frame
          }
        } else {
          // For all other types: int, short, char, byte, boolean
          spillOffsetOS += BYTES_IN_STACKSLOT;

          // (1a) fit in OS register, move the register
          if (nextOSArgReg <= LAST_OS_PARAMETER_GPR.value()) {
            asmArg.emitMR(GPR.lookup(nextOSArgReg++), GPR.lookup(nextVMArgReg++));
          } else if (nextVMArgReg <= LAST_VOLATILE_GPR.value()) {
            // (1b) spill OS register, but still fit in VM register
            asmArg.emitSTAddr(GPR.lookup(nextVMArgReg++), spillOffsetOS - BYTES_IN_ADDRESS, FP);
          } else {
            // (1c) spill VM register
            spillOffsetVM += BYTES_IN_STACKSLOT;
            asmArg.emitLInt(REGISTER_ZERO, spillOffsetVM - BYTES_IN_INT, FP);        // retrieve arg from VM spill area
            asmArg.emitSTAddr(REGISTER_ZERO, spillOffsetOS - BYTES_IN_ADDRESS, FP);
          }
        }
      }

      // Append the code sequences for parameter mapping
      // to the current machine code in reverse order
      // so that the move does not overwrite the parameters
      for (int arg = numArguments - 1; arg >= 0; arg--) {
        asm.appendInstructions(asmForArgs[arg].getMachineCodes());
      }
    }
  }

  /**
   * Emit code to do the C to Java transition:  JNI methods in JNIFunctions.java
   */
  public static void generateGlueCodeForJNIMethod(Assembler asm, RVMMethod mth) {
    int offset;
    int varargAmount = 0;

    String mthName = mth.getName().toString();
    final boolean usesVarargs =
        (mthName.startsWith("Call") && mthName.endsWith("Method")) || mthName.equals("NewObject");

    int glueFrameSize = JNI_GLUE_FRAME_SIZE + varargAmount;

    asm.emitSTAddrU(FP, -glueFrameSize, FP);     // buy the glue frame

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
      if (VM.BuildForPower64ELF_ABI) {
        offset = STACKFRAME_HEADER_SIZE + 3 * BYTES_IN_STACKSLOT;   // skip over slots for GPR 3-5
        for (int i = 6; i <= 10; i++) {
          asm.emitSTAddr(GPR.lookup(i), offset, FP);
          offset += BYTES_IN_ADDRESS;
        }
        // store FPRs 1-3 in first 3 slots of volatile FPR save area
        for (int i = 1; i <= 3; i++) {
          asm.emitSTFD(FPR.lookup(i), offset, FP);
          offset += BYTES_IN_DOUBLE;
        }
      } else if (VM.BuildForSVR4ABI) {
        // save all parameter registers
        offset = STACKFRAME_HEADER_SIZE + 0;
        for (int i = FIRST_OS_PARAMETER_GPR.value(); i <= LAST_OS_PARAMETER_GPR.value(); i++) {
          asm.emitSTAddr(GPR.lookup(i), offset, FP);
          offset += BYTES_IN_ADDRESS;
        }
        for (int i = FIRST_OS_PARAMETER_FPR.value(); i <= LAST_OS_PARAMETER_FPR.value(); i++) {
          asm.emitSTFD(FPR.lookup(i), offset, FP);
          offset += BYTES_IN_DOUBLE;
        }
      }
    } else {
      if (VM.BuildForSVR4ABI) {
        // adjust register contents (following SVR4 ABI) for normal JNI functions
        // especially dealing with long, spills
        // number of parameters of normal JNI functions should fix in
        // r3 - r12, f1 - f15, + 24 words,
        convertParametersFromSVR4ToJava(asm, mth);
      }
    }

    // Save non-volatile GPRs that will not be saved and restored by RVM.
    //
    offset = STACKFRAME_HEADER_SIZE + JNI_GLUE_SAVED_VOL_SIZE;   // skip 20 word volatile reg save area
    for (int i = FIRST_RVM_RESERVED_NV_GPR.value(); i <= LAST_RVM_RESERVED_NV_GPR.value(); i++) {
      asm.emitSTAddr(GPR.lookup(i), offset, FP);
      offset += BYTES_IN_ADDRESS;
    }

    // set the method ID for the glue frame
    // and save the return address in the previous frame
    //
    asm.emitLVAL(S0, INVISIBLE_METHOD_ID);
    asm.emitMFLR(REGISTER_ZERO);
    asm.emitSTW(S0, STACKFRAME_METHOD_ID_OFFSET.toInt(), FP);
    asm.emitSTAddr(REGISTER_ZERO, glueFrameSize + STACKFRAME_RETURN_ADDRESS_OFFSET.toInt(), FP);

    // Attempt to change the vpStatus of the current Processor to IN_JAVA
    //
    // on entry T0 = JNIEnv* which is an interior pointer to this thread's JNIEnvironment.
    // We first adjust this in place to be a pointer to a JNIEnvironment and then use
    // it to acquire THREAD_REGISTER (and JTOC on Linux).
    //
    // TODO update for AIX removal
    // AIX non volatile gprs 13-16 have been saved & are available (also gprs 11-13 can be used).
    // S0=13, S1=14, TI=15, THREAD_REGISTER=16 are available (&have labels) for changing state.
    // we leave the passed arguments untouched, unless we are blocked and have to call sysVirtualProcessorYield

    // Map from JNIEnv* to JNIEnvironment.
    // Must do this outside the loop as we need to do it exactly once.
    asm.emitADDI(T0, Offset.zero().minus(Entrypoints.JNIExternalFunctionsField.getOffset()), T0);

    int retryLoop = asm.getMachineCodeIndex();
    // acquire Jikes RVM THREAD_REGISTER (and JTOC Linux only).
    asm.emitLAddrOffset(THREAD_REGISTER, T0, Entrypoints.JNIEnvSavedTRField.getOffset());
    if (VM.BuildForSVR4ABI) {
      // When using the 64-bit PowerPC ELF ABI (e.g. on PPC64 Linux), the JTOC is part of
      // the Linkage triplet and this already set by our caller.
      // Thus, we only need this load when not on PPC64 Linux.
      asm.emitLAddrOffset(JTOC, T0, Entrypoints.JNIEnvSavedJTOCField.getOffset());
    }

    asm.emitLVALAddr(S1, Entrypoints.execStatusField.getOffset());
    asm.emitLWARX(S0, S1, THREAD_REGISTER);               // get status for processor
    asm.emitCMPI(S0, RVMThread.IN_JNI + (RVMThread.ALWAYS_LOCK_ON_STATE_TRANSITION ? 100 : 0));       // check if GC in progress, blocked in native mode
    ForwardReference frBlocked = asm.emitForwardBC(NE);

    asm.emitLVAL(S0, RVMThread.IN_JAVA);                // S0  <- new state value
    asm.emitSTWCXr(S0, S1, THREAD_REGISTER);              // attempt to change state to IN_JAVA
    asm.emitBC(NE, retryLoop);                            // br if failure -retry lwarx by jumping to label0
    ForwardReference frInJava = asm.emitForwardB();        // branch around code to call sysYield

    // branch to here if blocked in native, call leaveJNIBlocked
    // must save volatile gprs & fprs before the call and restore after
    //
    frBlocked.resolve(asm);
    offset = STACKFRAME_HEADER_SIZE;

    // save volatile GPRS 3-10
    for (int i = FIRST_OS_PARAMETER_GPR.value(); i <= LAST_OS_PARAMETER_GPR.value(); i++) {
      asm.emitSTAddr(GPR.lookup(i), offset, FP);
      offset += BYTES_IN_ADDRESS;
    }

    // save volatile FPRS 1-6
    for (int i = FIRST_OS_PARAMETER_FPR.value(); i <= LAST_OS_VARARG_PARAMETER_FPR.value(); i++) {
      asm.emitSTFD(FPR.lookup(i), offset, FP);
      offset += BYTES_IN_DOUBLE;
    }

    asm.emitLAddrOffset(KLUDGE_TI_REG,
                        JTOC,
                        Entrypoints.leaveJNIBlockedFromJNIFunctionCallMethod.getOffset());  // load addr of function
    asm.emitMTLR(KLUDGE_TI_REG);
    asm.emitBCLRL();                                                    // call RVMThread.leaveJNIBlockFromJNIFunction

    // restore the saved volatile GPRs 3-10 and FPRs 1-6
    offset = STACKFRAME_HEADER_SIZE;

    // restore volatile GPRS 3-10
    for (int i = FIRST_OS_PARAMETER_GPR.value(); i <= LAST_OS_PARAMETER_GPR.value(); i++) {
      asm.emitLAddr(GPR.lookup(i), offset, FP);
      offset += BYTES_IN_ADDRESS;
    }

    // restore volatile FPRS 1-6
    for (int i = FIRST_OS_PARAMETER_FPR.value(); i <= LAST_OS_VARARG_PARAMETER_FPR.value(); i++) {
      asm.emitLFD(FPR.lookup(i), offset, FP);
      offset += BYTES_IN_DOUBLE;
    }

    // NOW_IN_JAVA:
    // JTOC, and TR are all as Jikes RVM expects them;
    // params are where the Jikes RVM calling conventions expects them.
    //
    frInJava.resolve(asm);

    // get pointer to top java frame from JNIEnv, compute offset from current
    // frame pointer (offset to avoid more interior pointers) and save offset
    // in this glue frame
    //
    asm.emitLAddrOffset(S0,
                        T0,
                        Entrypoints.JNITopJavaFPField.getOffset());       // get addr of top java frame from JNIEnv
    asm.emitSUBFC(S0, FP, S0);                                                 // S0 <- offset from current FP
    // LINUX - 8
    asm.emitSTW(S0, glueFrameSize + JNI_GLUE_OFFSET_TO_PREV_JFRAME, FP);  // store offset at end of glue frame

    // BRANCH TO THE PROLOG FOR THE JNI FUNCTION
    ForwardReference frNormalPrologue = asm.emitForwardBL();

    // relative branch and link past the following epilog, to the normal prolog of the method
    // the normal epilog of the method will return to the epilog here to pop the glue stack frame

    // RETURN TO HERE FROM EPILOG OF JNI FUNCTION
    // CAUTION:  START OF EPILOG OF GLUE CODE
    // The section of code from here to "END OF EPILOG OF GLUE CODE" is nestled between
    // the glue code prolog and the real body of the JNI method.
    // T0 & T1 (R3 & R4) or F1 contain the return value from the function - DO NOT USE

    // assume: JTOC and THREAD_REG are valid, and all RVM non-volatile
    // GPRs and FPRs have been restored.  Our processor state will be  IN_JAVA.

    // establish T2 -> current thread's JNIEnvironment, from activeThread field
    // of current processor
    asm.emitLAddrOffset(T2, THREAD_REGISTER, Entrypoints.jniEnvField.getOffset());                         // T2 <- JNIEnvironment

    // before returning to C, set pointer to top java frame in JNIEnv, using offset
    // saved in this glue frame during transition from C to Java.  GC will use this saved
    // frame pointer if it is necessary to do GC with a processors active thread
    // stuck (and blocked) in native C, ie. GC starts scanning the threads stack at that frame.

    // LINUX -8
    asm.emitLInt(T3, glueFrameSize + JNI_GLUE_OFFSET_TO_PREV_JFRAME, FP); // load offset from FP to top java frame
    asm.emitADD(T3, FP, T3);                                    // T3 <- address of top java frame
    asm.emitSTAddrOffset(T3, T2, Entrypoints.JNITopJavaFPField.getOffset());     // store TopJavaFP back into JNIEnv

    // check to see if this frame address is the sentinel since there
    // may be no further Java frame below
    asm.emitCMPAddrI(T3, STACKFRAME_SENTINEL_FP.toInt());
    ForwardReference fr4 = asm.emitForwardBC(EQ);
    asm.emitLAddr(S0, 0, T3);                   // get fp for caller of prev J to C transition frame
    fr4.resolve(asm);

    // store current TR into JNIEnvironment; we may have switched TRs while in Java mode.
    asm.emitSTAddrOffset(THREAD_REGISTER, T2, Entrypoints.JNIEnvSavedTRField.getOffset());

    // change the state of the TR to IN_JNI
    //
    asm.emitLVALAddr(S1, Entrypoints.execStatusField.getOffset());
    asm.emitLWARX(S0, S1, THREAD_REGISTER);
    asm.emitCMPI(S0, RVMThread.IN_JAVA + (RVMThread.ALWAYS_LOCK_ON_STATE_TRANSITION ? 100 : 0));
    ForwardReference notInJava = asm.emitForwardBC(NE);
    asm.emitLVAL(S0, RVMThread.IN_JNI);
    asm.emitSTWCXr(S0, S1, THREAD_REGISTER);
    ForwardReference enteredJNIRef = asm.emitForwardBC(EQ);

    notInJava.resolve(asm);

    // NOTE: we save and restore volatiles here.  that's overkill.  we really
    // only need to save/restore the return registers (see above).  oh well.
    // if it works then I can't bring myself to care.

    asm.emitLAddrOffset(S0, THREAD_REGISTER, Entrypoints.threadContextRegistersField.getOffset());
    asm.emitLAddrOffset(S1, JTOC, ArchEntrypoints.saveVolatilesInstructionsField.getOffset());
    asm.emitMTLR(S1);
    asm.emitBCLRL();

    asm.emitLAddrOffset(S0, JTOC, Entrypoints.enterJNIBlockedFromJNIFunctionCallMethod.getOffset());
    asm.emitMTLR(S0);
    asm.emitBCLRL();

    asm.emitLAddrOffset(S0, THREAD_REGISTER, Entrypoints.threadContextRegistersField.getOffset());
    asm.emitLAddrOffset(S1, JTOC, ArchEntrypoints.restoreVolatilesInstructionsField.getOffset());
    asm.emitMTLR(S1);
    asm.emitBCLRL();

    enteredJNIRef.resolve(asm);

    // Restore the nonvolatile registers saved in the prolog above
    // Here we only save & restore ONLY those registers not restored by RVM
    //
    offset = STACKFRAME_HEADER_SIZE + JNI_GLUE_SAVED_VOL_SIZE;   // skip 20 word volatile reg save area
    for (int i = FIRST_RVM_RESERVED_NV_GPR.value(); i <= LAST_RVM_RESERVED_NV_GPR.value(); i++) {
      asm.emitLAddr(GPR.lookup(i), offset, FP);                     // 4 instructions
      offset += BYTES_IN_ADDRESS;
    }

    // pop frame
    asm.emitADDI(FP, glueFrameSize, FP);

    // load return address & return to caller
    // T0 & T1 (or F1) should still contain the return value
    //
    asm.emitLAddr(T2, STACKFRAME_RETURN_ADDRESS_OFFSET.toInt(), FP);
    asm.emitMTLR(T2);
    asm.emitBCLR(); // branch always, through link register

    // END OF EPILOG OF GLUE CODE; rest of method generated by Compiler from bytecodes of method in JNIFunctions
    frNormalPrologue.resolve(asm);
  }

  // SVR4 rounds gprs to odd for longs, but rvm convention uses all
  // we only process JNI functions that uses parameters directly
  // so only handle parameters in gprs now
  static void convertParametersFromSVR4ToJava(Assembler asm, RVMMethod meth) {
    if (VM.BuildForSVR4ABI) {
      TypeReference[] argTypes = meth.getParameterTypes();
      int argCount = argTypes.length;
      int nextVMReg = FIRST_VOLATILE_GPR.value();
      int nextOSReg = FIRST_OS_PARAMETER_GPR.value();

      for (int i = 0; i < argCount; i++) {
        if (argTypes[i].isFloatType()) {
          // skip over
        } else if (argTypes[i].isDoubleType()) {
          // skip over
        } else {
          if (argTypes[i].isLongType() && VM.BuildFor32Addr) {
            if (VM.BuildForSVR4ABI) {
              nextOSReg += (nextOSReg + 1) & 0x01;  // round up to odd for linux
            }
            if (nextOSReg != nextVMReg) {
              asm.emitMR(GPR.lookup(nextVMReg), GPR.lookup(nextOSReg));
              asm.emitMR(GPR.lookup(nextVMReg + 1), GPR.lookup(nextOSReg + 1));
            }
            nextOSReg += 2;
            nextVMReg += 2;
          } else {
            if (nextOSReg != nextVMReg) {
              asm.emitMR(GPR.lookup(nextVMReg), GPR.lookup(nextOSReg));
            }
            nextOSReg++;
            nextVMReg++;
          }
        }

        if (nextOSReg > LAST_OS_PARAMETER_GPR.value() + 1) {
          VM.sysWriteln("ERROR: " + meth + " has too many int or long parameters");
          VM.sysExit(EXIT_STATUS_JNI_COMPILER_FAILED);
        }
      }
    }
  }
}
