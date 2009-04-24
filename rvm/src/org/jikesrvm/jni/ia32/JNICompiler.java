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
package org.jikesrvm.jni.ia32;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
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
import org.jikesrvm.ia32.ThreadLocalState;
import org.jikesrvm.jni.JNICompiledMethod;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * This class compiles the prolog and epilog for all code that makes
 * the transition between Java and Native C for the 2 cases:
 * <ul>
 * <li>from Java to C:  all user-defined native methods</li>
 * <li>C to Java:  all JNI functions in {@link org.jikesrvm.jni.JNIFunctions}</li>
 * </ul>
 * When performing the transitions the values in registers and on the stack need
 * to be treated with care, but also when transitioning into Java we need to do
 * a spin-wait if a GC is underway.
 *
 * Transitioning from Java to C then back:
 * <ol>
 * <li>Set up stack frame and save non-volatile registers<li>
 * <li>Move all native method arguments on to stack (NB at this point all non-volatile state is saved)</li>
 * <li>Set up jniEnv</li>
 * <li>Record the frame pointer of the last Java frame (this) in the jniEnv</li>
 * <li>Set up JNI refs, a stack that holds references accessed via JNI</li>
 * <li>Call out to convert reference arguments to IDs</li>
 * <li>Set processor as being "in native"</li>
 * <li>Set up stack frame and registers for transition to C</li>
 * <li>Call out to C</li>
 * <li>Save result to stack</li>
 * <li>Transition back from "in native" to "in Java", take care that the
 *     Processor isn't "blocked in native", ie other processors have decided to
 *     start a GC and we're not permitted to execute Java code whilst this
 *     occurs</li>
 * <li>Convert a reference result (currently a JNI ref) into a true reference</li>
 * <li>Release JNI refs</li>
 * <li>Restore stack and place result in register</li>
 * <ol>
 *
 * Prologue generation from C to Java:
 * <ol>
 * <li>Set up stack frame with C arguments in Jikes RVM convention</li>
 * <li>Set up extra stack frame entry that records the previous last top Java FP</li>
 * <li>Transition from "in native" to "in Java" taking care of blocked (in GC)
       case</li>
 * </ol>
 *
 * Epilogue generation from Java to C:
 * <ol>
 * <li>Restore record of the previous last top Java FP in the jniEnv</li>
 * <li>Transition from "in Java" to "in native"</li>
 * <li>Set up result in registers. NB. JNIFunctions don't return references but
 *     JNI refs directly, so we don't need to transition these</li>
 * </ol>
 */
public abstract class JNICompiler implements BaselineConstants {

  /** Dummy field to force compilation of the exception deliverer */
  private org.jikesrvm.jni.ia32.JNIExceptionDeliverer unused;

  /** Offset of external functions field in JNIEnvironment */
  private static final  int jniExternalFunctionsFieldOffset =
    Entrypoints.JNIExternalFunctionsField.getOffset().toInt();

  // --- Java to C fields ---

  /** Location of non-volatile EDI register when saved to stack */
  static final Offset EDI_SAVE_OFFSET = Offset.fromIntSignExtend(STACKFRAME_BODY_OFFSET);
  /** Location of non-volatile EBX register when saved to stack */
  static final Offset EBX_SAVE_OFFSET = EDI_SAVE_OFFSET.minus(WORDSIZE);
  /** Location of non-volatile EBP register when saved to stack */
  static final Offset EBP_SAVE_OFFSET = EBX_SAVE_OFFSET.minus(WORDSIZE);
  /** Location of an extra copy of the RVMThread.jniEnv when saved to stack */
  private static final Offset JNI_ENV_OFFSET = EBP_SAVE_OFFSET.minus(WORDSIZE);
  /** Location of a saved version of the field JNIEnvironment.basePointerOnEntryToNative */
  private static final Offset BP_ON_ENTRY_OFFSET = JNI_ENV_OFFSET.minus(WORDSIZE);

  // --- C to Java fields ---
  /**
   * Stack frame location for saved JNIEnvironment.JNITopJavaFP that
   * will be clobbered by a transition from Java to C.  Only used in
   * the prologue & epilogue for JNIFunctions.
   */
  private static final Offset SAVED_JAVA_FP_OFFSET = Offset.fromIntSignExtend(STACKFRAME_BODY_OFFSET);

  /**
   * The following is used in BaselineCompilerImpl to compute offset to first local.
   * Number of non-volatile GPRs and FPRs and then 1 slot for the SAVED_JAVA_FP.
   */
  public static final int SAVED_GPRS_FOR_JNI = NATIVE_NONVOLATILE_GPRS.length + NATIVE_NONVOLATILE_FPRS.length + 1;

  /**
   * Compile a method to handle the Java to C transition and back
   * Transitioning from Java to C then back:
   * <ol>
   * <li>Set up stack frame and save non-volatile registers<li>
   * <li>Set up jniEnv - set up a register to hold JNIEnv and store
   *     the Processor in the JNIEnv for easy access</li>
   * <li>Move all native method arguments on to stack (NB at this point all
   *     non-volatile state is saved)</li>
   * <li>Record the frame pointer of the last Java frame (this) in the jniEnv</li>
   * <li>Call out to convert reference arguments to IDs</li>
   * <li>Set processor as being "in native"</li>
   * <li>Set up stack frame and registers for transition to C</li>
   * <li>Call out to C</li>
   * <li>Save result to stack</li>
   * <li>Transition back from "in native" to "in Java", take care that the
   *     Processor isn't "blocked in native", ie other processors have decided to
   *     start a GC and we're not permitted to execute Java code whilst this
   *     occurs</li>
   * <li>Convert a reference result (currently a JNI ref) into a true reference</li>
   * <li>Release JNI refs</li>
   * <li>Restore stack and place result in register</li>
   * <ol>
   */
  public static synchronized CompiledMethod compile(NativeMethod method) {
    // Meaning of constant offset into frame (assuming 4byte word size):
    // Stack frame:
    //        on entry          after prolog
    //
    //      high address        high address
    //      |          |        |          | Caller frame
    //      |          |        |          |
    // +    |arg 0     |        |arg 0     | <- firstParameterOffset
    // +    |arg 1     |        |arg 1     |
    // +    |...       |        |...       |
    // +8   |arg n-1   |        |arg n-1   | <- lastParameterOffset
    // +4   |returnAddr|        |returnAddr|
    //  0   +          +        +saved FP  + <- EBP/FP value in glue frame
    // -4   |          |        |methodID  |
    // -8   |          |        |saved EDI |
    // -C   |          |        |saved EBX |
    // -10  |          |        |saved EBP |
    // -14  |          |        |saved ENV |  (JNIEnvironment)
    // -18  |          |        |arg n-1   |  reordered args to native method
    // -1C  |          |        | ...      |  ...
    // -20  |          |        |arg 1     |  ...
    // -24  |          |        |arg 0     |  ...
    // -28  |          |        |class/obj |  required second arg to native method
    // -2C  |          |        |jni funcs |  required first arg to native method
    // -30  |          |        |          |
    //      |          |        |          |
    //      |          |        |          |
    //       low address         low address
    // Register values:
    // EBP    - after step 1 EBP holds a frame pointer allowing easy
    //          access to both this and the proceeding frame
    // ESP    - gradually floats down as the stack frame is initialized
    // S0/ECX - reference to the JNI environment after step 3

    JNICompiledMethod cm = (JNICompiledMethod)CompiledMethods.createCompiledMethod(method, CompiledMethod.JNI);
    ArchitectureSpecific.Assembler asm = new ArchitectureSpecific.Assembler(100 /*, true*/);   // some size for the instruction array

    Address nativeIP = method.getNativeIP();
    final Offset lastParameterOffset = Offset.fromIntSignExtend(2*WORDSIZE);
    //final Offset firstParameterOffset = Offset.fromIntSignExtend(WORDSIZE+(method.getParameterWords() << LG_WORDSIZE));
    final TypeReference[] args = method.getParameterTypes();

    // (1) Set up stack frame and save non-volatile registers

    // TODO:  check and resize stack once on the lowest Java to C transition
    // on the stack.  Not needed if we use the thread original stack

    // set 2nd word of header = return address already pushed by CALL
    asm.emitPUSH_RegDisp(THREAD_REGISTER, ArchEntrypoints.framePointerField.getOffset());

    // establish new frame
    ThreadLocalState.emitMoveRegToField(asm, ArchEntrypoints.framePointerField.getOffset(), SP);

    // set first word of header: method ID
    if (VM.VerifyAssertions) VM._assert(STACKFRAME_METHOD_ID_OFFSET == -WORDSIZE);
    asm.emitPUSH_Imm(cm.getId());

    // save nonvolatile registrs: EDI, EBX, EBP
    if (VM.VerifyAssertions) VM._assert(EDI_SAVE_OFFSET.toInt() == -2*WORDSIZE);
    asm.emitPUSH_Reg(EDI); // save nonvolatile EDI register
    if (VM.VerifyAssertions) VM._assert(EBX_SAVE_OFFSET.toInt() == -3*WORDSIZE);
    asm.emitPUSH_Reg(EBX); // save nonvolatile EBX register
    if (VM.VerifyAssertions) VM._assert(EBP_SAVE_OFFSET.toInt() == -4*WORDSIZE);
    asm.emitPUSH_Reg(EBP); // save nonvolatile EBP register

    // Establish EBP as the framepointer for use in the rest of the glue frame
    asm.emitLEA_Reg_RegDisp(EBP, SP, Offset.fromIntSignExtend(4*WORDSIZE));

    // (2) Set up jniEnv - set up a register to hold JNIEnv and store
    // the Processor in the JNIEnv for easy access

    // S0 = RVMThread.jniEnv
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegDisp(S0, THREAD_REGISTER, Entrypoints.jniEnvField.getOffset());
    } else {
      asm.emitMOV_Reg_RegDisp_Quad(S0, THREAD_REGISTER, Entrypoints.jniEnvField.getOffset());
    }
    if (VM.VerifyAssertions) VM._assert(JNI_ENV_OFFSET.toInt() == -5*WORDSIZE);
    asm.emitPUSH_Reg(S0); // save JNI Env for after call

    if (VM.VerifyAssertions) VM._assert(BP_ON_ENTRY_OFFSET.toInt() == -6*WORDSIZE);
    asm.emitPUSH_RegDisp(S0, Entrypoints.JNIEnvBasePointerOnEntryToNative.getOffset());
    // save BP into JNIEnv
    if (VM.BuildFor32Addr) {
      asm.emitMOV_RegDisp_Reg(S0, Entrypoints.JNIEnvBasePointerOnEntryToNative.getOffset(), EBP);
    } else {
      asm.emitMOV_RegDisp_Reg_Quad(S0, Entrypoints.JNIEnvBasePointerOnEntryToNative.getOffset(), EBP);
    }
    // (3) Move all native method arguments on to stack (NB at this
    // point all non-volatile state is saved)

    // (3.1) Count how many arguments could be passed in either FPRs or GPRs
    int numFprArgs=0;
    int numGprArgs=method.isStatic() ? 0 : 1;
    for (TypeReference arg : args) {
      if (arg.isFloatType() || arg.isDoubleType()) {
        numFprArgs++;
      } else if (VM.BuildFor32Addr && arg.isLongType()) {
        numGprArgs+=2;
      } else {
        numGprArgs++;
      }
    }
    // (3.2) Walk over arguments backwards pushing either from memory or registers
    Offset currentArg = lastParameterOffset;
    int argFpr=numFprArgs-1;
    int argGpr=numGprArgs-1;
    for (int i=args.length-1; i >= 0; i--) {
      TypeReference arg = args[i];
      if (arg.isFloatType()) {
        if (argFpr < PARAMETER_FPRS.length) {
          asm.emitPUSH_Reg(T0); // make space
          if (SSE2_FULL) {
            asm.emitMOVSS_RegInd_Reg(SP, (XMM)PARAMETER_FPRS[argFpr]);
          } else {
            asm.emitFSTP_RegInd_Reg(SP, (FPR)PARAMETER_FPRS[argFpr]);
          }
        } else {
          asm.emitPUSH_RegDisp(EBP, currentArg);
        }
        argFpr--;
      } else if (arg.isDoubleType()) {
        if (VM.BuildFor32Addr) {
          if (argFpr < PARAMETER_FPRS.length) {
            asm.emitPUSH_Reg(T0); // make space
            asm.emitPUSH_Reg(T0); // need 2 slots with 32bit addresses
            if (SSE2_FULL) {
              asm.emitMOVSD_RegInd_Reg(SP, (XMM)PARAMETER_FPRS[argFpr]);
            } else {
              asm.emitFSTP_RegInd_Reg_Quad(SP, (FPR)PARAMETER_FPRS[argFpr]);
            }
          } else {
            asm.emitPUSH_RegDisp(EBP, currentArg.plus(WORDSIZE));
            asm.emitPUSH_RegDisp(EBP, currentArg);  // need 2 slots with 32bit addresses
          }
        } else {
          if (argFpr < PARAMETER_FPRS.length) {
            asm.emitPUSH_Reg(T0); // make space
            if (SSE2_FULL) {
              asm.emitMOVSD_RegInd_Reg(SP, (XMM)PARAMETER_FPRS[argFpr]);
            } else {
              asm.emitFSTP_RegInd_Reg_Quad(SP, (FPR)PARAMETER_FPRS[argFpr]);
            }
          } else {
            asm.emitPUSH_RegDisp(EBP, currentArg);
          }
        }
        argFpr--;
        currentArg = currentArg.plus(WORDSIZE);
      } else if (VM.BuildFor32Addr && arg.isLongType()) {
        if (argGpr < PARAMETER_GPRS.length) {
          asm.emitPUSH_Reg(PARAMETER_GPRS[argGpr-1]);
          asm.emitPUSH_Reg(PARAMETER_GPRS[argGpr]);
        } else if (argGpr - 1 < PARAMETER_GPRS.length) {
          asm.emitPUSH_Reg(PARAMETER_GPRS[argGpr-1]);
          asm.emitPUSH_RegDisp(EBP, currentArg);
        } else {
          asm.emitPUSH_RegDisp(EBP, currentArg.plus(WORDSIZE));
          asm.emitPUSH_RegDisp(EBP, currentArg);
        }
        argGpr-=2;
        currentArg = currentArg.plus(WORDSIZE);
      } else {
        if (argGpr < PARAMETER_GPRS.length) {
          asm.emitPUSH_Reg(PARAMETER_GPRS[argGpr]);
        } else {
          asm.emitPUSH_RegDisp(EBP, currentArg);
        }
        argGpr--;
        if (VM.BuildFor64Addr && arg.isLongType()) {
          currentArg = currentArg.plus(WORDSIZE);
        }
      }
      currentArg = currentArg.plus(WORDSIZE);
    }
    // (3.3) push class or object argument
    if (method.isStatic()) {
      // push java.lang.Class object for klass
      Offset klassOffset = Offset.fromIntSignExtend(
          Statics.findOrCreateObjectLiteral(method.getDeclaringClass().getClassForType()));
      asm.emitPUSH_Abs(Magic.getTocPointer().plus(klassOffset));
    } else {
      if (VM.VerifyAssertions) VM._assert(argGpr == 0);
      asm.emitPUSH_Reg(PARAMETER_GPRS[0]);
    }
    // (3.4) push a pointer to the JNI functions that will be
    // dereferenced in native code
    asm.emitPUSH_Reg(S0);
    if (jniExternalFunctionsFieldOffset != 0) {
      if (VM.BuildFor32Addr) {
        asm.emitADD_RegInd_Imm(ESP, jniExternalFunctionsFieldOffset);
      } else {
        asm.emitADD_RegInd_Imm_Quad(ESP, jniExternalFunctionsFieldOffset);
      }
    }

    // (4) Call out to convert reference arguments to IDs, set thread as
    // being "in native" and record the frame pointer of the last Java frame
    // (this) in the jniEnv

    // Encode reference arguments into a long
    int encodedReferenceOffsets=0;
    for (int i=0, pos=0; i < args.length; i++, pos++) {
      TypeReference arg = args[i];
      if (arg.isReferenceType()) {
        if (VM.VerifyAssertions) VM._assert(pos < 32);
        encodedReferenceOffsets |= 1 << pos;
      } else if (arg.isLongType() || arg.isDoubleType()) {
        pos++;
      }
    }
    // Call out to JNI environment JNI entry
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegDisp(PARAMETER_GPRS[0], EBP, JNI_ENV_OFFSET);
    } else {
      asm.emitMOV_Reg_RegDisp_Quad(PARAMETER_GPRS[0], EBP, JNI_ENV_OFFSET);
    }
    asm.emitPUSH_Reg(PARAMETER_GPRS[0]);
    asm.emitMOV_Reg_Imm(PARAMETER_GPRS[1], encodedReferenceOffsets);
    asm.emitPUSH_Reg(PARAMETER_GPRS[1]);
    ObjectModel.baselineEmitLoadTIB(asm, S0.value(), PARAMETER_GPRS[0].value());
    asm.emitCALL_RegDisp(S0, Entrypoints.jniEntry.getOffset());

    // (5) Set up stack frame and registers for transition to C
    int argsPassedInRegister=0;
    if (VM.BuildFor64Addr) {
      int gpRegistersInUse=2;
      int fpRegistersInUse=0;
      boolean dataOnStack = false;
      asm.emitPOP_Reg(NATIVE_PARAMETER_GPRS[0]); // JNI env
      asm.emitPOP_Reg(NATIVE_PARAMETER_GPRS[1]); // Object/Class
      argsPassedInRegister+=2;
      for (TypeReference arg : method.getParameterTypes()) {
        if (arg.isFloatType()) {
          if (fpRegistersInUse < NATIVE_PARAMETER_FPRS.length) {
            // TODO: we can't have holes in the data that is on the stack, we need to shuffle it up
            if (dataOnStack) throw new Error("Unsupported native method parameter list");
            asm.emitMOVSS_Reg_RegInd((XMM)NATIVE_PARAMETER_FPRS[fpRegistersInUse], SP);
            asm.emitPOP_Reg(T0);
            fpRegistersInUse++;
            argsPassedInRegister++;
          } else {
            // no register available so we have data on the stack
            dataOnStack = true;
          }
        } else if (arg.isDoubleType()) {
          if (fpRegistersInUse < NATIVE_PARAMETER_FPRS.length) {
            // TODO: we can't have holes in the data that is on the stack, we need to shuffle it up
            if (dataOnStack) throw new Error("Unsupported native method parameter list");
            asm.emitMOVSD_Reg_RegInd((XMM)NATIVE_PARAMETER_FPRS[fpRegistersInUse], SP);
            asm.emitPOP_Reg(T0);
            asm.emitPOP_Reg(T0);
            fpRegistersInUse++;
            argsPassedInRegister+=2;
          } else {
            // no register available so we have data on the stack
            dataOnStack = true;
          }
        } else {
          if (gpRegistersInUse < NATIVE_PARAMETER_GPRS.length) {
            // TODO: we can't have holes in the data that is on the stack, we need to shuffle it up
            if (dataOnStack) throw new Error("Unsupported native method parameter list");
            asm.emitPOP_Reg(NATIVE_PARAMETER_GPRS[gpRegistersInUse]);
            gpRegistersInUse++;
            argsPassedInRegister++;
          } else {
            // no register available so we have data on the stack
            dataOnStack = true;
          }
        }
      }
    }

    // (6) Call out to C
    // move address of native code to invoke into T0
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_Imm(T0, nativeIP.toInt());
    } else {
      asm.emitMOV_Reg_Imm_Quad(T0, nativeIP.toLong());
    }
    // make the call to native code
    asm.emitCALL_Reg(T0);

    // (7) Discard parameters on stack
    // TODO: optimize stack adjustment
    if (VM.BuildFor32Addr) {
      // throw away args, class/this ptr and env
      int argsToThrowAway = method.getParameterWords()+2-argsPassedInRegister;
      if (argsToThrowAway != 0) {
        asm.emitADD_Reg_Imm(SP, argsToThrowAway << LG_WORDSIZE);
      }
    } else {
      // throw away args, class/this ptr and env
      int argsToThrowAway = args.length+2-argsPassedInRegister;
      if (argsToThrowAway != 0) {
        asm.emitADD_Reg_Imm_Quad(SP, argsToThrowAway << LG_WORDSIZE);
      }
    }

    // (8) Save result to stack
    final TypeReference returnType = method.getReturnType();
    if (returnType.isVoidType()) {
      // Nothing to save
    } else if (returnType.isFloatType()) {
      asm.emitPUSH_Reg(T0); // adjust stack
      if (VM.BuildFor32Addr) {
        asm.emitFSTP_RegInd_Reg(ESP, FP0);
      } else {
        asm.emitMOVSS_RegInd_Reg(ESP, XMM0);
      }
    } else if (returnType.isDoubleType()) {
      asm.emitPUSH_Reg(T0); // adjust stack
      asm.emitPUSH_Reg(T0); // adjust stack
      if (VM.BuildFor32Addr) {
        asm.emitFSTP_RegInd_Reg_Quad(ESP, FP0);
      } else {
        asm.emitMOVSD_RegInd_Reg(ESP, XMM0);
      }
    } else if (VM.BuildFor32Addr && returnType.isLongType()) {
      asm.emitPUSH_Reg(T0);
      asm.emitPUSH_Reg(T1);
    } else {
      // Ensure sign-extension is correct
      if (returnType.isBooleanType()) {
        asm.emitMOVZX_Reg_Reg_Byte(T0, T0);
      } else if (returnType.isByteType()) {
        asm.emitMOVSX_Reg_Reg_Byte(T0, T0);
      } else if (returnType.isCharType()) {
        asm.emitMOVZX_Reg_Reg_Word(T0, T0);
      } else if (returnType.isShortType()) {
        asm.emitMOVSX_Reg_Reg_Word(T0, T0);
      }
      asm.emitPUSH_Reg(T0);
    }

    // (9) Recover RVM style frame
    // (9.1) reload JNIEnvironment from glue frame
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegDisp(S0, EBP, JNICompiler.JNI_ENV_OFFSET);
    } else {
      asm.emitMOV_Reg_RegDisp_Quad(S0, EBP, JNICompiler.JNI_ENV_OFFSET);
    }
    // (9.2) Reload thread register from JNIEnvironment
    ThreadLocalState.emitLoadThread(asm, S0, Entrypoints.JNIEnvSavedTRField.getOffset());

    // (9.3) Establish frame pointer to this glue method
    ThreadLocalState.emitMoveRegToField(asm, ArchEntrypoints.framePointerField.getOffset(), EBP);

    // (10) Transition back from "in native" to "in Java", convert a reference
    // result (currently a JNI ref) into a true reference, release JNI refs
    asm.emitMOV_Reg_Reg(PARAMETER_GPRS[0], S0); // 1st arg is JNI Env
    if (returnType.isReferenceType()) {
      asm.emitPOP_Reg(PARAMETER_GPRS[1]);       // 2nd arg is ref result
    } else {
      // place dummy (null) operand on stack
      asm.emitXOR_Reg_Reg(PARAMETER_GPRS[1], PARAMETER_GPRS[1]);
    }
    asm.emitPUSH_Reg(S0);                       // save JNIEnv
    asm.emitPUSH_Reg(S0);                       // push arg 1
    asm.emitPUSH_Reg(PARAMETER_GPRS[1]);        // push arg 2
    // Do the call
    ObjectModel.baselineEmitLoadTIB(asm, S0.value(), S0.value());
    asm.emitCALL_RegDisp(S0, Entrypoints.jniExit.getOffset());
    asm.emitPOP_Reg(S0); // restore JNIEnv

    // (11) Restore stack and place result in register
    // place result in register
    if (returnType.isVoidType()) {
      // Nothing to save
    } else if (returnType.isReferenceType()) {
      // value already in register
    } else if (returnType.isFloatType()) {
      if (SSE2_FULL) {
        asm.emitMOVSS_Reg_RegInd(XMM0, ESP);
      } else {
        asm.emitFLD_Reg_RegInd(FP0, ESP);
      }
      asm.emitPOP_Reg(T0); // adjust stack
    } else if (returnType.isDoubleType()) {
      if (SSE2_FULL) {
        asm.emitMOVSD_Reg_RegInd(XMM0, ESP);
      } else {
        asm.emitFLD_Reg_RegInd_Quad(FP0, ESP);
      }
      asm.emitPOP_Reg(T0); // adjust stack
      asm.emitPOP_Reg(T0); // adjust stack
    } else if (VM.BuildFor32Addr && returnType.isLongType()) {
      asm.emitPOP_Reg(T0);
      asm.emitPOP_Reg(T1);
    } else {
      asm.emitPOP_Reg(T0);
    }

    asm.emitPOP_Reg(EBX); // saved previous native BP
    asm.emitMOV_RegDisp_Reg(S0, Entrypoints.JNIEnvBasePointerOnEntryToNative.getOffset(), EBX);
    asm.emitPOP_Reg(EBX); // throw away JNI env
    asm.emitPOP_Reg(EBP); // restore non-volatile EBP
    asm.emitPOP_Reg(EBX); // restore non-volatile EBX
    asm.emitPOP_Reg(EDI); // restore non-volatile EDI
    asm.emitPOP_Reg(S0);  // throw away cmid
    asm.emitPOP_RegDisp(THREAD_REGISTER, ArchEntrypoints.framePointerField.getOffset());

    // (12) Return to caller
    // pop parameters from stack (Note that parameterWords does not include "this")
    if (method.isStatic()) {
      asm.emitRET_Imm(method.getParameterWords() << LG_WORDSIZE);
    } else {
      asm.emitRET_Imm((method.getParameterWords() + 1) << LG_WORDSIZE);
    }

    MachineCode machineCode = new ArchitectureSpecific.MachineCode(asm.getMachineCodes(), null);
    cm.compileComplete(machineCode.getInstructions());
    return cm;
  }

  /**
   * Handle the C to Java transition:  JNI methods in JNIFunctions.java.
   * Create a prologue for the baseline compiler.
   * <pre>
   * NOTE:
   *   -We need THREAD_REGISTER to access Java environment; we can get it from
   *    the JNIEnv* (which is an interior pointer to the JNIEnvironment)
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
   * caller     |arg 0       | JNIEnv*           |arg 0       | JNIEnvironment
   *    ESP ->  |return addr |                   |return addr |
   *            |            |           EBP ->  |saved FP    | outer most native frame pointer
   *            |            |                   |methodID    | normal MethodID for JNI function
   *            |            |                   |saved JavaFP| offset to preceeding java frame
   *            |            |                   |saved nonvol| to be used for nonvolatile storage
   *            |            |                   |  ...       |   including ebp on entry
   *            |            |                   |arg 0       | copied in reverse order (JNIEnvironment)
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
    // Variable tracking the depth of the stack as we generate the prologue
    int stackDepth=0;
    // 1st word of header = return address already pushed by CALL
    // 2nd word of header = space for frame pointer
    if (VM.VerifyAssertions) VM._assert(STACKFRAME_FRAME_POINTER_OFFSET == stackDepth << LG_WORDSIZE);
    asm.emitPUSH_Reg(EBP);
    stackDepth--;
    // start new frame:  set FP to point to the new frame
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_Reg(EBP, SP);
    } else {
      asm.emitMOV_Reg_Reg_Quad(EBP, SP);
    }
    // set 3rd word of header: method ID
    if (VM.VerifyAssertions) VM._assert(STACKFRAME_METHOD_ID_OFFSET == stackDepth << LG_WORDSIZE);
    asm.emitPUSH_Imm(methodID);
    stackDepth--;
    // buy space for the SAVED_JAVA_FP
    if (VM.VerifyAssertions) VM._assert(STACKFRAME_BODY_OFFSET == stackDepth << LG_WORDSIZE);
    asm.emitPUSH_Reg(T0);
    stackDepth--;
    // store non-volatiles
    for (GPR r : NATIVE_NONVOLATILE_GPRS) {
      if(r != EBP) {
        asm.emitPUSH_Reg(r);
      } else {
        asm.emitPUSH_RegInd(EBP); // save original EBP value
      }
      stackDepth--;
    }
    for (FloatingPointMachineRegister r : NATIVE_NONVOLATILE_FPRS) {
      // TODO: we assume non-volatile will hold at most a double
      asm.emitPUSH_Reg(T0); // adjust space for double
      asm.emitPUSH_Reg(T0);
      stackDepth-=2;
      if (r instanceof XMM) {
        asm.emitMOVSD_RegInd_Reg(SP, (XMM)r);
      } else {
        // NB this will fail for anything other than FPR0
        asm.emitFST_RegInd_Reg_Quad(SP, (FPR)r);
      }
    }
    if (VM.VerifyAssertions) VM._assert(stackDepth << LG_WORDSIZE == STACKFRAME_BODY_OFFSET - (SAVED_GPRS_FOR_JNI << LG_WORDSIZE), "of2fp="+stackDepth+" sg4j="+SAVED_GPRS_FOR_JNI);
    // Adjust first param from JNIEnv* to JNIEnvironment.
    final Offset firstStackArgOffset = Offset.fromIntSignExtend(2 * WORDSIZE);
    if (jniExternalFunctionsFieldOffset != 0) {
      if (NATIVE_PARAMETER_GPRS.length > 0) {
        if (VM.BuildFor32Addr) {
          asm.emitSUB_Reg_Imm(NATIVE_PARAMETER_GPRS[0], jniExternalFunctionsFieldOffset);
        } else {
          asm.emitSUB_Reg_Imm_Quad(NATIVE_PARAMETER_GPRS[0], jniExternalFunctionsFieldOffset);
        }
      } else {
        if (VM.BuildFor32Addr) {
          asm.emitSUB_RegDisp_Imm(EBP, firstStackArgOffset, jniExternalFunctionsFieldOffset);
        } else {
          asm.emitSUB_RegDisp_Imm_Quad(EBP, firstStackArgOffset, jniExternalFunctionsFieldOffset);
        }
      }
    }

    // copy the arguments in reverse order
    final TypeReference[] argTypes = method.getParameterTypes(); // does NOT include implicit this or class ptr
    Offset stackArgOffset = firstStackArgOffset;
    final int startOfStackedArgs = stackDepth+1; // negative value relative to EBP
    int argGPR = 0;
    int argFPR = 0;
    for (TypeReference argType : argTypes) {
      if (argType.isFloatType()) {
        if (argFPR < NATIVE_PARAMETER_FPRS.length) {
          asm.emitPUSH_Reg(T0); // adjust stack
          if (VM.BuildForSSE2) {
            asm.emitMOVSS_RegInd_Reg(SP, (XMM)NATIVE_PARAMETER_FPRS[argFPR]);
          } else {
            asm.emitFSTP_RegInd_Reg(SP, (FPR)FP0);
          }
          argFPR++;
        } else {
          asm.emitPUSH_RegDisp(EBP, stackArgOffset);
          stackArgOffset = stackArgOffset.plus(WORDSIZE);
        }
        stackDepth--;
      } else if (argType.isDoubleType()) {
        if (argFPR < NATIVE_PARAMETER_FPRS.length) {
          asm.emitPUSH_Reg(T0); // adjust stack
          asm.emitPUSH_Reg(T0);
          if (VM.BuildForSSE2) {
            asm.emitMOVSD_RegInd_Reg(SP, (XMM)NATIVE_PARAMETER_FPRS[argGPR]);
          } else {
            asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
          }
          argFPR++;
        } else {
          if (VM.BuildFor32Addr) {
            asm.emitPUSH_RegDisp(EBP, stackArgOffset.plus(WORDSIZE));
            asm.emitPUSH_RegDisp(EBP, stackArgOffset);
            stackArgOffset = stackArgOffset.plus(2*WORDSIZE);
          } else {
            asm.emitPUSH_Reg(T0); // adjust stack
            asm.emitPUSH_RegDisp(EBP, stackArgOffset);
            stackArgOffset = stackArgOffset.plus(WORDSIZE);
          }
        }
        stackDepth-=2;
      } else if (argType.isLongType()) {
        if (VM.BuildFor32Addr) {
          if (argGPR+1 < NATIVE_PARAMETER_GPRS.length) {
            asm.emitPUSH_Reg(NATIVE_PARAMETER_GPRS[argGPR]);
            asm.emitPUSH_Reg(NATIVE_PARAMETER_GPRS[argGPR+1]);
            argGPR+=2;
          } else if (argGPR < NATIVE_PARAMETER_GPRS.length) {
            asm.emitPUSH_RegDisp(EBP, stackArgOffset);
            asm.emitPUSH_Reg(NATIVE_PARAMETER_GPRS[argGPR]);
            argGPR++;
            stackArgOffset = stackArgOffset.plus(WORDSIZE);
          } else {
            asm.emitPUSH_RegDisp(EBP, stackArgOffset.plus(WORDSIZE));
            asm.emitPUSH_RegDisp(EBP, stackArgOffset);
            stackArgOffset = stackArgOffset.plus(WORDSIZE*2);
          }
          stackDepth-=2;
        } else {
          asm.emitPUSH_Reg(T0); // adjust stack
          if (argGPR < NATIVE_PARAMETER_GPRS.length) {
            asm.emitPUSH_Reg(NATIVE_PARAMETER_GPRS[argGPR]);
            argGPR++;
          } else {
            asm.emitPUSH_RegDisp(EBP, stackArgOffset);
            stackDepth-=2;
            stackArgOffset = stackArgOffset.plus(WORDSIZE);
          }
          stackDepth-=2;
        }
      } else {
        // Reference, int or smaller type
        // NB we don't convert JNI references to true references (as
        // in the entry/exit to a JNI method) as our JNI functions
        // expect integer arguments
        if (argGPR < NATIVE_PARAMETER_GPRS.length) {
          asm.emitPUSH_Reg(NATIVE_PARAMETER_GPRS[argGPR]);
          argGPR++;
        } else {
          asm.emitPUSH_RegDisp(EBP, stackArgOffset);
          stackArgOffset = stackArgOffset.plus(WORDSIZE);
        }
        stackDepth--;
      }
    }

    // START of code sequence to atomically change thread status from
    // IN_JNI to IN_JAVA, looping in a call to
    // RVMThread.leaveJNIBlockedFromJNIFunctionCallMethod if
    // BLOCKED_IN_NATIVE
    int retryLabel = asm.getMachineCodeIndex(); // backward branch label

    // Restore THREAD_REGISTER from JNIEnvironment
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegDisp(EBX, EBP, Offset.fromIntSignExtend((startOfStackedArgs-1) * WORDSIZE));   // pick up arg 0 (from our frame)
    } else {
      asm.emitMOV_Reg_RegDisp_Quad(EBX, EBP, Offset.fromIntSignExtend((startOfStackedArgs-1) * WORDSIZE));   // pick up arg 0 (from our frame)
    }
    ThreadLocalState.emitLoadThread(asm, EBX, Entrypoints.JNIEnvSavedTRField.getOffset());

    // what we need to keep in mind at this point:
    // - EBX has JNI env (but it's nonvolatile)
    // - EBP has the FP (but it's nonvolatile)
    // - stack has the args but not the locals
    // - TR has been restored

    // attempt to change the thread state to IN_JAVA
    asm.emitMOV_Reg_Imm(T0, RVMThread.IN_JNI);
    asm.emitMOV_Reg_Imm(T1, RVMThread.IN_JAVA);
    ThreadLocalState.emitCompareAndExchangeField(
      asm,
      Entrypoints.execStatusField.getOffset(),
      T1);

    // if we succeeded, move on, else go into slow path
    ForwardReference doneLeaveJNIRef = asm.forwardJcc(Assembler.EQ);

    // make the slow call
    asm.emitCALL_Abs(
      Magic.getTocPointer().plus(
        Entrypoints.leaveJNIBlockedFromJNIFunctionCallMethod.getOffset()));

    // arrive here when we've switched to IN_JAVA
    doneLeaveJNIRef.resolve(asm);
    // END of code sequence to change state from IN_JNI to IN_JAVA

    // status is now IN_JAVA. GC can not occur while we execute on a processor
    // in this state, so it is safe to access fields of objects.
    // RVM TR register has been restored and EBX contains a pointer to
    // the thread's JNIEnvironment.

    // done saving, bump SP to reserve room for the local variables
    // SP should now be at the point normally marked as emptyStackOffset
    int numLocalVariables = method.getLocalWords() - method.getParameterWords();
    // TODO: optimize this space adjustment
    if (VM.BuildFor32Addr) {
      asm.emitSUB_Reg_Imm(SP, (numLocalVariables << LG_WORDSIZE));
    } else {
      asm.emitSUB_Reg_Imm_Quad(SP, (numLocalVariables << LG_WORDSIZE));
    }
    // Retrieve -> preceeding "top" java FP from jniEnv and save in current
    // frame of JNIFunction
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegDisp(S0, EBX, Entrypoints.JNITopJavaFPField.getOffset());
    } else {
      asm.emitMOV_Reg_RegDisp_Quad(S0, EBX, Entrypoints.JNITopJavaFPField.getOffset());
    }
    // get offset from current FP and save in hdr of current frame
    if (VM.BuildFor32Addr) {
      asm.emitSUB_Reg_Reg(S0, EBP);
      asm.emitMOV_RegDisp_Reg(EBP, SAVED_JAVA_FP_OFFSET, S0);
    } else {
      asm.emitSUB_Reg_Reg_Quad(S0, EBP);
      asm.emitMOV_RegDisp_Reg_Quad(EBP, SAVED_JAVA_FP_OFFSET, S0);
    }

    // clobber the saved frame pointer with that from the JNIEnvironment (work around for omit-frame-pointer)
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegDisp(S0, EBX, Entrypoints.JNIEnvBasePointerOnEntryToNative.getOffset());
      asm.emitMOV_RegInd_Reg(EBP, S0);
    } else {
      asm.emitMOV_Reg_RegDisp_Quad(S0, EBX, Entrypoints.JNIEnvBasePointerOnEntryToNative.getOffset());
      asm.emitMOV_RegInd_Reg_Quad(EBP, S0);
    }

    // put framePointer in Thread following Jikes RVM conventions.
    ThreadLocalState.emitMoveRegToField(asm, ArchEntrypoints.framePointerField.getOffset(), EBP);

    // at this point: TR has been restored &
    // processor status = IN_JAVA,
    // arguments for the call have been setup, space on the stack for locals
    // has been acquired.

    // finally proceed with the normal Java compiled code
    // skip the thread switch test for now, see BaselineCompilerImpl.genThreadSwitchTest(true)
    //asm.emitNOP(1); // end of prologue marker
  }

  /**
   * Handle the C to Java transition:  JNI methods in JNIFunctions.java.
   * Create an epilogue for the baseline compiler.
   */
  public static void generateEpilogForJNIMethod(Assembler asm, RVMMethod method) {
    // assume RVM TR regs still valid. potentially T1 & T0 contain return
    // values and should not be modified. we use regs saved in prolog and restored
    // before return to do whatever needs to be done.

    if (VM.BuildFor32Addr) {
      // if returning long, switch the order of the hi/lo word in T0 and T1
      if (method.getReturnType().isLongType()) {
        asm.emitPUSH_Reg(T1);
        asm.emitMOV_Reg_Reg(T1, T0);
        asm.emitPOP_Reg(T0);
      } else {
        if (SSE2_FULL && VM.BuildFor32Addr) {
          // Marshall from XMM0 -> FP0
          if (method.getReturnType().isDoubleType()) {
            if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
            asm.emitMOVSD_RegDisp_Reg(THREAD_REGISTER, Entrypoints.scratchStorageField.getOffset(), XMM0);
            asm.emitFLD_Reg_RegDisp_Quad(FP0, THREAD_REGISTER, Entrypoints.scratchStorageField.getOffset());
          } else if (method.getReturnType().isFloatType()) {
            if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
            asm.emitMOVSS_RegDisp_Reg(THREAD_REGISTER, Entrypoints.scratchStorageField.getOffset(), XMM0);
            asm.emitFLD_Reg_RegDisp(FP0, THREAD_REGISTER, Entrypoints.scratchStorageField.getOffset());
          }
        }
      }
    }

    // current processor status is IN_JAVA, so we only GC at yieldpoints

    // S0 <- JNIEnvironment
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegDisp(S0, THREAD_REGISTER, Entrypoints.jniEnvField.getOffset());
    } else {
      asm.emitMOV_Reg_RegDisp_Quad(S0, THREAD_REGISTER, Entrypoints.jniEnvField.getOffset());
    }

    // set jniEnv TopJavaFP using value saved in frame in prolog
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegDisp(EDI, EBP, SAVED_JAVA_FP_OFFSET);      // EDI<-saved TopJavaFP (offset)
      asm.emitADD_Reg_Reg(EDI, EBP);                                // change offset from FP into address
      asm.emitMOV_RegDisp_Reg(S0, Entrypoints.JNITopJavaFPField.getOffset(), EDI); // jniEnv.TopJavaFP <- EDI
    } else {
      asm.emitMOV_Reg_RegDisp_Quad(EDI, EBP, SAVED_JAVA_FP_OFFSET);      // EDI<-saved TopJavaFP (offset)
      asm.emitADD_Reg_Reg_Quad(EDI, EBP);                                // change offset from FP into address
      asm.emitMOV_RegDisp_Reg_Quad(S0, Entrypoints.JNITopJavaFPField.getOffset(), EDI); // jniEnv.TopJavaFP <- EDI
    }

    // NOTE: we could save the TR in the JNI env, but no need, that would have
    // already been done.

    // what's going on here:
    // - SP and EBP have important stuff in them, but that's fine, since
    //   a call will restore SP and EBP is non-volatile for RVM code
    // - TR still refers to the thread

    // save return values
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(T1);

    // attempt to change the thread state to IN_JNI
    asm.emitMOV_Reg_Imm(T0, RVMThread.IN_JAVA);
    asm.emitMOV_Reg_Imm(T1, RVMThread.IN_JNI);
    ThreadLocalState.emitCompareAndExchangeField(
      asm,
      Entrypoints.execStatusField.getOffset(),
      T1);

    // if success, skip the slow path call
    ForwardReference doneEnterJNIRef = asm.forwardJcc(Assembler.EQ);

    // fast path failed, make the call
    asm.emitCALL_Abs(
      Magic.getTocPointer().plus(
        Entrypoints.enterJNIBlockedFromJNIFunctionCallMethod.getOffset()));

    // OK - we reach here when we have set the state to IN_JNI
    doneEnterJNIRef.resolve(asm);

    // restore return values
    asm.emitPOP_Reg(T1);
    asm.emitPOP_Reg(T0);

    // reload native/C nonvolatile regs - saved in prolog
    for (FloatingPointMachineRegister r : NATIVE_NONVOLATILE_FPRS) {
      // TODO: we assume non-volatile will hold at most a double
      if (r instanceof XMM) {
        asm.emitMOVSD_Reg_RegInd((XMM)r, SP);
      } else {
        // NB this will fail for anything other than FPR0
        asm.emitFLD_Reg_RegInd_Quad((FPR)r, SP);
      }
      asm.emitPOP_Reg(T0); // adjust space for double
      asm.emitPOP_Reg(T0);
    }
    // NB when EBP is restored it isn't our outer most EBP but rather than
    // nonvolatile push as the 1st instruction of the prologue
    for (int i=NATIVE_NONVOLATILE_GPRS.length-1; i >= 0; i--) {
      GPR r = NATIVE_NONVOLATILE_GPRS[i];
      asm.emitPOP_Reg(r);
    }

    // Discard JNIEnv, CMID and outer most native frame pointer
    if (VM.BuildFor32Addr) {
      asm.emitADD_Reg_Imm(SP, 3*WORDSIZE); // discard current stack frame
    } else {
      asm.emitADD_Reg_Imm_Quad(SP, 3*WORDSIZE); // discard current stack frame
    }
    asm.emitRET();                // return to caller
  }
}
