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
package org.jikesrvm.jni.arm;


import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_INT;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;
import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;

import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.ALWAYS;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.GE;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.EQ;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.NE;
import static org.jikesrvm.arm.BaselineConstants.T0;
import static org.jikesrvm.arm.BaselineConstants.T1;
import static org.jikesrvm.arm.BaselineConstants.T2;
import static org.jikesrvm.arm.BaselineConstants.T3;
import static org.jikesrvm.arm.BaselineConstants.F0;
import static org.jikesrvm.arm.BaselineConstants.F0and1;
import static org.jikesrvm.arm.RegisterConstants.TR;
import static org.jikesrvm.arm.RegisterConstants.JTOC;
import static org.jikesrvm.arm.RegisterConstants.FP;
import static org.jikesrvm.arm.RegisterConstants.R12;
import static org.jikesrvm.arm.RegisterConstants.SP;
import static org.jikesrvm.arm.RegisterConstants.LR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_VOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_VOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_VOLATILE_FPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_VOLATILE_FPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_VOLATILE_DPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_LOCAL_GPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_LOCAL_GPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_OS_PARAMETER_GPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_OS_PARAMETER_GPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_OS_PARAMETER_FPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_OS_PARAMETER_FPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_OS_PARAMETER_DPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_OS_PARAMETER_DPR;
import static org.jikesrvm.arm.RegisterConstants.NUM_OS_PARAMETER_GPRS;
import static org.jikesrvm.arm.RegisterConstants.NUM_OS_PARAMETER_FPRS;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_SAVED_REGISTER_OFFSET;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_PARAMETER_OFFSET;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_FRAME_POINTER_OFFSET;
import static org.jikesrvm.arm.StackframeLayoutConstants.BYTES_IN_STACKSLOT;
import static org.jikesrvm.arm.StackframeLayoutConstants.LOG_BYTES_IN_STACKSLOT;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NativeMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.common.assembler.ForwardReference;
import org.jikesrvm.compilers.common.assembler.arm.Assembler;
import org.jikesrvm.jni.JNICompiledMethod;
import org.jikesrvm.jni.JNIGlobalRefTable;
import org.jikesrvm.arm.RegisterConstants.GPR;
import org.jikesrvm.arm.RegisterConstants.FPR;
import org.jikesrvm.arm.RegisterConstants.DPR;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.scheduler.RVMThread;
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

  // Since nonvolatiles are saved and restored for the GC's benefit in the below code, can use them as scratch
  // And we can assume they are preserved by the function call since they are system nonvolatiles
  private static final GPR JNIENV  = GPR.R7;
  private static final GPR BASE    = GPR.R4;  // Pointer to jrefs array
  private static final GPR TOP     = GPR.R5;  // Index of top of array in bytes
  private static final GPR SCRATCH = GPR.R6;  // TODO: move these constants to their own file


  /**
   * This method creates the stub to link native method.  It will be called
   * from the lazy linker the first time a native method is invoked.  The stub
   * generated will be patched by the lazy linker to link to the native method
   * for all future calls. <p>
   * <pre>
   * The stub performs the following tasks in the prologue:
   * <ol>
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
   * The "main" frame exactly follows the OS native ABI.
   * The "mini-frame" stores RVM-specific fields.
   * <pre>
   *
   *                 java caller stack      <--- SP at the point when control passes to the callee
   *            +-------------------------+
   *            |     Saved LR            | <--- Callee's FP + 8     (STACKFRAME_RETURN_ADDRESS_OFFSET = 8)
   *            +-------------------------+
   *            |   Compiled method ID    | <--- Callee's FP + 4     (STACKFRAME_METHOD_ID_OFFSET = 4)
   *            +-------------------------+
   *            |     Saved R11 (FP)      | <--- Callee's FP         (STACKFRAME_FRAME_POINTER_OFFSET = 0)
   *            +-------------------------+
   *            |                         | <--- Highest saved core register (R8)   (STACKFRAME_SAVED_REGISTER_OFFSET = 0, meaning the top of the slot)
   *            |                         |
   *            |     Saved Registers     |
   *            |                         |
   *            |                         | <--- Lowest saved core register (R4)
   *            +-------------------------+
   *            |     JNIEnvironment      |
   *            +-------------------------+
   *            |    native stack frame   |
   *
   * </pre>
   * <p>
   * Runtime.unwindNativeStackFrame will return a pointer to the mini-frame
   * because none of our stack walkers need to do anything with the main frame.
   */
  public static synchronized CompiledMethod compile(NativeMethod method) {
    JNICompiledMethod cm = (JNICompiledMethod)CompiledMethods.createCompiledMethod(method, CompiledMethod.JNI);
    Assembler asm = new Assembler(0);
    RVMClass klass = method.getDeclaringClass();

    if (VM.VerifyAssertions) VM._assert(JNIENV.value()  <= LAST_LOCAL_GPR.value() && JNIENV.value()  >= FIRST_LOCAL_GPR.value());
    if (VM.VerifyAssertions) VM._assert(BASE.value()    <= LAST_LOCAL_GPR.value() && BASE.value()    >= FIRST_LOCAL_GPR.value());
    if (VM.VerifyAssertions) VM._assert(TOP.value()     <= LAST_LOCAL_GPR.value() && TOP.value()     >= FIRST_LOCAL_GPR.value());
    if (VM.VerifyAssertions) VM._assert(SCRATCH.value() <= LAST_LOCAL_GPR.value() && SCRATCH.value() >= FIRST_LOCAL_GPR.value());
    if (VM.VerifyAssertions) VM._assert(T3.value() <= LAST_VOLATILE_GPR.value());

    // TODO: can use push multiple instruction

    asm.generateImmediateLoad(ALWAYS, R12, cm.getId()); // compiled method id

    asm.emitPUSH(ALWAYS, LR);
    asm.emitPUSH(ALWAYS, R12);
    asm.emitPUSH(ALWAYS, FP);

    if (VM.VerifyAssertions) VM._assert(STACKFRAME_FRAME_POINTER_OFFSET.toInt() == 0);
    asm.emitMOV(ALWAYS, FP, SP); // Set frame pointer

    // save non-volatile GPRs for the GC to look at
    // (also allows us to use these for other purposes in the prologue and have them preserved in the epilogue)

    for (int i = LAST_LOCAL_GPR.value(); i >= FIRST_LOCAL_GPR.value(); i--) {
      asm.emitPUSH(ALWAYS, GPR.lookup(i));
    }

    // establish R7 -> thread's JNIEnv structure
    asm.generateOffsetLoad(ALWAYS, JNIENV, TR, Entrypoints.jniEnvField.getOffset());

    // save the TR and JTOC registers in the JNIEnvironment object for possible calls back into Java
    asm.generateOffsetStore(ALWAYS, TR,   JNIENV, Entrypoints.JNIEnvSavedTRField.getOffset());
    asm.generateOffsetStore(ALWAYS, JTOC, JNIENV, Entrypoints.JNIEnvSavedJTOCField.getOffset());

    // save mini-frame frame pointer in JNIEnv, JNITopJavaFP, which will be the frame
    // to start scanning this stack during GC, if top of stack is still executing in C
    asm.generateOffsetStore(ALWAYS, FP, JNIENV, Entrypoints.JNITopJavaFPField.getOffset());

    asm.emitPUSH(ALWAYS, JNIENV); // Save JNIEnvironment in the stack frame


    //
    // SET UP JREFS ARRAY
    //

    // Set up the Reference table for GC
    // R4 (=BASE) <- JREFS array base
    asm.generateOffsetLoad(ALWAYS, BASE, JNIENV, Entrypoints.JNIRefsField.getOffset());
    // R5 (=TOP) <- JREFS current top
    asm.generateOffsetLoad(ALWAYS, TOP, JNIENV, Entrypoints.JNIRefsTopField.getOffset());   // JREFS offset for current TOP

    // TODO - count number of refs
    // TODO - emit overflow check for JNIRefs array

    // This is a good time to do a stack overflow check, because registers have been saved
    // And the JNI values that the check will use have been loaded
    // NOTE: this must be done before the condition Thread.hasNativeStackFrame() become true
    // so that the first Java to C transition will be allowed to resize the stack
    // (currently, this is true when the JNIRefsTop index has been incremented from 0, as it will be in the next block of code)
    if (VM.VerifyAssertions) VM._assert(JNIENV.value() == GPR.R7.value()); // TODO: enforce this by moving the constants to their own class
    if (VM.VerifyAssertions) VM._assert(TOP.value() == GPR.R5.value());
    if (VM.VerifyAssertions) VM._assert(SCRATCH.value() == GPR.R6.value());
    int frameSize = (method.getParameterWords() + 2) << LOG_BYTES_IN_STACKSLOT; // Enough for all the parameters + jnienv + obj
    if (frameSize < 32) frameSize = 32;                                         // Minimum 8 words for the frame
    asm.generateNativeStackOverflowCheck(frameSize + 14);                           // add at least 14 for C frame (header + spill)

    // start a new JNIRefs frame on each transition from Java to native C
    // push current SavedFP ptr onto top of JNIRefs stack (use available R6 (=SCRATCH) reg as a temp)
    // and make current TOP the new savedFP
    asm.generateOffsetLoad(ALWAYS, SCRATCH, JNIENV, Entrypoints.JNIRefsSavedFPField.getOffset());
    asm.emitSTR(ALWAYS, SCRATCH, BASE, TOP);            // push prev frame ptr onto JNIRefs array
    asm.generateOffsetStore(ALWAYS, TOP, JNIENV, Entrypoints.JNIRefsSavedFPField.getOffset());  // save the index as new frame ptr in JNIEnv
    asm.emitADDimm(ALWAYS, TOP, TOP, BYTES_IN_ADDRESS); // TODO: use the single-instruction version of this push


    // generate the code to map the parameters to OS convention and add the
    // second parameter (either the "this" ptr or class if a static method)
    // and the first parameter (JNI Function ptr)
    // Opens a new frame in the JNIRefs table to register the references.
    // Assumes R7 (=JNIENV) set to JNIEnv
    //         R4 (=BASE)   set to JNIRefsField
    //         R5 (=TOP)    is the index (in bytes) of the first unusued slot in the JNIRefs array
    // On return, these registers are still valid (TOP has been modified, however)
    //
    // storeParameters() also returns the number of parameters that this method received on the stack (so we can pop them later)
    int paramStackBytes = storeParameters(asm, method, klass);


    // store the new JNIRefs array TOP back into JNIEnv
    asm.generateOffsetStore(ALWAYS, TOP, JNIENV, Entrypoints.JNIRefsTopField.getOffset());

    //
    // change the status of the thread to IN_JNI
    //

    asm.emitADDimm(ALWAYS, TR, TR, Entrypoints.execStatusField.getOffset().toInt());                        // temporarily move TR
    asm.emitLDREX(ALWAYS, R12, TR);                                                                         // get status for thread
    asm.emitCMPimm(ALWAYS, R12, RVMThread.IN_JAVA + (RVMThread.ALWAYS_LOCK_ON_STATE_TRANSITION ? 100 : 0)); // we should be in java code?
    asm.emitMOVimm(EQ, R12, RVMThread.IN_JNI);                                                              // R12  <- new state value
    asm.emitSTREX(EQ, LR, R12, TR);                                                                         // attempt to change state to IN_JNI (LR is scratch)
    asm.emitSUBimm(ALWAYS, TR, TR, Entrypoints.execStatusField.getOffset().toInt());                        // restore TR
    asm.emitCMPimm(EQ, LR, 0);                                                                              // LR will hold 0 on success

    // If we were not in java, NE still holds
    // If we were and the store failed, NE now holds
    // If all is well, EQ holds
    ForwardReference enteredJNIRef = asm.generateForwardBranch(EQ); // branch if success over slow path

    // Save volatile registers in case they are clobbered (LR was saved previously)
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, ArchEntrypoints.saveVolatilesInstructionsField.getOffset());
    asm.generateOffsetLoad(ALWAYS, R12, TR, Entrypoints.threadContextRegistersField.getOffset()); // saveVolatiles needs R12 = registers object
    asm.emitBLX(ALWAYS, LR);

    // Call into our friendly slow path function.  note that this should
    // work because:
    // 1) we're not calling from C so we don't care what registers are
    //    considered non-volatile in C
    // 2) all Java non-volatiles have been saved
    // 3) the only other registers we need are taken care
    //    of (see above)
    // 4) the prologue and epilogue will take care of the frame pointer
    //    accordingly (it will just save it on the stack and then restore
    //    it - so we don't even have to know what its value is here)
    // the only thing we have to make sure of is that MMTk ignores the
    // framePointer field in RVMThread and uses the one in the JNI
    // environment instead (see Collection.prepareMutator)...
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.enterJNIBlockedFromCallIntoNativeMethod.getOffset());
    asm.emitBLX(ALWAYS, LR);               // call RVMThread.enterJNIBlocked

    // Restore volatile registers
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, ArchEntrypoints.restoreVolatilesInstructionsField.getOffset());
    asm.generateOffsetLoad(ALWAYS, R12, TR, Entrypoints.threadContextRegistersField.getOffset()); // restoreVolatiles needs R12 = registers object
    asm.emitBLX(ALWAYS, LR);

    // come here when we're done
    enteredJNIRef.resolve(asm);

    // CALL NATIVE METHOD
    asm.generateImmediateLoad(ALWAYS, R12, method.getNativeIP().toInt());
    asm.emitBLX(ALWAYS, R12);

    // JTOC, TR, JNIENV, BASE registers still valid after the call

    asm.generateImmediateAdd(ALWAYS, SP, FP, STACKFRAME_SAVED_REGISTER_OFFSET.toInt() - ((1 + LAST_LOCAL_GPR.value() - FIRST_LOCAL_GPR.value()) << LOG_BYTES_IN_ADDRESS)); // Return to the start of our stackframe

    //
    // try to return thread status to IN_JAVA
    //

    asm.emitADDimm(ALWAYS, TR, TR, Entrypoints.execStatusField.getOffset().toInt());                       // temporarily move TR
    asm.emitLDREX(ALWAYS, R12, TR);                                                                        // get status for processor
    asm.emitCMPimm(ALWAYS, R12, RVMThread.IN_JNI + (RVMThread.ALWAYS_LOCK_ON_STATE_TRANSITION ? 100 : 0)); // are we IN_JNI code?
    asm.emitMOVimm(EQ, R12, RVMThread.IN_JAVA);                                                            // R12  <- new state value
    asm.emitSTREX(EQ, LR, R12, TR);                                                                        // attempt to change state to java
    asm.emitSUBimm(ALWAYS, TR, TR, Entrypoints.execStatusField.getOffset().toInt());                       // restore TR
    asm.emitCMPimm(EQ, LR, 0);                                                                             // LR will hold 0 on success

    // If we were not in JNI, NE still holds
    // If we were and the store failed, NE now holds
    // If all is well, EQ holds
    ForwardReference fr = asm.generateForwardBranch(EQ); // branch over blocked call if state change successful

    // Save the return value
    asm.emitPUSH(ALWAYS, T0);
    asm.emitPUSH(ALWAYS, T1);

    // if not IN_JNI call RVMThread.leaveJNIBlockedFromCallIntoNative
    asm.generateOffsetLoad(ALWAYS, R12, JTOC, Entrypoints.leaveJNIBlockedFromCallIntoNativeMethod.getOffset());
    asm.emitBLX(ALWAYS, R12);              // call RVMThread.leaveJNIBlockedFromCallIntoNative

    // Restore the return value
    asm.emitPOP(ALWAYS, T1);
    asm.emitPOP(ALWAYS, T0);

    fr.resolve(asm);

    // pop jrefs frame off the JNIRefs stack, "reopen" the previous top jref frame
    asm.generateOffsetLoad(ALWAYS, TOP, JNIENV, Entrypoints.JNIRefsSavedFPField.getOffset());      // Get current frame ptr
    asm.emitLDR(ALWAYS, SCRATCH, BASE, TOP);                                                       // Load saved frame ptr
    asm.generateOffsetStore(ALWAYS, SCRATCH, JNIENV, Entrypoints.JNIRefsSavedFPField.getOffset()); // Store saved frame ptr
    asm.generateOffsetStore(ALWAYS, TOP, JNIENV, Entrypoints.JNIRefsTopField.getOffset());         // Reset stack ptr to the value of the frame ptr

    // if the the return type is a reference, the native C is returning a jref
    // which is a byte offset from the beginning of the thread's JNIRefs stack/array
    // of the corresponding ref.  In this case, emit code to replace the returned
    // offset (in T0) with the ref from the JNIRefs array
    // The returned reference can also be a JNI Global Reference, created inside the native code; this requires special handling
    TypeReference returnType = method.getReturnType();
    if (returnType.isReferenceType()) {
      asm.emitCMPimm(ALWAYS, T0, 0); // negative = global reference

      // Local ref - load from JNIRefs
      asm.emitLDR(GE, T0, BASE, T0);
      ForwardReference afterGlobalRef = asm.generateForwardBranch(GE);

      // Deal with global references (Use T3 and T2 as scratch)
      asm.generateImmediateLoad(ALWAYS, T3, JNIGlobalRefTable.STRONG_REF_BIT);
      asm.emitANDS(ALWAYS, T2, T0, T3); // Set condition flags
      asm.generateOffsetLoad(ALWAYS, T2, JTOC, Entrypoints.JNIGlobalRefsField.getOffset());

      // Now the condition EQ is true for weak refs and NE is true for strong refs

      asm.emitORR(EQ, T0, T0, T3); // STRONG_REF_BIT
      asm.emitRSBimm(ALWAYS, T0, T0, 0); // Negate     TODO: can use the flag in the LDR instruction to subtract the reg value
      asm.emitLDRshift(ALWAYS, T0, T2, T0, LOG_BYTES_IN_ADDRESS); // convert index to offset and load the value
      asm.generateOffsetLoad(EQ, T0, T0, Entrypoints.referenceReferentField.getOffset());

      afterGlobalRef.resolve(asm);
    } else if (returnType.isLongType()) {
      // Move long return value from GPRs (system convention) to FPR (Jikes convention)
      asm.emitVMOV_core_to_extension64(ALWAYS, F0and1, T0, T1);
    }


    // Check for exception and store result in condition flags
    asm.generateOffsetLoad(ALWAYS, R12, JNIENV, Entrypoints.JNIHasPendingExceptionField.getOffset());
    asm.emitCMPimm(ALWAYS, R12, 0); // Sets condition flags


    // pop the whole stack frame, restore the Java caller frame
    // TODO: can use pop multiple instruction

    // restore non-volatile registers.
    for (int i = FIRST_LOCAL_GPR.value(); i <= LAST_LOCAL_GPR.value(); i++) {
      asm.emitPOP(ALWAYS, GPR.lookup(i));
    }

    asm.emitPOP(ALWAYS, FP);
    asm.emitPOP(ALWAYS, R12); // Discard compiled method id
    asm.emitPOP(ALWAYS, LR);

    if (VM.VerifyAssertions) VM._assert((paramStackBytes & 0x3) == 0); // paramStackBytes % 4 == 0; i.e. word-aligned

    if (paramStackBytes > 0)  // Pop on-stack parameters
      asm.generateImmediateAdd(ALWAYS, SP, SP, paramStackBytes);

    // CHECK CONDITION FLAGS AND BRANCH TO ATHROW CODE OR RETURN NORMALLY
    asm.emitBX(EQ, LR);       // if no pending exception, return to caller

    asm.generateOffsetLoad(ALWAYS, R12, JTOC, Entrypoints.jniThrowPendingException.getOffset());
    asm.emitBX(ALWAYS, R12);  // branch to the exception delivery code, does not return

    cm.compileComplete(asm.getMachineCodes());
    return cm;
  }

  /**
   * Map the arguments from RVM convention to OS convention,
   * and replace all references with indexes into JNIRefs array.
   * <p>
   * Assumption on entry:
   * <ul>
   *   <li>JNIENV = R7 points to JNIEnv
   *   <li>Nonvolatiles (TOP, BASE, SCRATCH = R4, R5, R6) have been saved
   * </ul>
   * <p>
   * Also update the JNIRefs array
   *
   * Returns the number of words of spills (on-stack parameters) passed to the function, so
   * we know how much to pop later
   */
  private static int storeParameters(Assembler asm, NativeMethod method, RVMClass klass) {

    // Several factors conspire to make this function complicated

    // First, we have to insert a new parameter, which means all the rest have to be moved around, and
    // something might get pushed out of the registers and onto the stack

    // Second, the native calling convention requires doubleword spills to be doubleword aligned, so
    // random slots get skipped, and (since spills are stored in reverse order),
    // we can't start writing any of them until we've run through the allocation algorithm once to
    // figure out where the spills begin

    // Third, ARM calling convention allows floats to back-fill registers left behind by doubles,
    // (I chose to disallow this for the Jikes convention), which means that
    // a float might need to move from spills to registers

    // Fourth, ARM convention requires longs to be passed in GPRs but I chose to use FPRs in Jikes,
    // so some floats might need to move from spills to registers, some longs might need to move from
    // FPRs to GPRs or from FPRs to spills, or from spills to GPRs; and some GPRs might get pushed out to spills

    // In short, we have to calculate BOTH the Jikes and the ARM allocation.
    // And we would have to do this anyway even if they were the same because we are inserting
    // a parameter and everything is getting shifted around.


  /*
   *
   * Before conversion:
   *
   *            |                         |
   *            |     Jikes  spills       |
   *            |                         | <--- FP + STACKFRAME_PARAMETER_OFFSET (=12)
   *            +-------------------------+
   *            |       Saved LR          |
   *            +-------------------------+
   *            |   Compiled method ID    |
   *            +-------------------------+
   *            |     Saved R11 (FP)      | <--- FP
   *            +-------------------------+
   *            |                         |
   *            |                         |
   *            |     Saved Registers     |
   *            |                         |
   *            |                         |
   *            +-------------------------+
   *            |     JNIEnvironment      | <--- SP
   *            +-------------------------+
   *
   *
   * After conversion:
   *
   *            |                         |
   *            |     Jikes  spills       |
   *            |                         | <--- FP + STACKFRAME_PARAMETER_OFFSET (=12)
   *            +-------------------------+
   *            |       Saved LR          |
   *            +-------------------------+
   *            |   Compiled method ID    |
   *            +-------------------------+
   *            |     Saved R11 (FP)      | <--- FP
   *            +-------------------------+
   *            |                         |
   *            |                         |
   *            |     Saved Registers     |
   *            |                         |
   *            |                         |
   *            +-------------------------+
   *            |     JNIEnvironment      |
   *            +-------------------------+
   *            |     optional padding    | <--- May or may not be present depending on alignment of stack and of native spills
   *            +-------------------------+
   *            |                         |
   *            |     Native  spills      |
   *            |                         | <--- SP aligned to a 64-bit boundary
   *            +-------------------------+
   */

    // TODO: test this thoroughly
    TypeReference[] args = method.getParameterTypes(); // does not include "this" pointer

    int gp = FIRST_OS_PARAMETER_GPR.value();
    int fp = FIRST_OS_PARAMETER_FPR.value(); // In ARM calling convention, single-precision floats can backfill.
    int dp = FIRST_OS_PARAMETER_DPR.value(); // So if the arguments are float, double, float, they go in S0, D1, S1 (note that D1 is the double view of S2 and S3).

    int totalSpillWords = 0;

    gp++; // First parameter = JNIEnv
    gp++; // Second parameter = class (for static) or this (for non-static)

    for (int i = 0; i < args.length; i++) {
      TypeReference t = args[i];
      if (t.isDoubleType()) {
        if (dp < fp) dp = fp;
        if ((dp & 1) == 1) dp++; // 64-bit align
        if (dp <= LAST_OS_PARAMETER_DPR.value()) {
          dp += 2;
        } else {
          if ((totalSpillWords & 1) == 1) totalSpillWords++; // 64-bit align
          totalSpillWords += 2;
        }
      } else if (t.isFloatType()) {
        if (fp < dp && (fp & 1) == 0) fp = dp; // No more to backfill
        if (fp <= LAST_OS_PARAMETER_FPR.value()) {
          fp++;
        } else {
          totalSpillWords++;
        }
      } else if (t.isLongType()) {
        if ((gp & 1) == 1) gp++; // 64-bit align
        if (gp <= LAST_OS_PARAMETER_GPR.value() - 1) {
          gp += 2;
        } else {
          if ((totalSpillWords & 1) == 1) totalSpillWords++; // 64-bit align
          totalSpillWords += 2;
        }
      } else { // int-like or object
        if (gp <= LAST_OS_PARAMETER_GPR.value()) {
          gp++;
        } else {
          totalSpillWords++;
        }
      }
    }

    if (VM.VerifyAssertions) VM._assert(gp <= LAST_OS_PARAMETER_GPR.value() + 1);
    if (VM.VerifyAssertions) VM._assert(fp <= LAST_OS_PARAMETER_FPR.value() + 1);
    if (VM.VerifyAssertions) VM._assert(dp <= LAST_OS_PARAMETER_DPR.value() + 2);
    if (VM.VerifyAssertions) VM._assert((dp & 1) == 0);

    // Now we know how many spills the native call needs, we can calculate alignment and then actually start copying

    asm.emitANDSimm(ALWAYS, R12, SP, 4); // Test alignment (sets condition flags)

    if ((totalSpillWords & 1) == 0) { // even number, so doubleword-align SP -> need to decrement if (SP & 4 != 0)
       asm.emitSUBimm(NE, SP, SP, BYTES_IN_STACKSLOT);
    } else {                        // odd number, so do the opposite (after writing the spills the SP will be aligned)
       asm.emitSUBimm(EQ, SP, SP, BYTES_IN_STACKSLOT);
    }

    if (totalSpillWords != 0)
      asm.generateImmediateSubtract(ALWAYS, SP, SP, totalSpillWords << LOG_BYTES_IN_INT);

    // Ready to copy!

    // Write the spills first (can't write registers yet because we might overwrite stuff)

    int readOffset = STACKFRAME_PARAMETER_OFFSET.toInt() >> LOG_BYTES_IN_STACKSLOT;    // Read Jikes spills using offsets from FP
    int writeOffset = 0;  // Write native spills using offsets from SP
    int sourceGP = FIRST_VOLATILE_GPR.value();
    int sourceFP = FIRST_VOLATILE_FPR.value();
    int destGP = FIRST_OS_PARAMETER_GPR.value();
    int destFP = FIRST_OS_PARAMETER_FPR.value();
    int destDP = FIRST_OS_PARAMETER_DPR.value();


    final int CLASS_PARAMETER = -3;
    final int JNI_ENV_VALUE = -2;
    final int UNUSED = 0;
    final int IN_FPR = 1000;
    final int IN_DPR = 2000;        // Means that we can use a double-word read/write
    final int IN_GPR = 3000;
    final int IN_GPR_REF = 4000;
    final int IN_SPILLS = 5000;
    final int IN_SPILLS_D = 20000;  // We can use a double-word read
    int[] gprSources = new int[NUM_OS_PARAMETER_GPRS];  // Records where to copy GPRs from - uses the constants
    int[] fprSources = new int[NUM_OS_PARAMETER_FPRS];  // above to divide the values into cases

    gprSources[destGP] = JNI_ENV_VALUE;
    destGP++; // First parameter = JNIEnv

    if (method.isStatic()) {
      gprSources[destGP] = CLASS_PARAMETER;
    } else {
      gprSources[destGP] = IN_GPR_REF + sourceGP;
      sourceGP++;
    }
    destGP++; // Second parameter = class (for static) or this (for non-static)

    if (!method.isStatic())
      sourceGP++;

    for (int i = 0; i < args.length; i++) {
      TypeReference t = args[i];
      if (t.isDoubleType()) {
        if ((sourceFP & 1) == 1) sourceFP++; // 64-bit align

        if (destDP < destFP) destDP = destFP;
        if ((destDP & 1) == 1) destDP++; // 64-bit align

        if (destDP <= LAST_OS_PARAMETER_DPR.value()) {
          if (sourceFP <= LAST_VOLATILE_DPR.value()) {
            if (VM.VerifyAssertions) VM._assert(sourceFP >= destDP);
            fprSources[destDP] = IN_DPR + sourceFP;
            sourceFP += 2;
          } else {
            fprSources[destDP] = IN_SPILLS_D + readOffset;
            readOffset += 2;
          }
          destDP += 2;
        } else {
          if ((writeOffset & 1) == 1) writeOffset++;
          if (sourceFP <= LAST_VOLATILE_DPR.value()) {
            if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); // Native convention should never use more FPRs than Jikes
          } else {
            asm.emitLDRimm(ALWAYS, R12, FP, readOffset << LOG_BYTES_IN_INT);
            asm.emitSTRimm(ALWAYS, R12, SP, writeOffset << LOG_BYTES_IN_INT);

            asm.emitLDRimm(ALWAYS, R12, FP, readOffset + 1 << LOG_BYTES_IN_INT);
            asm.emitSTRimm(ALWAYS, R12, SP, writeOffset + 1 << LOG_BYTES_IN_INT);
            readOffset += 2;
          }
          writeOffset += 2;
        }
      } else if (t.isFloatType()) {
        if (destFP < destDP && (destFP & 1) == 0) destFP = destDP; // No more to backfill

        if (destFP <= LAST_OS_PARAMETER_FPR.value()) {
          if (sourceFP <= LAST_VOLATILE_FPR.value()) {
            if (VM.VerifyAssertions) VM._assert(sourceFP >= destFP);
            fprSources[destFP] = IN_FPR + sourceFP;
            sourceFP++;
          } else {
            fprSources[destFP] = IN_SPILLS + readOffset;
            if (VM.VerifyAssertions) VM._assert(IN_SPILLS + readOffset < IN_SPILLS_D);
            readOffset++;
          }
          destFP++;
        } else {
          if (sourceFP <= LAST_VOLATILE_FPR.value()) {
            if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); // Native convention should never use more FPRs than Jikes
          } else {
            asm.emitLDRimm(ALWAYS, R12, FP, readOffset << LOG_BYTES_IN_INT);
            asm.emitSTRimm(ALWAYS, R12, SP, writeOffset << LOG_BYTES_IN_INT);
            readOffset++;
          }
          writeOffset++;
        }
      } else if (t.isLongType()) {
        if ((sourceFP & 1) == 1) sourceFP++; // 64-bit align
        if ((destGP & 1) == 1) destGP++; // 64-bit align

        if (destGP <= LAST_OS_PARAMETER_GPR.value() - 1) {
          if (sourceFP <= LAST_VOLATILE_DPR.value()) {
            gprSources[destGP] = IN_FPR + sourceFP;
            gprSources[destGP + 1] = IN_FPR + sourceFP + 1;
            sourceFP += 2;
          } else {
            gprSources[destGP] = IN_SPILLS + readOffset;
            gprSources[destGP + 1] = IN_SPILLS + readOffset + 1;
            readOffset += 2;
          }
          destGP += 2;
        } else {
          if ((writeOffset & 1) == 1) writeOffset++;
          if (sourceFP <= LAST_VOLATILE_DPR.value()) {
            asm.emitVSTR64(ALWAYS, DPR.lookup(sourceFP), SP, writeOffset << LOG_BYTES_IN_INT);
            sourceFP += 2;
          } else {
            asm.emitLDRimm(ALWAYS, R12, FP, readOffset << LOG_BYTES_IN_INT);
            asm.emitSTRimm(ALWAYS, R12, SP, writeOffset << LOG_BYTES_IN_INT);

            asm.emitLDRimm(ALWAYS, R12, FP, readOffset + 1 << LOG_BYTES_IN_INT);
            asm.emitSTRimm(ALWAYS, R12, SP, writeOffset + 1 << LOG_BYTES_IN_INT);
            readOffset += 2;
          }
          writeOffset += 2;
        }
      } else { // int-like or object
        if (destGP <= LAST_OS_PARAMETER_GPR.value()) {
          if (sourceGP <= LAST_VOLATILE_GPR.value()) {
            if (t.isReferenceType()) {
              if (VM.VerifyAssertions) VM._assert(destGP >= sourceGP);
              gprSources[destGP] = IN_GPR_REF + sourceGP;
            } else {
              if (VM.VerifyAssertions) VM._assert(destGP >= sourceGP);
              gprSources[destGP] = IN_GPR + sourceGP;
            }
            sourceGP++;
          } else {
            if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); // Native convention should never use fewer GPRs than Jikes
          }
          destGP++;
        } else {
          if (sourceGP <= LAST_VOLATILE_GPR.value()) {
            if (t.isReferenceType()) {
              asm.emitCMPimm(ALWAYS, GPR.lookup(sourceGP), 0);            // Is it null?
              asm.emitSTR(NE, GPR.lookup(sourceGP), BASE, TOP);           // Push reference onto JNIRefs array
              asm.emitMOV(NE, GPR.lookup(sourceGP), TOP);                 // Parameter replaced with index into array
              asm.emitADDimm(NE, TOP, TOP, BYTES_IN_ADDRESS); // TODO: use the single-instruction version of this push
              asm.emitSTRimm(ALWAYS, GPR.lookup(sourceGP), SP, writeOffset << LOG_BYTES_IN_INT); // Write JREF (or unaltered null) to the stack - this spill is now written
            } else {
              asm.emitSTRimm(ALWAYS, GPR.lookup(sourceGP), SP, writeOffset << LOG_BYTES_IN_INT);
            }
            sourceGP++;
          } else {
            if (t.isReferenceType()) {
              asm.emitLDRimm(ALWAYS, R12, FP, readOffset << LOG_BYTES_IN_INT);
              asm.emitCMPimm(ALWAYS, R12, 0);                 // Is it null?
              asm.emitSTR(NE, R12, BASE, TOP);                // Push reference onto JNIRefs array
              asm.emitMOV(NE, R12, TOP);                      // Parameter replaced with index into array
              asm.emitADDimm(NE, TOP, TOP, BYTES_IN_ADDRESS); // TODO: use the single-instruction version of this push
              asm.emitSTRimm(ALWAYS, R12, SP, writeOffset << LOG_BYTES_IN_INT); // Write JREF (or unaltered null) to the stack - this spill is now written
            } else {
              asm.emitLDRimm(ALWAYS, R12, FP, readOffset << LOG_BYTES_IN_INT);
              asm.emitSTRimm(ALWAYS, R12, SP, writeOffset << LOG_BYTES_IN_INT);
            }
            readOffset++;
          }
          writeOffset++;
        }
      }
    }

    if (VM.VerifyAssertions) VM._assert(sourceGP <= LAST_VOLATILE_GPR.value() + 1);
    if (VM.VerifyAssertions) VM._assert(sourceFP <= LAST_VOLATILE_FPR.value() + 1);
    if (VM.VerifyAssertions) VM._assert(destGP == gp);
    if (VM.VerifyAssertions) VM._assert(destFP == fp);
    if (VM.VerifyAssertions) VM._assert(destDP == dp);
    if (VM.VerifyAssertions) VM._assert(writeOffset == totalSpillWords);


    // Now write the GPRs (can't write FPRs yet because we might overwrite stuff)

    if (VM.VerifyAssertions) VM._assert(FIRST_OS_PARAMETER_GPR.value() == 0);
    for (int i = LAST_OS_PARAMETER_GPR.value(); i >= FIRST_OS_PARAMETER_GPR.value(); i--) { // Go backwards here to avoid overwriting registers before they are moved
      if (gprSources[i] >= IN_FPR) {
        if (VM.VerifyAssertions) VM._assert(i != 0 && (i != 1 || !method.isStatic()));
        if (gprSources[i] < IN_GPR) {            // Half of a long value (in FPR)
          asm.emitVMOV_extension32_to_core(ALWAYS, GPR.lookup(i), FPR.lookup(gprSources[i] - IN_FPR));  // TODO: copy both values at the same time
        } else if (gprSources[i] < IN_GPR_REF) {   // Not a reference, in GPR
          if (VM.VerifyAssertions) VM._assert(i >= gprSources[i] - IN_GPR); // Native arguments never use fewer GPRs than JikesRVM
          if (i != gprSources[i] - IN_GPR)
            asm.emitMOV(ALWAYS, GPR.lookup(i), GPR.lookup(gprSources[i] - IN_GPR));
        } else {                                 // A reference, in GPR
          if (VM.VerifyAssertions) VM._assert(i >= gprSources[i] - IN_GPR_REF); // Native arguments never use fewer GPRs than JikesRVM
          if (VM.VerifyAssertions) VM._assert(gprSources[i] < IN_SPILLS);
          if (i != gprSources[i] - IN_GPR_REF)
            asm.emitMOV(ALWAYS, GPR.lookup(i), GPR.lookup(gprSources[i] - IN_GPR_REF));
          asm.emitCMPimm(ALWAYS, GPR.lookup(i), 0);                   // Is it null? (if null just leave it, otherwise:)
          asm.emitSTR(NE, GPR.lookup(i), BASE, TOP);                  // Push reference onto JNIRefs array
          asm.emitMOV(NE, GPR.lookup(i), TOP);                        // Parameter replaced with index into array
          asm.emitADDimm(NE, TOP, TOP, BYTES_IN_ADDRESS); // TODO: use the single-instruction version of this push
        }
      } else if (gprSources[i] == JNI_ENV_VALUE) { // JNIEnv parameter
        if (VM.VerifyAssertions) VM._assert(i == 0);
          // Load required JNI function ptr into first parameter reg (T0)
          // This pointer is an interior pointer to the JNIEnvironment which is
          // currently in JNIENV (=R7).
          asm.emitADDimm(ALWAYS, GPR.lookup(i), JNIENV, Entrypoints.JNIExternalFunctionsField.getOffset().toInt());
      } else if (gprSources[i] == CLASS_PARAMETER) { // klass parameter
        if (VM.VerifyAssertions) VM._assert(i == 1 && method.isStatic());
        Offset klassOffset = Offset.fromIntSignExtend(Statics.findOrCreateObjectLiteral(klass.getClassForType()));
        asm.generateOffsetLoad(ALWAYS, GPR.lookup(i), JTOC, klassOffset);
        asm.emitSTR(ALWAYS, GPR.lookup(i), BASE, TOP);                  // Push class ptr onto JNIRefs array
        asm.emitMOV(ALWAYS, GPR.lookup(i), TOP);                        // Parameter replaced with index into array
        asm.emitADDimm(ALWAYS, TOP, TOP, BYTES_IN_ADDRESS); // TODO: use the single-instruction version of this push
      } else {
        if (VM.VerifyAssertions) VM._assert(gprSources[i] == UNUSED);
      }
    }


    // Now write the FPRs

    if (VM.VerifyAssertions) VM._assert(FIRST_OS_PARAMETER_FPR.value() == 0);
    for (int i = FIRST_OS_PARAMETER_FPR.value(); i <= LAST_OS_PARAMETER_FPR.value(); i++) {
      if (fprSources[i] >= IN_FPR) {
        if (fprSources[i] < IN_DPR) {
          if (VM.VerifyAssertions) VM._assert(i <= fprSources[i] - IN_FPR);    // Native calling convention never uses more FPRs than JikesRVM convention
          if (i != fprSources[i] - IN_FPR)
            asm.emitVMOV32(ALWAYS, FPR.lookup(i), FPR.lookup(fprSources[i] - IN_FPR));

        } else if (fprSources[i] < IN_GPR) {
          if (VM.VerifyAssertions) VM._assert(i <= fprSources[i] - IN_DPR); // Native calling convention never uses more FPRs than JikesRVM convention
          if (VM.VerifyAssertions) VM._assert((i & 1) == 0); // Should be even
          if (VM.VerifyAssertions) VM._assert(fprSources[i + 1] == UNUSED);
          if (i != fprSources[i] - IN_DPR)
            asm.emitVMOV64(ALWAYS, DPR.lookup(i), DPR.lookup(fprSources[i] - IN_DPR));
          i++; // Skip one

        } else if (fprSources[i] < IN_SPILLS_D) {
          if (VM.VerifyAssertions) VM._assert(fprSources[i] >= IN_SPILLS);
          asm.emitVLDR32(ALWAYS, FPR.lookup(i), FP, (fprSources[i] - IN_SPILLS) << LOG_BYTES_IN_INT);

        } else {
          if (VM.VerifyAssertions) VM._assert((i & 1) == 0); // Should be even
          if (VM.VerifyAssertions) VM._assert(fprSources[i + 1] == UNUSED);
          asm.emitVLDR64(ALWAYS, DPR.lookup(i), FP, (fprSources[i] - IN_SPILLS_D) << LOG_BYTES_IN_INT);
          i++; // skip one

        }
      } else {
        if (VM.VerifyAssertions) VM._assert(fprSources[i] == UNUSED);
      }
    }

    return (readOffset - (STACKFRAME_PARAMETER_OFFSET.toInt() >> LOG_BYTES_IN_STACKSLOT)) << LOG_BYTES_IN_INT; // The number of Jikes spills
  }

  /**
   * Emit code to do the C to Java transition:  JNI methods in JNIFunctions.java
   */
  public static void generatePrologueForJNIMethod(Assembler asm, RVMMethod mth) {

    // Save non-volatile GPRs that will not be saved and restored by RVM.
    asm.emitPUSH(ALWAYS, TR); // TODO use push-multiple instruction
    asm.emitPUSH(ALWAYS, JTOC);

    // on entry T0 = JNIEnv* which is an interior pointer to this thread's JNIEnvironment.
    // We first adjust this in place to be a pointer to a JNIEnvironment and then use
    // it to acquire THREAD_REGISTER and JTOC.
    asm.emitSUBimm(ALWAYS, T0, T0, Entrypoints.JNIExternalFunctionsField.getOffset().toInt());
    asm.generateOffsetLoad(ALWAYS, TR,   T0, Entrypoints.JNIEnvSavedTRField.getOffset());
    asm.generateOffsetLoad(ALWAYS, JTOC, T0, Entrypoints.JNIEnvSavedJTOCField.getOffset());

    // Assumes SP points to (native parameters + 2 slots)
    convertParametersFromNativeToJava(asm, mth);

    asm.emitPUSH(ALWAYS, LR); // Save LR so we can use as scratch

    // Attempt to change the vpStatus of the current Processor to IN_JAVA
    asm.emitADDimm(ALWAYS, TR, TR, Entrypoints.execStatusField.getOffset().toInt());                       // temporarily move TR
    asm.emitLDREX(ALWAYS, R12, TR);                                                                        // get status for processor
    asm.emitCMPimm(ALWAYS, R12, RVMThread.IN_JNI + (RVMThread.ALWAYS_LOCK_ON_STATE_TRANSITION ? 100 : 0)); // check if GC in progress, blocked in native mode
    asm.emitMOVimm(EQ, R12, RVMThread.IN_JAVA);                                                            // R12  <- new state value
    asm.emitSTREX(EQ, LR, R12, TR);                                                                        // attempt to change state to java
    asm.emitSUBimm(ALWAYS, TR, TR, Entrypoints.execStatusField.getOffset().toInt());                       // restore TR
    asm.emitCMPimm(EQ, LR, 0);                                                                             // LR will hold 0 on success

    // If we were not IN_JNI, NE still holds
    // If we were and the store failed, NE now holds
    // If all is well, EQ holds
    ForwardReference frInJava = asm.generateForwardBranch(EQ); // branch over blocked call if state change successful

    // blocked in native, call leaveJNIBlocked
    // must save volatile gprs & fprs before the call and restore after

    // LR still saved so can be used as scratch

    // Save volatile registers in case they are clobbered (LR was saved previously)
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, ArchEntrypoints.saveVolatilesInstructionsField.getOffset());
    asm.generateOffsetLoad(ALWAYS, R12, TR, Entrypoints.threadContextRegistersField.getOffset()); // saveVolatiles needs R12 = registers object
    asm.emitBLX(ALWAYS, LR);

    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.leaveJNIBlockedFromJNIFunctionCallMethod.getOffset());
    asm.emitBLX(ALWAYS, LR);               // call RVMThread.leaveJNIBlockFromJNIFunction

    // Restore volatile registers
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, ArchEntrypoints.restoreVolatilesInstructionsField.getOffset());
    asm.generateOffsetLoad(ALWAYS, R12, TR, Entrypoints.threadContextRegistersField.getOffset()); // restoreVolatiles needs R12 = registers object
    asm.emitBLX(ALWAYS, LR);

    // NOW_IN_JAVA:
    // JTOC, and TR are all as Jikes RVM expects them;
    // params are where the Jikes RVM calling conventions expects them.
    frInJava.resolve(asm);

    // Restore LR
    asm.emitPOP(ALWAYS, LR);

    // END OF PROLOGUE OF GLUE CODE; rest of method generated by Compiler from bytecodes of method in JNIFunctions
  }

  // Assumes SP points to (native parameters + 2 slots); note the alignment is important - some code here assumes that
  // the SP will be doubleword aligned (as per ARM calling convention), and the 2-slot offset preserves this
  private static void convertParametersFromNativeToJava(Assembler asm, RVMMethod method) {

    String mthName = method.getName().toString();
    final boolean usesVarargs =
        (mthName.startsWith("Call") && mthName.endsWith("Method")) || mthName.equals("NewObject");

    if (usesVarargs) {
      asm.generateUndefined(); // These should be handled by the C functions in sysVarArgs.c and the Java methods in JNIHelpers.java
    }

    if (mthName.equals("<init>"))    // Initialiser of the JNIFunctions class should never be called
      asm.generateUndefined();
    else
      if (VM.VerifyAssertions) VM._assert(method.isStatic()); // All other methods in the class are static

    // TODO: test this thoroughly
    TypeReference[] args = method.getParameterTypes();

    // First calculate how many spill words the Java function needs
    int gp = FIRST_VOLATILE_GPR.value();
    int fp = FIRST_VOLATILE_FPR.value();

    int spillWords = 0;

    for (TypeReference t : args) {
      if (t.isLongType() || t.isDoubleType()) {
        if ((fp & 1) == 1) fp++; // 64-bit align
        if (fp <= LAST_VOLATILE_DPR.value()) {
          fp += 2;
        } else {
          spillWords += 2;
        }
      } else if (t.isFloatType()) {
        if (fp <= LAST_VOLATILE_FPR.value()) {
          fp++;
        } else {
          spillWords++;
        }
      } else { // int-like or object
        if (gp <= LAST_VOLATILE_GPR.value()) {
          gp++;
        } else {
          spillWords++;
        }
      }
    }

    if (VM.VerifyAssertions) VM._assert(gp <= LAST_VOLATILE_GPR.value() + 1);
    if (VM.VerifyAssertions) VM._assert(fp <= LAST_VOLATILE_FPR.value() + 1);

    // Native spills start 2 slots above the SP, java spills will now go below the SP
    int readOffset = 2;
    int writeOffset = -spillWords;

    // Write the spills first (can't write registers yet because we might overwrite stuff)

    int destGP = FIRST_VOLATILE_GPR.value();
    int destFP = FIRST_VOLATILE_FPR.value();
    int sourceGP = FIRST_OS_PARAMETER_GPR.value();
    int sourceFP = FIRST_OS_PARAMETER_FPR.value();
    int sourceDP = FIRST_OS_PARAMETER_DPR.value();

    final int UNUSED = 0;
    final int IN_FPR = 1000;
    final int IN_DPR = 2000;        // Means that we can use a double-word read/write
    final int IN_GPR = 3000;
    final int IN_SPILLS = 4000;
    final int IN_SPILLS_D = 20000;  // We can use a double-word read
    int[] gprSources = new int[NUM_OS_PARAMETER_GPRS];  // Records where to copy GPRs from - uses the constants
    int[] fprSources = new int[NUM_OS_PARAMETER_FPRS];  // above to divide the values into cases

    for (int i = 0; i < args.length; i++) {
      TypeReference t = args[i];
      if (t.isDoubleType()) {
        if ((destFP & 1) == 1) destFP++; // 64-bit align

        if (sourceDP < sourceFP) sourceDP = sourceFP;
        if ((sourceDP & 1) == 1) sourceDP++; // 64-bit align

        if (sourceDP <= LAST_OS_PARAMETER_DPR.value()) {
          if (destFP <= LAST_VOLATILE_DPR.value()) {
            if (VM.VerifyAssertions) VM._assert(destFP >= sourceDP);
            fprSources[destFP] = IN_DPR + sourceDP;
            destFP += 2;
          } else {
            asm.emitVSTR64(ALWAYS, DPR.lookup(sourceDP), SP, writeOffset << LOG_BYTES_IN_INT);
            writeOffset += 2;
          }
          sourceDP += 2;
        } else {
          if ((readOffset & 1) == 1) readOffset++;
          if (destFP <= LAST_VOLATILE_DPR.value()) {
            if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); // Native convention should never use more FPRs than Jikes
          } else {
            asm.emitLDRimm(ALWAYS, R12, FP, readOffset << LOG_BYTES_IN_INT);
            asm.emitSTRimm(ALWAYS, R12, SP, writeOffset << LOG_BYTES_IN_INT);

            asm.emitLDRimm(ALWAYS, R12, FP, readOffset + 1 << LOG_BYTES_IN_INT);
            asm.emitSTRimm(ALWAYS, R12, SP, writeOffset + 1 << LOG_BYTES_IN_INT);
            writeOffset += 2;
          }
          readOffset += 2;
        }
      } else if (t.isFloatType()) {
        if (sourceFP < sourceDP && (sourceFP & 1) == 0) sourceFP = sourceDP; // No more to backfill

        if (sourceFP <= LAST_OS_PARAMETER_FPR.value()) {
          if (destFP <= LAST_VOLATILE_FPR.value()) {
            if (VM.VerifyAssertions) VM._assert(destFP >= sourceDP);
            fprSources[destFP] = IN_FPR + sourceFP;
            destFP++;
          } else {
            asm.emitVSTR32(ALWAYS, FPR.lookup(sourceFP), SP, writeOffset << LOG_BYTES_IN_INT);
            writeOffset++;
          }
          sourceFP++;
        } else {
          if (destFP <= LAST_VOLATILE_FPR.value()) {
            if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); // Native convention should never use more FPRs than Jikes
          } else {
            asm.emitLDRimm(ALWAYS, R12, FP, readOffset << LOG_BYTES_IN_INT);
            asm.emitSTRimm(ALWAYS, R12, SP, writeOffset << LOG_BYTES_IN_INT);
            writeOffset++;
          }
          readOffset++;
        }
     } else if (t.isLongType()) {
        if ((destFP & 1) == 1) destFP++; // 64-bit align
        if ((sourceGP & 1) == 1) sourceGP++; // 64-bit align

        if (sourceGP <= LAST_OS_PARAMETER_GPR.value() - 1) {
          if (destFP <= LAST_VOLATILE_DPR.value()) {
            fprSources[destFP] = IN_GPR + sourceGP;
            destFP += 2;
          } else {
            asm.emitSTRimm(ALWAYS, GPR.lookup(sourceGP),     SP, writeOffset << LOG_BYTES_IN_INT);
            asm.emitSTRimm(ALWAYS, GPR.lookup(sourceGP + 1), SP, writeOffset + 1 << LOG_BYTES_IN_INT);
            writeOffset += 2;
          }
          sourceGP += 2;
        } else {
          if ((readOffset & 1) == 1) readOffset++;
          if (destFP <= LAST_VOLATILE_DPR.value()) {
            fprSources[destFP] = IN_SPILLS_D + readOffset;
            destFP += 2;
          } else {
            asm.emitLDRimm(ALWAYS, R12, FP, readOffset << LOG_BYTES_IN_INT);
            asm.emitSTRimm(ALWAYS, R12, SP, writeOffset << LOG_BYTES_IN_INT);

            asm.emitLDRimm(ALWAYS, R12, FP, readOffset + 1 << LOG_BYTES_IN_INT);
            asm.emitSTRimm(ALWAYS, R12, SP, writeOffset + 1 << LOG_BYTES_IN_INT);
            writeOffset += 2;
          }
          readOffset += 2;
        }
      } else { // int-like or object
        if (sourceGP <= LAST_OS_PARAMETER_GPR.value()) {
          if (destGP <= LAST_VOLATILE_GPR.value()) {
            if (VM.VerifyAssertions) VM._assert(sourceGP >= destGP);
            gprSources[destGP] = IN_GPR + sourceGP;
            destGP++;
          } else {
            if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); // Native convention should never use fewer GPRs than Jikes
          }
          sourceGP++;
        } else {
          if (destGP <= LAST_VOLATILE_GPR.value()) {
            gprSources[destGP] = IN_SPILLS + readOffset;
            destGP++;
          } else {
            asm.emitLDRimm(ALWAYS, R12, FP, readOffset << LOG_BYTES_IN_INT);
            asm.emitSTRimm(ALWAYS, R12, SP, writeOffset << LOG_BYTES_IN_INT);
            writeOffset++;
          }
          readOffset++;
        }
      }
    }

    if (VM.VerifyAssertions) VM._assert(destGP == gp);
    if (VM.VerifyAssertions) VM._assert(destFP == fp);
    if (VM.VerifyAssertions) VM._assert(sourceGP <= LAST_OS_PARAMETER_GPR.value() + 1);
    if (VM.VerifyAssertions) VM._assert(sourceFP <= LAST_OS_PARAMETER_FPR.value() + 1);
    if (VM.VerifyAssertions) VM._assert(sourceDP <= LAST_OS_PARAMETER_DPR.value() + 2);
    if (VM.VerifyAssertions) VM._assert(writeOffset == 0);


    // Now write the FPRs (can't write GPRs yet because we might overwrite stuff)

    if (VM.VerifyAssertions) VM._assert(FIRST_VOLATILE_FPR.value() == 0);
    for (int i = LAST_VOLATILE_FPR.value(); i <= FIRST_VOLATILE_FPR.value(); i--) { // Go backwards here to avoid overwriting registers before they are moved
      if (fprSources[i] >= IN_FPR) {
        if (fprSources[i] < IN_DPR) {
          if (VM.VerifyAssertions) VM._assert(i >= fprSources[i] - IN_FPR);    // Native calling convention never uses more FPRs than JikesRVM convention
          if (i != fprSources[i] - IN_FPR)
            asm.emitVMOV32(ALWAYS, FPR.lookup(i), FPR.lookup(fprSources[i] - IN_FPR));

        } else if (fprSources[i] < IN_GPR) {
          if (VM.VerifyAssertions) VM._assert(i >= fprSources[i] - IN_DPR); // Native calling convention never uses more FPRs than JikesRVM convention
          if (VM.VerifyAssertions) VM._assert((i & 1) == 0); // Should be even
          if (VM.VerifyAssertions) VM._assert(fprSources[i + 1] == UNUSED);
          if (i != fprSources[i] - IN_DPR)
            asm.emitVMOV64(ALWAYS, DPR.lookup(i), DPR.lookup(fprSources[i] - IN_DPR));
        } else if (fprSources[i] < IN_SPILLS) {
          if (VM.VerifyAssertions) VM._assert(((fprSources[i] - IN_GPR) & 1) == 0); // A long value (in GPR)
          if (VM.VerifyAssertions) VM._assert((i & 1) == 0); // Should be even
          if (VM.VerifyAssertions) VM._assert(fprSources[i + 1] == UNUSED);
          asm.emitVMOV_core_to_extension64(ALWAYS, DPR.lookup(i), GPR.lookup(fprSources[i] - IN_GPR), GPR.lookup(fprSources[i] - IN_GPR + 1));
        } else if (fprSources[i] < IN_SPILLS_D) {
          if (VM.VerifyAssertions) VM._assert(fprSources[i] >= IN_SPILLS);
          asm.emitVLDR32(ALWAYS, FPR.lookup(i), SP, (fprSources[i] - IN_SPILLS) << LOG_BYTES_IN_INT);
        } else {
          if (VM.VerifyAssertions) VM._assert((i & 1) == 0); // Should be even
          if (VM.VerifyAssertions) VM._assert(fprSources[i + 1] == UNUSED);
          asm.emitVLDR64(ALWAYS, DPR.lookup(i), SP, (fprSources[i] - IN_SPILLS_D) << LOG_BYTES_IN_INT);
        }
      } else {
        if (VM.VerifyAssertions) VM._assert(fprSources[i] == UNUSED);
      }
    }


    // Now write the GPRs

    if (VM.VerifyAssertions) VM._assert(FIRST_VOLATILE_GPR.value() == 0);
    for (int i = FIRST_VOLATILE_GPR.value(); i <= LAST_VOLATILE_GPR.value(); i++) {
      if (gprSources[i] < IN_GPR) {
        if (VM.VerifyAssertions) VM._assert(gprSources[i] == UNUSED);
      } else if (gprSources[i] < IN_SPILLS) {
        if (VM.VerifyAssertions) VM._assert(i <= gprSources[i] - IN_GPR); // Native arguments never use fewer GPRs than JikesRVM
        if (i != gprSources[i] - IN_GPR)
          asm.emitMOV(ALWAYS, GPR.lookup(i), GPR.lookup(gprSources[i] - IN_GPR));
      } else if (gprSources[i] < IN_SPILLS_D) {
          asm.emitLDRimm(ALWAYS, GPR.lookup(i), SP, (gprSources[i] - IN_SPILLS) << LOG_BYTES_IN_INT);
      } else {
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      }
    }

    // Move stack pointer past all the parameters
    if (spillWords != 0)
      asm.generateImmediateSubtract(ALWAYS, SP, SP, spillWords << LOG_BYTES_IN_INT);
  }

  public static void generateEpilogueForJNIMethod(Assembler asm, RVMMethod mth) {

    // START OF EPILOGUE OF GLUE CODE

    // T0 and T1 or F0and1 contain the return value from the function - DO NOT USE

    // assume: JTOC and THREAD_REG are valid, and all RVM non-volatile
    // GPRs and FPRs have been restored.  Our processor state will be IN_JAVA.

    // establish T2 -> current thread's JNIEnvironment, from activeThread field
    // of current processor
    asm.generateOffsetLoad(ALWAYS, T2, TR, Entrypoints.jniEnvField.getOffset());

    // store current TR into JNIEnvironment; we may have switched TRs while in Java mode.
    asm.generateOffsetStore(ALWAYS, TR, T2, Entrypoints.JNIEnvSavedTRField.getOffset());

    //
    // change the state of the TR to IN_JNI
    //
    asm.emitADDimm(ALWAYS, TR, TR, Entrypoints.execStatusField.getOffset().toInt());                        // temporarily move TR
    asm.emitLDREX(ALWAYS, R12, TR);                                                                         // get status for thread
    asm.emitCMPimm(ALWAYS, R12, RVMThread.IN_JAVA + (RVMThread.ALWAYS_LOCK_ON_STATE_TRANSITION ? 100 : 0)); // we should be in java code?
    asm.emitMOVimm(EQ, R12, RVMThread.IN_JNI);                                                              // R12  <- new state value
    asm.emitSTREX(EQ, T3, R12, TR);                                                                         // attempt to change state to IN_JNI (use T3 as scratch)
    asm.emitSUBimm(ALWAYS, TR, TR, Entrypoints.execStatusField.getOffset().toInt());                        // restore TR
    asm.emitCMPimm(EQ, T3, 0);                                                                              // R8 will hold 0 on success

    // If we were not in java, NE still holds
    // If we were and the store failed, NE now holds
    // If all is well, EQ holds
    ForwardReference enteredJNIRef = asm.generateForwardBranch(EQ); // branch if success over slow path

    // Save the return value in case it gets clobbered
    if (mth.getReturnType().isDoubleType() || mth.getReturnType().isLongType())
      asm.emitVPUSH64(ALWAYS, F0and1);
    else if (mth.getReturnType().isFloatType())
      asm.emitVPUSH32(ALWAYS, F0);
    else if (!mth.getReturnType().isVoidType())
      asm.emitPUSH(ALWAYS, T0);

    asm.emitPUSH(ALWAYS, LR);              // Save LR
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.enterJNIBlockedFromJNIFunctionCallMethod.getOffset());
    asm.emitBLX(ALWAYS, LR);               // call RVMThread.enterJNIBlockedFromJNIFunction
    asm.emitPOP(ALWAYS, LR);               // Restore LR

    // Restore the return value
    if (mth.getReturnType().isDoubleType() || mth.getReturnType().isLongType())
      asm.emitVPOP64(ALWAYS, F0and1);
    else if (mth.getReturnType().isFloatType())
      asm.emitVPOP32(ALWAYS, F0);
    else if (!mth.getReturnType().isVoidType())
      asm.emitPOP(ALWAYS, T0);

    enteredJNIRef.resolve(asm);

    if (mth.getReturnType().isLongType()) {
      // Move long return value from FPR (Jikes convention) to GPRs (system convention)
      asm.emitVMOV_extension64_to_core(ALWAYS, T0, T1, F0and1);
    }

    // Restore the nonvolatile registers saved in the prolog above
    // Here we only save & restore ONLY those registers not restored by RVM
    asm.emitPOP(ALWAYS, JTOC);
    asm.emitPOP(ALWAYS, TR);

    asm.emitBX(ALWAYS, LR); // Return to caller
  }
}
