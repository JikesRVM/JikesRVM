/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.jni.ia32;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NativeMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.common.assembler.ForwardReference;
import org.jikesrvm.compilers.common.assembler.ia32.Assembler;
import org.jikesrvm.ia32.BaselineConstants;
import org.jikesrvm.ia32.MachineCode;
import org.jikesrvm.ia32.ProcessorLocalState;
import org.jikesrvm.jni.JNICompiledMethod;
import org.jikesrvm.jni.JNIGlobalRefTable;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.Processor;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * This class compiles the prolog and epilog for all code that makes
 * the transition between Java and Native C
 * <pre>
 * 2 cases:
 *  -from Java to C:  all user-defined native methods
 *  -C to Java:  all JNI functions in JNIFunctions.java
 * </pre>
 *
 * If this code is being used, then we assume RVM_WITH_SVR4_ABI is set.
 */
public abstract class JNICompiler implements BaselineConstants {

  // offsets to saved regs and addresses in java to C glue frames
  // EDI and EBX are nonvolatile registers in RVM
  //
  private static final int SAVED_GPRS = 4;
  public static final Offset EDI_SAVE_OFFSET = Offset.fromIntSignExtend(STACKFRAME_BODY_OFFSET);
  public static final Offset EBX_SAVE_OFFSET = EDI_SAVE_OFFSET.minus(WORDSIZE);
  public static final Offset EBP_SAVE_OFFSET = EBX_SAVE_OFFSET.minus(WORDSIZE);
  public static final Offset JNI_ENV_OFFSET = EBP_SAVE_OFFSET.minus(WORDSIZE);

  // following used in prolog & epilog for JNIFunctions
  // offset of saved offset to preceeding java frame
  public static final Offset SAVED_JAVA_FP_OFFSET = Offset.fromIntSignExtend(STACKFRAME_BODY_OFFSET);

  // following used in BaselineCompilerImpl to compute offset to first local:
  // includes 5 words:
  //   SAVED_JAVA_FP,  JNIEnvironment, S0 (ECX), EBX, and EDI
  public static final int SAVED_GPRS_FOR_JNI = 5;

  /*****************************************************************
   * Handle the Java to C transition:  native methods
   */
  public static synchronized CompiledMethod compile(NativeMethod method) {
    JNICompiledMethod cm =
        (JNICompiledMethod) CompiledMethods.createCompiledMethod(method, CompiledMethod.JNI);
    Assembler asm = new ArchitectureSpecific.Assembler(100);   // some size for the instruction array
    Address nativeIP = method.getNativeIP();
    // recompute some constants
    int parameterWords = method.getParameterWords();

    // Meaning of constant offset into frame:
    // STACKFRAME_HEADER_SIZE =  12              (CHECK ??)
    // SAVED_GPRS = 4 words/registers
    // Stack frame:
    //        on entry          after prolog
    //
    //      high address        high address
    //      |          |        |          | Caller frame
    //      |          |        |          |
    // +    |arg 0     |        |arg 0     |    -> firstParameterOffset
    // +    |arg 1     |        |arg 1     |
    // +    |...       |        |...       |
    // +8   |arg n-1   |        |arg n-1   |
    // +4   |returnAddr|        |returnAddr|
    //  0   +          +        +saved FP  + <---- FP for glue frame
    // -4   |          |        |methodID  |
    // -8   |          |        |saved EDI |    -> STACKFRAME_BODY_OFFSET = -8
    // -C   |          |        |saved EBX |
    // -10  |          |        |saved EBP |
    // -14  |          |        |saved ENV |  (JNIEnvironment)
    // -18  |          |        |arg n-1   |  reordered args to native method
    // -1C  |          |        | ...      |  ...
    // -20  |          |        |arg 1     |  ...
    // -24  |          |        |arg 0     |  ...
    // -28  |          |        |class/obj |  required second arg to native method
    // -2C  |          |        |jniEnv    |  required first arg to native method
    // -30  |          |        |          |
    //      |          |        |          |
    //      |          |        |          |
    //       low address         low address

    // TODO:  check and resize stack once on the lowest Java to C transition
    // on the stack.  Not needed if we use the thread original stack

    // Fill in frame header - similar to normal prolog
    prepareStackHeader(asm, method, cm.getId());

    // Process the arguments - specific to method being called
    storeParametersForLintel(asm, method);

    // change processor status to IN_NATIVE
    ProcessorLocalState.emitMoveImmToField(asm, Entrypoints.vpStatusField.getOffset(), Processor.IN_NATIVE);

    // load address of native code to invoke into S0
    asm.emitMOV_Reg_Imm(S0, nativeIP.toInt());

    // make the call to native code
    asm.emitCALL_Reg(S0);

    // return from native code here...
    // T0 contains single word return value from native
    // T1 will contain the second word of a long return value

    // push return values on stack
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(T1);

    int retryLabel = asm.getMachineCodeIndex();     // backward branch label

    // reload JNIEnvironment from glue frame
    asm.emitMOV_Reg_RegDisp(S0, EBP, JNICompiler.JNI_ENV_OFFSET);

    // reload processor register from JNIEnvironment
    ProcessorLocalState.emitLoadProcessor(asm, S0, Entrypoints.JNIEnvSavedPRField.getOffset());

    // T0 gets PR.statusField
    ProcessorLocalState.emitMoveFieldToReg(asm, T0, Entrypoints.vpStatusField.getOffset());

    asm.emitCMP_Reg_Imm(T0, Processor.IN_NATIVE);      // still IN_NATIVE?
    ForwardReference fr1 = asm.forwardJcc(Assembler.EQ);       // if so, skip over call to pthread yield

    // blocked in native, do pthread yield
    asm.emitMOV_Reg_Abs(T0, Magic.getTocPointer().plus(Entrypoints.the_boot_recordField.getOffset()));
    asm.emitCALL_RegDisp(T0, Entrypoints.sysVirtualProcessorYieldIPField.getOffset());
    asm.emitJMP_Imm(retryLabel);                          // retry from beginning

    fr1.resolve(asm);      // branch here if IN_NATIVE, attempt to go to IN_JAVA

    // T0 (EAX) contains "old value" (required for CMPXCNG instruction)
    asm.emitMOV_Reg_Imm(T1, Processor.IN_JAVA);  // T1<-new value (IN_JAVA)
    ProcessorLocalState.emitCompareAndExchangeField(asm,
                                                       Entrypoints.vpStatusField.getOffset(),
                                                       T1); // atomic compare-and-exchange
    asm.emitJCC_Cond_Imm(Assembler.NE, retryLabel);

    // status is now IN_JAVA (normal operation)

    // pop return values off stack into expected regs before returning to caller
    asm.emitPOP_Reg(T1);
    asm.emitPOP_Reg(T0);

    // If the return type is reference, look up the real value in the JNIref array

    // S0 <- threads' JNIEnvironment
    ProcessorLocalState.emitMoveFieldToReg(asm, S0, Entrypoints.activeThreadField.getOffset());
    asm.emitMOV_Reg_RegDisp(S0, S0, Entrypoints.jniEnvField.getOffset());

    if (method.getReturnType().isReferenceType()) {
      asm.emitCMP_Reg_Imm(T0, 0);
      ForwardReference globalRef = asm.forwardJcc(Assembler.LT);

      // Deal with local references
      asm.emitADD_Reg_RegDisp(T0,
          S0,
          Entrypoints.JNIRefsField.getOffset());      // T0 <- address of entry (not index)
      asm.emitMOV_Reg_RegInd(T0, T0);   // get the reference
      ForwardReference afterGlobalRef = asm.forwardJMP();

      // Deal with global references
      globalRef.resolve(asm);
      asm.emitMOV_Reg_Reg(T1, T0);
      asm.emitTEST_Reg_Imm(T1, JNIGlobalRefTable.STRONG_REF_BIT);
      asm.emitMOV_Reg_Abs(T1, Magic.getTocPointer().plus(Entrypoints.JNIGlobalRefsField.getOffset()));
      ForwardReference weakGlobalRef = asm.forwardJcc(Assembler.EQ);

      // Strong global references
      asm.emitNEG_Reg(T0);
      asm.emitMOV_Reg_RegIdx(T0, T1, T0, Assembler.WORD, Offset.zero());
      ForwardReference afterWeakGlobalRef = asm.forwardJMP();

      // Weak global references
      weakGlobalRef.resolve(asm);
      asm.emitOR_Reg_Imm(T0, JNIGlobalRefTable.STRONG_REF_BIT);
      asm.emitNEG_Reg(T0);
      asm.emitMOV_Reg_RegIdx(T0, T1, T0, Assembler.WORD, Offset.zero());
      asm.emitMOV_Reg_RegDisp(T0, T0, Entrypoints.referenceReferentField.getOffset());

      afterWeakGlobalRef.resolve(asm);
      afterGlobalRef.resolve(asm);
    } else if (method.getReturnType().isLongType()) {
      asm.emitPUSH_Reg(T1);    // need to use T1 in popJNIrefForEpilog and to swap order T0-T1
    }

    // pop frame in JNIRefs array (assumes S0 holds JNIEnvironment)
    popJNIrefForEpilog(asm);

    // then swap order of T0 and T1 for long
    if (method.getReturnType().isLongType()) {
      asm.emitMOV_Reg_Reg(T1, T0);
      asm.emitPOP_Reg(T0);
    }

    // CHECK EXCEPTION AND BRANCH TO ATHROW CODE OR RETURN NORMALLY

    // get pending exception from JNIEnv
    asm.emitMOV_Reg_RegDisp(EBX,
                            S0,
                            Entrypoints.JNIPendingExceptionField.getOffset());  // EBX <- JNIPendingException
    asm.emitMOV_RegDisp_Imm(S0,
                            Entrypoints.JNIPendingExceptionField.getOffset(),
                            0);    // clear the current pending exception

    asm.emitCMP_Reg_Imm(EBX, 0);   // check for exception pending:  JNIPendingException = non zero
    ForwardReference fr = asm.forwardJcc(Assembler.EQ);            // Br if yes

    // if pending exception, discard the return value and current stack frame
    // then jump to athrow
    asm.emitMOV_Reg_Reg(T0, EBX);
    asm.emitMOV_Reg_Abs(T1, Magic.getTocPointer().plus(Entrypoints.athrowMethod.getOffset())); // acquire jump addr before restoring nonvolatiles

    asm.emitMOV_Reg_Reg(SP, EBP);                        // discard current stack frame
    asm.emitMOV_Reg_RegDisp(EDI, SP, EDI_SAVE_OFFSET);   // restore nonvolatile EDI register
    asm.emitMOV_Reg_RegDisp(EBX, SP, EBX_SAVE_OFFSET);   // restore nonvolatile EBX register
    asm.emitMOV_Reg_RegDisp(EBP, SP, EBP_SAVE_OFFSET);   // restore nonvolatile EBP register

    asm.emitPOP_RegDisp(PR, ArchEntrypoints.framePointerField.getOffset());

    // don't use CALL since it will push on the stack frame the return address to here
    asm.emitJMP_Reg(T1); // jumps to RuntimeEntrypoints.athrow

    fr.resolve(asm);  // branch to here if no exception

    // no exception, proceed to return to caller
    asm.emitMOV_Reg_Reg(SP, EBP);                           // discard current stack frame

    asm.emitMOV_Reg_RegDisp(EDI, SP, EDI_SAVE_OFFSET);   // restore nonvolatile EDI register
    asm.emitMOV_Reg_RegDisp(EBX, SP, EBX_SAVE_OFFSET);    // restore nonvolatile EBX register
    asm.emitMOV_Reg_RegDisp(EBP, SP, EBP_SAVE_OFFSET);    // restore nonvolatile EBP register

    asm.emitPOP_RegDisp(PR, ArchEntrypoints.framePointerField.getOffset());

    if (SSE2_FULL) {
      // Marshall from FP0 to XMM0
      if (method.getReturnType().isFloatType()) {
        asm.emitFSTP_RegDisp_Reg(PR, Entrypoints.scratchStorageField.getOffset(), FP0);
        asm.emitMOVSS_Reg_RegDisp(XMM0, PR, Entrypoints.scratchStorageField.getOffset());
      } else if  (method.getReturnType().isDoubleType()) {
        asm.emitFSTP_RegDisp_Reg_Quad(PR, Entrypoints.scratchStorageField.getOffset(), FP0);
        asm.emitMOVSD_Reg_RegDisp(XMM0, PR, Entrypoints.scratchStorageField.getOffset());
      }
    }

    // return to caller
    // pop parameters from stack (Note that parameterWords does not include "this")
    if (method.isStatic()) {
      asm.emitRET_Imm(parameterWords << LG_WORDSIZE);
    } else {
      asm.emitRET_Imm((parameterWords + 1) << LG_WORDSIZE);
    }

    MachineCode machineCode = new ArchitectureSpecific.MachineCode(asm.getMachineCodes(), null);
    cm.compileComplete(machineCode.getInstructions());
    return cm;
  }

  /**************************************************************
   * Prepare the stack header for Java to C transition.
   * <pre>
   *         before               after
   *       high address         high address
   *       |          |         |          | Caller frame
   *       |          |         |          |
   *  +    |arg 0     |         |arg 0     |
   *  +    |arg 1     |         |arg 1     |
   *  +    |...       |         |...       |
   *  +8   |arg n-1   |         |arg n-1   |
   *  +4   |returnAddr|         |returnAddr|
   *   0   +          +         +saved FP  + <---- FP for glue frame
   *  -4   |          |         |methodID  |
   *  -8   |          |         |saved EDI |
   *  -C   |          |         |saved EBX |
   *  -10  |          |         |          |
   *
   * </pre>
   */
  static void prepareStackHeader(Assembler asm, RVMMethod method, int compiledMethodId) {

    // set 2nd word of header = return address already pushed by CALL
    asm.emitPUSH_RegDisp(PR, ArchEntrypoints.framePointerField.getOffset());

    // start new frame:  set FP to point to the new frame
    ProcessorLocalState.emitMoveRegToField(asm, ArchEntrypoints.framePointerField.getOffset(), SP);

    // set first word of header: method ID
    asm.emitMOV_RegDisp_Imm(SP, Offset.fromIntSignExtend(STACKFRAME_METHOD_ID_OFFSET), compiledMethodId);

    // save nonvolatile registrs: EDI, EBX, EBP
    asm.emitMOV_RegDisp_Reg(SP, EDI_SAVE_OFFSET, EDI);
    asm.emitMOV_RegDisp_Reg(SP, EBX_SAVE_OFFSET, EBX);
    asm.emitMOV_RegDisp_Reg(SP, EBP_SAVE_OFFSET, EBP);

    asm.emitMOV_Reg_Reg(EBP, SP); // Establish EBP as the framepointer for use in the rest of the glue frame
  }

  /**************************************************************
   * Process the arguments.
   *
   * <pre>
   *   -insert the 2 JNI args
   *   -replace pointers
   *   -reverse the order of the args from Java to fit the C convention
   *   -
   *
   *         before               after
   *
   *       high address         high address
   *       |          |         |          | Caller frame
   *       |          |         |          |
   *  +    |arg 0     |         |arg 0     |    -> firstParameterOffset
   *  +    |arg 1     |         |arg 1     |
   *  +    |...       |         |...       |
   *  +8   |arg n-1   |         |arg n-1   |
   *  +4   |returnAddr|         |returnAddr|
   *   0   +saved FP  +         +saved FP  + <---- FP for glue frame
   *  -4   |methodID  |         |methodID  |
   *  -8   |saved EDI |         |saved EDI |    -> STACKFRAME_BODY_OFFSET = -8
   *  -C   |saved EBX |         |saved EBX |
   *       |          |         |align pad |
   *  -10  |          |         |returnAddr|  (return from OutOfLine to generated epilog)
   *  -14  |          |         |saved PR  |
   *  -18  |          |         |arg n-1   |  reordered args to native method (firstLocalOffset
   *  -1C  |          |         | ...      |  ...
   *  -20  |          |         |arg 1     |  ...
   *  -24  |          |         |arg 0     |  ...
   *  -28  |          |         |class/obj |  required second arg
   *  -2C  |          |   SP -> |jniEnv    |  required first arg  (emptyStackOffset)
   *  -30  |          |         |          |
   *       |          |         |          |
   *        low address          low address
   * </pre>
   */
  static void storeParametersForLintel(Assembler asm, RVMMethod method) {
    RVMClass klass = method.getDeclaringClass();
    int parameterWords = method.getParameterWords();
    int savedRegistersSize = SAVED_GPRS << LG_WORDSIZE;
    int firstLocalOffset = STACKFRAME_BODY_OFFSET - savedRegistersSize;
    Offset emptyStackOffset =
        Offset.fromIntSignExtend(firstLocalOffset - ((parameterWords + 2) << LG_WORDSIZE) + WORDSIZE);
    Offset firstParameterOffset =
        Offset.fromIntSignExtend(STACKFRAME_BODY_OFFSET + STACKFRAME_HEADER_SIZE + (parameterWords << LG_WORDSIZE));

    TypeReference[] types = method.getParameterTypes();   // does NOT include implicit this or class ptr
    int numArguments = types.length;                // number of arguments for this method
    int numRefArguments = 1;                        // initialize with count of 1 for the JNI arg
    int numFloats = 0;                              // number of float or double arguments

    // quick count of number of references
    for (int i = 0; i < numArguments; i++) {
      if (types[i].isReferenceType()) {
        numRefArguments++;
      }
      if (types[i].isFloatType() || types[i].isDoubleType()) {
        numFloats++;
      }
    }

    // first push the parameters passed in registers back onto the caller frame
    // to free up the registers for use
    // The number of registers holding parameter is
    // RegisterConstants.NUM_PARAMETER_GPRS
    // Their indices are in RegisterConstants.VOLATILE_GPRS[]
    int gpr = 0;
    // note that firstParameterOffset does not include "this"
    Offset parameterOffset = firstParameterOffset;

    // handle the "this" parameter
    if (!method.isStatic()) {
      asm.emitMOV_RegDisp_Reg(EBP, firstParameterOffset.plus(WORDSIZE), VOLATILE_GPRS[gpr]);
      gpr++;
    }

    for (int i = 0; i < numArguments && gpr < NUM_PARAMETER_GPRS; i++) {
      if (types[i].isDoubleType()) {
        parameterOffset = parameterOffset.minus(2 * WORDSIZE);
      } else if (types[i].isFloatType()) {
        parameterOffset = parameterOffset.minus(WORDSIZE);
      } else if (types[i].isLongType()) {
        if (gpr < NUM_PARAMETER_GPRS) {   // get the hi word
          asm.emitMOV_RegDisp_Reg(EBP, parameterOffset, VOLATILE_GPRS[gpr]);
          gpr++;
          parameterOffset = parameterOffset.minus(WORDSIZE);
        }
        if (gpr < NUM_PARAMETER_GPRS) {    // get the lo word
          asm.emitMOV_RegDisp_Reg(EBP, parameterOffset, VOLATILE_GPRS[gpr]);
          gpr++;
          parameterOffset = parameterOffset.minus(WORDSIZE);
        }
      } else {
        if (gpr < NUM_PARAMETER_GPRS) {   // all other types fit in one word
          asm.emitMOV_RegDisp_Reg(EBP, parameterOffset, VOLATILE_GPRS[gpr]);
          gpr++;
          parameterOffset = parameterOffset.minus(WORDSIZE);
        }
      }
    }

    // bump SP to set aside room for the args + 2 additional JNI args
    asm.emitADD_Reg_Imm(SP, emptyStackOffset.toInt());

    // SP should now point to the bottom of the argument stack,
    // which is arg[n-1]

    // Prepare the side stack to hold new refs
    // Leave S0 holding the threads' JNIEnvironment
    ProcessorLocalState.emitMoveFieldToReg(asm, S0, Entrypoints.activeThreadField.getOffset());
    asm.emitMOV_Reg_RegDisp(S0, S0, Entrypoints.jniEnvField.getOffset());        // S0 <- jniEnv

    // save PR in the jniEnv for JNI call from native
    ProcessorLocalState.emitStoreProcessor(asm, S0, Entrypoints.JNIEnvSavedPRField.getOffset());

    // save JNIEnvironemt in stack frame so we can find it when we return
    asm.emitMOV_RegDisp_Reg(EBP, JNI_ENV_OFFSET, S0);

    // save FP for glue frame in JNI env - used by GC when in C
    asm.emitMOV_RegDisp_Reg(S0, Entrypoints.JNITopJavaFPField.getOffset(), EBP);  // jniEnv.JNITopJavaFP <- FP

    //********************************************
    // Between HERE and THERE, S0 and T0 are in use
    // S0 holds the JNIEnvironemnt for the thread.
    // T0 holds the address to TOP of JNIRefs stack
    //    (set up by startJNIrefForProlog, used by pushJNIRef)
    // >>>> HERE <<<<
    startJNIrefForProlog(asm, numRefArguments);

    // Insert the JNIEnv* arg at the first entry:
    // This is an interior pointer to JNIEnvironment, which is held in S0.
    asm.emitMOV_Reg_Reg(EBX, S0);
    asm.emitADD_Reg_Imm(EBX, Entrypoints.JNIExternalFunctionsField.getOffset().toInt());
    asm.emitMOV_RegDisp_Reg(EBP, emptyStackOffset, EBX);                  // store as 1st arg

    // Insert the JNI arg at the second entry: class or object as a jref index
    if (method.isStatic()) {
      // For static method, push on arg stack the RVMClass object
      //    jtoc[tibOffset] -> class TIB ptr -> first TIB entry -> class object -> classForType
      Offset klassOffset = Offset.fromIntSignExtend(Statics.findOrCreateObjectLiteral(klass.getClassForType()));
      // push java.lang.Class object for klass
      asm.emitMOV_Reg_Abs(EBX, Magic.getTocPointer().plus(klassOffset));
    } else {
      // For nonstatic method, "this" pointer should be the first arg in the caller frame,
      // make it the 2nd arg in the glue frame
      asm.emitMOV_Reg_RegDisp(EBX, EBP, firstParameterOffset.plus(WORDSIZE));
    }

    // Generate the code to push this pointer in ebx on to the JNIRefs stack
    // and use the JREF index in its place
    // Assume: S0 is the jniEnv pointer (left over from above)
    //         T0 contains the address to TOP of JNIRefs stack
    // Kill value in ebx
    // On return, ebx contains the JREF index
    pushJNIref(asm);
    asm.emitMOV_RegDisp_Reg(EBP, emptyStackOffset.plus(WORDSIZE), EBX);  // store as 2nd arg

    // Now fill in the rest:  copy parameters from caller frame into glue frame
    // in reverse order for C
    int i = parameterWords - 1;
    int fpr = numFloats - 1;
    for (int argIndex = numArguments - 1; argIndex >= 0; argIndex--) {

      // for reference, substitute with a jref index
      if (types[argIndex].isReferenceType()) {
        asm.emitMOV_Reg_RegDisp(EBX, EBP, firstParameterOffset.minus(i * WORDSIZE));
        asm.emitCMP_Reg_Imm(EBX, 0);
        ForwardReference beq = asm.forwardJcc(Assembler.EQ);
        pushJNIref(asm);
        beq.resolve(asm);
        asm.emitMOV_RegDisp_Reg(EBP, emptyStackOffset.plus(WORDSIZE * (2 + i)), EBX);
        i--;

        // for float and double, the first NUM_PARAMETER_FPRS args have
        // been loaded in the FPU stack, need to pop them from there
      } else if (types[argIndex].isDoubleType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
          // pop this 2-word arg from the FPU stack
          if (SSE2_FULL) {
            asm.emitMOVSD_RegDisp_Reg(EBP, emptyStackOffset.plus(WORDSIZE * (2 + i - 1)), XMM.lookup(fpr));
          } else {
            asm.emitFSTP_RegDisp_Reg_Quad(EBP, emptyStackOffset.plus(WORDSIZE * (2 + i - 1)), FP0);
          }
        } else {
          // copy this 2-word arg from the caller frame
          asm.emitMOV_Reg_RegDisp(EBX, EBP, firstParameterOffset.minus(i * WORDSIZE));
          asm.emitMOV_RegDisp_Reg(EBP, emptyStackOffset.plus(WORDSIZE * (2 + i - 1)), EBX);
          asm.emitMOV_Reg_RegDisp(EBX, EBP, firstParameterOffset.minus((i - 1) * WORDSIZE));
          asm.emitMOV_RegDisp_Reg(EBP, emptyStackOffset.plus(WORDSIZE * (2 + i)), EBX);
        }
        i -= 2;
        fpr--;
      } else if (types[argIndex].isFloatType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
          // pop this 1-word arg from the FPU stack
          if (SSE2_FULL) {
            asm.emitMOVSS_RegDisp_Reg(EBP, emptyStackOffset.plus(WORDSIZE * (2 + i)), XMM.lookup(fpr));
          } else {
            asm.emitFSTP_RegDisp_Reg(EBP, emptyStackOffset.plus(WORDSIZE * (2 + i)), FP0);
          }
        } else {
          // copy this 1-word arg from the caller frame
          asm.emitMOV_Reg_RegDisp(EBX, EBP, firstParameterOffset.minus(i * WORDSIZE));
          asm.emitMOV_RegDisp_Reg(EBP, emptyStackOffset.plus(WORDSIZE * (2 + i)), EBX);
        }
        i--;
        fpr--;
      } else if (types[argIndex].isLongType()) {
        //  copy other 2-word parameters: observe the high/low order when moving
        asm.emitMOV_Reg_RegDisp(EBX, EBP, firstParameterOffset.minus(i * WORDSIZE));
        asm.emitMOV_RegDisp_Reg(EBP, emptyStackOffset.plus(WORDSIZE * (2 + i - 1)), EBX);
        asm.emitMOV_Reg_RegDisp(EBX, EBP, firstParameterOffset.minus((i - 1) * WORDSIZE));
        asm.emitMOV_RegDisp_Reg(EBP, emptyStackOffset.plus(WORDSIZE * (2 + i)), EBX);
        i -= 2;
      } else {
        // copy other 1-word parameters
        asm.emitMOV_Reg_RegDisp(EBX, EBP, firstParameterOffset.minus(i * WORDSIZE));
        asm.emitMOV_RegDisp_Reg(EBP, emptyStackOffset.plus(WORDSIZE * (2 + i)), EBX);
        i--;
      }
    }

    // Now set the top offset based on how many values we actually pushed.
    asm.emitSUB_Reg_RegDisp(T0, S0, Entrypoints.JNIRefsField.getOffset());
    asm.emitMOV_RegDisp_Reg(S0, Entrypoints.JNIRefsTopField.getOffset(), T0);

    // >>>> THERE <<<<
    // End use of T0 and S0
  }

  /**************************************************************
   * Generate code to convert a pointer value to a JREF index.
   *
   * <pre>
   * This includes the following steps:
   *   (1) start by calling startJNIrefForProlog()
   *   (2) for each reference, put it in ebx and call pushJNIref()
   *       to convert; the handler will be left in ebx
   *
   *   +-------+
   *   |       |  <-JNIRefsMax (byte index of last entry)
   *   |       |
   *   |       |
   *   |       |  <-JNIRefsTop (byte index of valid top entry)
   *   |       |
   *   |       |
   *   |FPindex|  <-JNIRefsSavedFP (byte index of Java to C transition)
   *   |       |
   *   |       |
   *   |       |
   *   |       |
   *   |       |
   *   |       |
   *   |       |  <-JNIRefs
   *   +-------+
   * </pre>
   */

  /**
   * Start a new frame for this Java to C transition.
   * <pre>
   * Expect:
   *    -S0 contains a pointer to the Thread.jniEnv
   * Perform these steps:
   *    -push current SavedFP index
   *    -set SaveFP index <- current TOP
   * Leave registers ready for more push onto the jniEnv.JNIRefs array
   *    -S0 holds jniEnv so we can update jniEnv.JNIRefsTop and
   *    -T0 holds address of top =  starting address of jniEnv.JNIRefs array + jniEnv.JNIRefsTop
   *     T0 is to be incremented before each push
   *    S0              ebx                         T0
   *  jniEnv
   *    .         jniEnv.JNIRefs             jniEnv.JNIRefsTop
   *    .               .                    jniEnv.JNIRefsTop + 4
   *    .         jniEnv.JNIRefsSavedFP            .
   *    .               .                    jniEnv.JNIRefsTop
   *    .               .                    address(JNIRefsTop)
   *    .
   * </pre>
   */
  static void startJNIrefForProlog(Assembler asm, int numRefsExpected) {

    // on entry, S0 contains a pointer to the Thread.jniEnv
    asm.emitMOV_Reg_RegDisp(EBX, S0, Entrypoints.JNIRefsField.getOffset());    // ebx <- JNIRefs base

    // get and check index of top for overflow
    asm.emitMOV_Reg_RegDisp(T0, S0, Entrypoints.JNIRefsTopField.getOffset());  // T0 <- index of top
    asm.emitADD_Reg_Imm(T0, numRefsExpected * WORDSIZE);                // increment index of top
    asm.emitCMP_Reg_RegDisp(T0,
                            S0,
                            Entrypoints.JNIRefsMaxField.getOffset());   // check against JNIRefsMax for overflow
    // TODO:  Do something if overflow!!!

    asm.emitADD_RegDisp_Imm(S0, Entrypoints.JNIRefsTopField.getOffset(), WORDSIZE); // increment index of top
    asm.emitMOV_Reg_RegDisp(T0, S0, Entrypoints.JNIRefsTopField.getOffset());  // T0 <- index of top
    asm.emitADD_Reg_Reg(T0, EBX);                                       // T0 <- address of top (not index)

    // start new frame:  push current JNIRefsSavedFP onto stack and set it to the new top index
    asm.emitMOV_Reg_RegDisp(EBX, S0, Entrypoints.JNIRefsSavedFPField.getOffset()); // ebx <- jniEnv.JNIRefsSavedFP
    asm.emitMOV_RegInd_Reg(T0, EBX);                                   // push (T0) <- ebx
    asm.emitMOV_Reg_RegDisp(T0, S0, Entrypoints.JNIRefsTopField.getOffset());   // reload T0 <- index of top
    asm.emitMOV_RegDisp_Reg(S0,
                            Entrypoints.JNIRefsSavedFPField.getOffset(),
                            T0); // jniEnv.JNIRefsSavedFP <- index of top

    // leave T0 with address pointing to the top of the frame for more push later
    asm.emitADD_Reg_RegDisp(T0,
                            S0,
                            Entrypoints.JNIRefsField.getOffset());       // recompute T0 <- address of top (not index)
  }

  /**
   * Push a pointer value onto the JNIRefs array.
   *
   * <pre>
   * Expect:
   *   -T0 pointing to the address of the valid top
   *   -the pointer value in register ebx
   *   -the space in the JNIRefs array has checked for overflow
   *   by startJNIrefForProlog()
   * Perform these steps:
   *   -increment the JNIRefsTop index in ebx by 4
   *   -push a pointer value in ebx onto the top of the JNIRefs array
   *   -put the JNIRefsTop index into the sourceReg as the replacement for the pointer
   * </pre>
   * Note:  jniEnv.JNIRefsTop is not updated yet
   */
  static void pushJNIref(Assembler asm) {
    asm.emitADD_Reg_Imm(T0, WORDSIZE);                            // increment top address
    asm.emitMOV_RegInd_Reg(T0, EBX);                               // store ref at top
    asm.emitMOV_Reg_Reg(EBX, T0);                                 // replace ref in ebx with top address
    asm.emitSUB_Reg_RegDisp(EBX, S0, Entrypoints.JNIRefsField.getOffset());   // subtract base address to get offset
  }

  /**
   * Generate the code to pop the frame in JNIRefs array for this Java to C transition.
   *
   * <pre>
   * Expect:
   *  -PR register is valid
   *  -S0 contains a pointer to the Thread.jniEnv
   *  -EBX and T1 are available as scratch registers
   * Perform these steps:
   *  -jniEnv.JNIRefsTop <- jniEnv.JNIRefsSavedFP - 4
   *  -jniEnv.JNIRefsSavedFP <- (jniEnv.JNIRefs + jniEnv.JNIRefsSavedFP)
   * </pre>
   */
  static void popJNIrefForEpilog(Assembler asm) {
    // on entry, S0 contains a pointer to the Thread.jniEnv
    // set TOP to point to entry below the last frame
    asm.emitMOV_Reg_RegDisp(T1, S0, Entrypoints.JNIRefsSavedFPField.getOffset());    // ebx <- JNIRefsSavedFP
    asm.emitMOV_RegDisp_Reg(S0, Entrypoints.JNIRefsTopField.getOffset(), T1);        // JNIRefsTop <- ebx
    asm.emitSUB_RegDisp_Imm(S0, Entrypoints.JNIRefsTopField.getOffset(), WORDSIZE);  // JNIRefsTop -= 4

    // load savedFP with the index to the last frame
    asm.emitMOV_Reg_RegDisp(EBX, S0, Entrypoints.JNIRefsField.getOffset());          // ebx <- JNIRefs base
    asm.emitMOV_Reg_RegIdx(EBX,
                           EBX,
                           T1,
                           Assembler.BYTE,
                           Offset.zero());                                 // ebx <- (JNIRefs base + SavedFP index)
    asm.emitMOV_RegDisp_Reg(S0, Entrypoints.JNIRefsSavedFPField.getOffset(), EBX);   // JNIRefsSavedFP <- ebx
  }

  /*****************************************************************
   * Handle the C to Java transition:  JNI methods in JNIFunctions.java.
   *
   * <pre>
   * NOTE:
   *   -We need PR to access Java environment; we can get it from the
   *    JNIEnv* (which is an interior pointer to the JNIEnvironment)
   *   -Unlike the powerPC scheme which has a special prolog preceding
   *    the normal Java prolog, the Intel scheme replaces the Java prolog
   *    completely with the special prolog
   *
   *            Stack on entry            Stack at end of prolog after call
   *             high memory                       high memory
   *            |            |                   |            |
   *    EBP ->  |saved FP    |                   |saved FP    |
   *            |  ...       |                   |  ...       |
   *            |            |                   |            |
   *            |arg n-1     |                   |arg n-1     |
   * native     |  ...       |                   |  ...       |
   * caller     |arg 0       |                   |arg 0       |
   *    ESP ->  |return addr |                   |return addr |
   *            |            |           EBP ->  |saved FP    |
   *            |            |                   |methodID    | normal MethodID for JNI function
   *            |            |                   |saved JavaFP| offset to preceeding java frame
   *            |            |                   |saved edi   | to be used for nonvolatile
   *            |            |                   |  "   ebx   | to be used for nonvolatile
   *            |            |                   |  "   ecx   | to be used for scrach
   *            |            |                   |  "   esi   | to be used for PR
   *            |            |                   |arg 0       | copied in reverse order
   *            |            |                   |  ...       |
   *            |            |           ESP ->  |arg n-1     |
   *            |            |                   |            | normally compiled Java code continue
   *            |            |                   |            |
   *            |            |                   |            |
   *            |            |                   |            |
   *             low memory                        low memory
   * </pre>
   */
  public static void generateGlueCodeForJNIMethod(Assembler asm, NormalMethod method, int methodID) {
    // set 2nd word of header = return address already pushed by CALL
    // NOTE: C calling convention is that EBP contains the caller's framepointer.
    //       Therefore our C to Java transition frames must follow this protocol,
    //       not the RVM protocol in which the caller's framepointer is in
    //       pr.framePointer and EBP is a nonvolatile register.
    asm.emitPUSH_Reg(EBP);

    // start new frame:  set FP to point to the new frame
    asm.emitMOV_Reg_Reg(EBP, SP);

    // set first word of header: method ID
    asm.emitPUSH_Imm(methodID);
    // buy space for the rest of the header (values stored later)
    asm.emitSUB_Reg_Imm(SP, STACKFRAME_HEADER_SIZE - 2 * WORDSIZE);

    // save registers that will be used in RVM, to be restored on return to C
    // TODO: I don't think we need to do this: C has no nonvolatile registers on Linux/x86 --dave
    // TODO: DAVE
    asm.emitPUSH_Reg(EDI);
    asm.emitPUSH_Reg(EBX);
    asm.emitPUSH_Reg(S0);
    ProcessorLocalState.emitPushProcessor(asm);

    // Adjust first param from JNIEnv* to JNIEnvironment.
    asm.emitSUB_RegDisp_Imm(EBP,
                            Offset.fromIntSignExtend(2 * WORDSIZE),
                            Entrypoints.JNIExternalFunctionsField.getOffset().toInt());

    // copy the arguments in reverse order
    TypeReference[] types = method.getParameterTypes();   // does NOT include implicit this or class ptr
    int numArguments = types.length;                // number of arguments for this method
    Offset argOffset = Offset.fromIntSignExtend(2 * WORDSIZE);           // add 2 to get to arg area in caller frame
    for (int i = 0; i < numArguments; i++) {
      if (types[i].isLongType() || types[i].isDoubleType()) {
        // handle 2-words case:
        asm.emitMOV_Reg_RegDisp(EBX, EBP, argOffset.plus(WORDSIZE));
        asm.emitPUSH_Reg(EBX);
        asm.emitMOV_Reg_RegDisp(EBX, EBP, argOffset);
        asm.emitPUSH_Reg(EBX);
        argOffset = argOffset.plus(2 * WORDSIZE);
      } else {
        // Handle 1-word case:
        asm.emitMOV_Reg_RegDisp(EBX, EBP, argOffset);
        asm.emitPUSH_Reg(EBX);
        argOffset = argOffset.plus(WORDSIZE);
      }
    }

    // START of code sequence to atomically change processor status from IN_NATIVE
    // to IN_JAVA, looping in a call to sysVirtualProcessorYield if BLOCKED_IN_NATIVE
    int retryLabel = asm.getMachineCodeIndex();     // backward branch label

    // Restore PR from JNIEnvironment
    asm.emitMOV_Reg_RegDisp(EBX, EBP, Offset.fromIntSignExtend(2 * WORDSIZE));   // pick up arg 0 (from callers frame)
    ProcessorLocalState.emitLoadProcessor(asm, EBX, Entrypoints.JNIEnvSavedPRField.getOffset());

    // T0 gets PR.statusField
    ProcessorLocalState.emitMoveFieldToReg(asm, T0, Entrypoints.vpStatusField.getOffset());
    asm.emitCMP_Reg_Imm(T0, Processor.IN_NATIVE);      // jmp if still IN_NATIVE
    ForwardReference fr = asm.forwardJcc(Assembler.EQ);       // if so, skip 3 instructions

    // blocked in native, do pthread yield
    asm.emitMOV_Reg_Abs(T0, Magic.getTocPointer().plus(Entrypoints.the_boot_recordField.getOffset()));  // T0<-bootrecord addr
    asm.emitCALL_RegDisp(T0, Entrypoints.sysVirtualProcessorYieldIPField.getOffset());
    asm.emitJMP_Imm(retryLabel);                          // retry from beginning

    fr.resolve(asm);      // branch here if IN_NATIVE, attempt to go to IN_JAVA

    // T0 (EAX) contains "old value" (required for CMPXCNG instruction)
    // S0 contains address of status word to be swapped
    asm.emitMOV_Reg_Imm(T1, Processor.IN_JAVA);  // T1<-new value (IN_JAVA)
    ProcessorLocalState.emitCompareAndExchangeField(asm,
                                                       Entrypoints.vpStatusField.getOffset(),
                                                       T1); // atomic compare-and-exchange
    asm.emitJCC_Cond_Imm(Assembler.NE, retryLabel);

    // END of code sequence to change state from IN_NATIVE to IN_JAVA

    // status is now IN_JAVA. GC can not occur while we execute on a processor
    // in this state, so it is safe to access fields of objects.
    // RVM PR register has been restored and EBX contains a pointer to
    // the thread's JNIEnvironment.

    // done saving, bump SP to reserve room for the local variables
    // SP should now be at the point normally marked as emptyStackOffset
    int numLocalVariables = method.getLocalWords() - method.getParameterWords();
    asm.emitSUB_Reg_Imm(SP, (numLocalVariables << LG_WORDSIZE));

    // Retrieve -> preceeding "top" java FP from jniEnv and save in current
    // frame of JNIFunction
    asm.emitMOV_Reg_RegDisp(S0, EBX, Entrypoints.JNITopJavaFPField.getOffset());

    // get offset from current FP and save in hdr of current frame
    asm.emitSUB_Reg_Reg(S0, EBP);
    asm.emitMOV_RegDisp_Reg(EBP, SAVED_JAVA_FP_OFFSET, S0);

    // put framePointer in VP following Jikes RVM conventions.
    ProcessorLocalState.emitMoveRegToField(asm, ArchEntrypoints.framePointerField.getOffset(), EBP);

    // at this point: PR has been restored &
    // processor status = IN_JAVA,
    // arguments for the call have been setup, space on the stack for locals
    // has been acquired.

    // finally proceed with the normal Java compiled code
    // skip the thread switch test for now, see BaselineCompilerImpl.genThreadSwitchTest(true)
    asm.emitNOP(); // end of prologue marker
  }

  public static void generateEpilogForJNIMethod(Assembler asm, RVMMethod method) {
    // assume RVM PR regs still valid. potentially T1 & T0 contain return
    // values and should not be modified. we use regs saved in prolog and restored
    // before return to do whatever needs to be done.

    // if returning long, switch the order of the hi/lo word in T0 and T1
    if (method.getReturnType().isLongType()) {
      asm.emitPUSH_Reg(T1);
      asm.emitMOV_Reg_Reg(T1, T0);
      asm.emitPOP_Reg(T0);
    } else {
      if (SSE2_FULL) {
        // Marshall from XMM0 -> FP0
        if (method.getReturnType().isDoubleType()) {
          asm.emitMOVSD_RegDisp_Reg(PR, Entrypoints.scratchStorageField.getOffset(), XMM0);
          asm.emitFLD_Reg_RegDisp_Quad(FP0, PR, Entrypoints.scratchStorageField.getOffset());
        } else if (method.getReturnType().isFloatType()) {
          asm.emitMOVSS_RegDisp_Reg(PR, Entrypoints.scratchStorageField.getOffset(), XMM0);
          asm.emitFLD_Reg_RegDisp(FP0, PR, Entrypoints.scratchStorageField.getOffset());
        }
      }
    }

    // current processor status is IN_JAVA, so we only GC at yieldpoints

    // S0 <- JNIEnvironment
    ProcessorLocalState.emitMoveFieldToReg(asm, S0, Entrypoints.activeThreadField.getOffset());
    asm.emitMOV_Reg_RegDisp(S0, S0, Entrypoints.jniEnvField.getOffset());

    // set jniEnv TopJavaFP using value saved in frame in prolog
    asm.emitMOV_Reg_RegDisp(EDI, EBP, SAVED_JAVA_FP_OFFSET);      // EDI<-saved TopJavaFP (offset)
    asm.emitADD_Reg_Reg(EDI, EBP);                                // change offset from FP into address
    asm.emitMOV_RegDisp_Reg(S0, Entrypoints.JNITopJavaFPField.getOffset(), EDI); // jniEnv.TopJavaFP <- EDI

    // in case thread has migrated to different PR, reset saved PRs to current PR
    ProcessorLocalState.emitStoreProcessor(asm, S0, Entrypoints.JNIEnvSavedPRField.getOffset());

    // change current processor status to IN_NATIVE
    ProcessorLocalState.emitMoveImmToField(asm, Entrypoints.vpStatusField.getOffset(), Processor.IN_NATIVE);

    // reload native/C nonvolatile regs - saved in prolog
    // what about FPRs
    // TODO: DAVE we really don't need to do this.  C has no nonvols on Linux/x86
    ProcessorLocalState.emitPopProcessor(asm);
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(EBX);
    asm.emitPOP_Reg(EDI);

    // NOTE: C expects the framepointer to be restored to EBP, so
    //       the epilogue for the C to Java glue code must follow that
    //       convention, not the RVM one!
    //       Also note that RVM treats EBP is a nonvolatile, so we don't
    //       explicitly save/restore it.
    asm.emitMOV_Reg_Reg(SP, EBP); // discard current stack frame
    asm.emitPOP_Reg(EBP);
    asm.emitRET();                // return to caller
  }
}
