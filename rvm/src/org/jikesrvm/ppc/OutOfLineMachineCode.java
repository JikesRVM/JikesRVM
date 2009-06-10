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
package org.jikesrvm.ppc;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.compilers.common.assembler.ForwardReference;
import org.jikesrvm.compilers.common.assembler.ppc.Assembler;
import org.jikesrvm.compilers.common.assembler.ppc.AssemblerConstants;
import org.jikesrvm.jni.ppc.JNIStackframeLayoutConstants;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Entrypoints;
import org.vmmagic.unboxed.Offset;

/**
 * A place to put hand written machine code typically invoked by Magic
 * methods.
 *
 * Hand coding of small inline instruction sequences is typically handled by
 * each compiler's implementation of Magic methods.  A few Magic methods
 * are so complex that their implementations require many instructions.
 * But our compilers do not inline arbitrary amounts of machine code.
 * We therefore write such code blocks here, out of line.
 *
 * These code blocks can be shared by all compilers. They can be branched to
 * via a jtoc offset (obtained from Entrypoints.XXXInstructionsMethod).
 *
 * 17 Mar 1999 Derek Lieber
 *
 * 15 Jun 2001 Dave Grove and Bowen Alpern (Derek believed that compilers
 * could inline these methods if they wanted.  We do not believe this would
 * be very easy since they return thru the LR.)
 */
public abstract class OutOfLineMachineCode
    implements BaselineConstants, JNIStackframeLayoutConstants, AssemblerConstants {
  public static void init() {
    reflectiveMethodInvokerInstructions = generateReflectiveMethodInvokerInstructions();
    saveThreadStateInstructions = generateSaveThreadStateInstructions();
    threadSwitchInstructions = generateThreadSwitchInstructions();
    restoreHardwareExceptionStateInstructions = generateRestoreHardwareExceptionStateInstructions();
    saveVolatilesInstructions = generateSaveVolatilesInstructions();
    restoreVolatilesInstructions = generateRestoreVolatilesInstructions();
  }

  @SuppressWarnings("unused")
  // Accessed via EntryPoints
  private static ArchitectureSpecific.CodeArray reflectiveMethodInvokerInstructions;
  @SuppressWarnings("unused")
  // Accessed via EntryPoints
  private static ArchitectureSpecific.CodeArray saveThreadStateInstructions;
  @SuppressWarnings("unused")
  // Accessed via EntryPoints
  private static ArchitectureSpecific.CodeArray threadSwitchInstructions;
  @SuppressWarnings("unused")
  // Accessed via EntryPoints
  private static ArchitectureSpecific.CodeArray restoreHardwareExceptionStateInstructions;
  @SuppressWarnings("unused")
  // Accessed via EntryPoints
  private static ArchitectureSpecific.CodeArray saveVolatilesInstructions;
  // Accessed via EntryPoints
  private static ArchitectureSpecific.CodeArray restoreVolatilesInstructions;

  // Machine code for reflective method invocation.
  // See also: "Compiler.generateMethodInvocation".
  //
  // Registers taken at runtime:
  //   T0 == address of method entrypoint to be called
  //   T1 == address of gpr registers to be loaded
  //   T2 == address of fpr registers to be loaded
  //   T4 == address of spill area in calling frame
  //
  // Registers returned at runtime:
  //   standard return value conventions used
  //
  // Side effects at runtime:
  //   artificial stackframe created and destroyed
  //   R0, volatile, and scratch registers destroyed
  //
  private static ArchitectureSpecific.CodeArray generateReflectiveMethodInvokerInstructions() {
    Assembler asm = new ArchitectureSpecific.Assembler(0);

    //
    // free registers: 0, S0
    //
    asm.emitMFLR(0);                                         // save...
    asm.emitSTAddr(0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); // ...return address

    asm.emitMTCTR(T0);                          // CTR := start of method code

    //
    // free registers: 0, S0, T0
    //

    // create new frame
    //
    asm.emitMR(S0, FP);                  // S0 := old frame pointer
    asm.emitLIntOffset(T0, T4, ObjectModel.getArrayLengthOffset()); // T0 := number of spill words
    asm.emitADDI(T4, -BYTES_IN_ADDRESS, T4);                  // T4 -= 4 (predecrement, ie. T4 + 4 is &spill[0] )
    int spillLoopLabel = asm.getMachineCodeIndex();
    asm.emitADDICr(T0, T0, -1);                  // T0 -= 1 (and set CR)
    ForwardReference fr1 = asm.emitForwardBC(LT); // if T0 < 0 then break
    asm.emitLAddrU(0, BYTES_IN_ADDRESS, T4);                  // R0 := *(T4 += 4)
    asm.emitSTAddrU(0, -BYTES_IN_ADDRESS, FP);                  // put one word of spill area
    asm.emitB(spillLoopLabel); // goto spillLoop:
    fr1.resolve(asm);

    asm.emitSTAddrU(S0, -STACKFRAME_HEADER_SIZE, FP);     // allocate frame header and save old fp
    asm.emitLVAL(T0, INVISIBLE_METHOD_ID);
    asm.emitSTWoffset(T0, FP, Offset.fromIntSignExtend(STACKFRAME_METHOD_ID_OFFSET)); // set method id

    //
    // free registers: 0, S0, T0, T4
    //

    // load up fprs
    //
    ForwardReference setupFPRLoader = asm.emitForwardBL();

    for (int i = LAST_VOLATILE_FPR; i >= FIRST_VOLATILE_FPR; --i) {
      asm.emitLFDU(i, BYTES_IN_DOUBLE, T2);                 // FPRi := fprs[i]
    }

    //
    // free registers: 0, S0, T0, T2, T4
    //

    // load up gprs
    //
    ForwardReference setupGPRLoader = asm.emitForwardBL();

    for (int i = LAST_VOLATILE_GPR; i >= FIRST_VOLATILE_GPR; --i) {
      asm.emitLAddrU(i, BYTES_IN_ADDRESS, S0);                 // GPRi := gprs[i]
    }

    //
    // free registers: 0, S0
    //

    // invoke method
    //
    asm.emitBCCTRL();                            // branch and link to method code

    // emit method epilog
    //
    asm.emitLAddr(FP, 0, FP);                                    // restore caller's frame
    asm.emitLAddr(S0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);   // pick up return address
    asm.emitMTLR(S0);                                            //
    asm.emitBCLR();                                                // return to caller

    setupFPRLoader.resolve(asm);
    asm.emitMFLR(T4);                          // T4 := address of first fpr load instruction
    asm.emitLIntOffset(T0, T2, ObjectModel.getArrayLengthOffset()); // T0 := number of fprs to be loaded
    asm.emitADDI(T4,
                 VOLATILE_FPRS << LG_INSTRUCTION_WIDTH,
                 T4); // T4 := address of first instruction following fpr loads
    asm.emitSLWI(T0, T0, LG_INSTRUCTION_WIDTH); // T0 := number of bytes of fpr load instructions
    asm.emitSUBFC(T4, T0, T4);                // T4 := address of instruction for highest numbered fpr to be loaded
    asm.emitMTLR(T4);                           // LR := """
    asm.emitADDI(T2, -BYTES_IN_DOUBLE, T2);    // predecrement fpr index (to prepare for update instruction)
    asm.emitBCLR();                            // branch to fpr loading instructions

    setupGPRLoader.resolve(asm);
    asm.emitMFLR(T4);                          // T4 := address of first gpr load instruction
    asm.emitLIntOffset(T0, T1, ObjectModel.getArrayLengthOffset()); // T0 := number of gprs to be loaded
    asm.emitADDI(T4,
                 VOLATILE_GPRS << LG_INSTRUCTION_WIDTH,
                 T4); // T4 := address of first instruction following gpr loads
    asm.emitSLWI(T0, T0, LG_INSTRUCTION_WIDTH); // T0 := number of bytes of gpr load instructions
    asm.emitSUBFC(T4, T0, T4);                  // T4 := address of instruction for highest numbered gpr to be loaded
    asm.emitMTLR(T4);                          // LR := """
    asm.emitADDI(S0, -BYTES_IN_ADDRESS, T1);   // predecrement gpr index (to prepare for update instruction)
    asm.emitBCLR();                            // branch to gpr loading instructions

    return asm.makeMachineCode().getInstructions();
  }

  // Machine code to implement "Magic.saveThreadState()".
  //
  // Registers taken at runtime:
  //   T0 == address of Registers object
  //
  // Registers returned at runtime:
  //   none
  //
  // Side effects at runtime:
  //   T1 destroyed
  //
  private static ArchitectureSpecific.CodeArray generateSaveThreadStateInstructions() {
    Assembler asm = new ArchitectureSpecific.Assembler(0);

    // save return address
    //
    asm.emitMFLR(T1);               // T1 = LR (return address)
    asm.emitSTAddrOffset(T1, T0, ArchEntrypoints.registersIPField.getOffset()); // registers.ip = return address

    // save non-volatile fprs
    //
    asm.emitLAddrOffset(T1, T0, ArchEntrypoints.registersFPRsField.getOffset()); // T1 := registers.fprs[]
    for (int i = FIRST_NONVOLATILE_FPR; i <= LAST_NONVOLATILE_FPR; ++i) {
      asm.emitSTFD(i, i << LOG_BYTES_IN_DOUBLE, T1);
    }

    // save non-volatile gprs
    //
    asm.emitLAddrOffset(T1, T0, ArchEntrypoints.registersGPRsField.getOffset()); // T1 := registers.gprs[]
    for (int i = FIRST_NONVOLATILE_GPR; i <= LAST_NONVOLATILE_GPR; ++i) {
      asm.emitSTAddr(i, i << LOG_BYTES_IN_ADDRESS, T1);
    }

    // save fp
    //
    asm.emitSTAddr(FP, FP << LOG_BYTES_IN_ADDRESS, T1);

    // return to caller
    //
    asm.emitBCLR();

    return asm.makeMachineCode().getInstructions();
  }

  /**
   * Machine code to implement "Magic.threadSwitch()".
   *
   * Currently not functional on PNT. Left for template for possible reintroduction.
   *
   *  Parameters taken at runtime:
   *    T0 == address of Thread object for the current thread
   *    T1 == address of Registers object for the new thread
   *
   *  Registers returned at runtime:
   *    none
   *
   *  Side effects at runtime:
   *    sets current Thread's beingDispatched field to false
   *    saves current Thread's nonvolatile hardware state in its Registers object
   *    restores new thread's Registers nonvolatile hardware state.
   *    execution resumes at address specificed by restored thread's Registers ip field
   */
  private static ArchitectureSpecific.CodeArray generateThreadSwitchInstructions() {
    Assembler asm = new ArchitectureSpecific.Assembler(0);

    Offset ipOffset = ArchEntrypoints.registersIPField.getOffset();
    Offset fprsOffset = ArchEntrypoints.registersFPRsField.getOffset();
    Offset gprsOffset = ArchEntrypoints.registersGPRsField.getOffset();

    // (1) Save nonvolatile hardware state of current thread.
    asm.emitMFLR(T3);                         // T3 gets return address
    asm.emitLAddrOffset(T2,
                        T0,
                        Entrypoints.threadContextRegistersField.getOffset());         // T2 = T0.contextRegisters
    asm.emitSTAddrOffset(T3, T2, ipOffset);           // T0.contextRegisters.ip = return address

    // save non-volatile fprs
    asm.emitLAddrOffset(T3, T2, fprsOffset); // T3 := T0.contextRegisters.fprs[]
    for (int i = FIRST_NONVOLATILE_FPR; i <= LAST_NONVOLATILE_FPR; ++i) {
      asm.emitSTFD(i, i << LOG_BYTES_IN_DOUBLE, T3);
    }

    // save non-volatile gprs
    asm.emitLAddrOffset(T3, T2, gprsOffset); // T3 := registers.gprs[]
    for (int i = FIRST_NONVOLATILE_GPR; i <= LAST_NONVOLATILE_GPR; ++i) {
      asm.emitSTAddr(i, i << LOG_BYTES_IN_ADDRESS, T3);
    }

    // save fp
    asm.emitSTAddr(FP, FP << LOG_BYTES_IN_ADDRESS, T3);

    // (2) Restore nonvolatile hardware state of new thread.

    // restore non-volatile fprs
    asm.emitLAddrOffset(T0, T1, fprsOffset); // T0 := T1.fprs[]
    for (int i = FIRST_NONVOLATILE_FPR; i <= LAST_NONVOLATILE_FPR; ++i) {
      asm.emitLFD(i, i << LOG_BYTES_IN_DOUBLE, T0);
    }

    // restore non-volatile gprs
    asm.emitLAddrOffset(T0, T1, gprsOffset); // T0 := T1.gprs[]
    for (int i = FIRST_NONVOLATILE_GPR; i <= LAST_NONVOLATILE_GPR; ++i) {
      asm.emitLAddr(i, i << LOG_BYTES_IN_ADDRESS, T0);
    }

    // restore fp
    asm.emitLAddr(FP, FP << LOG_BYTES_IN_ADDRESS, T0);

    // resume execution at saved ip (T1.ipOffset)
    asm.emitLAddrOffset(T0, T1, ipOffset);
    asm.emitMTLR(T0);
    asm.emitBCLR();

    return asm.makeMachineCode().getInstructions();
  }

  // Machine code to implement "Magic.restoreHardwareExceptionState()".
  //
  // Registers taken at runtime:
  //   T0 == address of Registers object
  //
  // Registers returned at runtime:
  //   none
  //
  // Side effects at runtime:
  //   all registers are restored except condition registers, count register,
  //   JTOC_POINTER, and THREAD_REGISTER with execution resuming at "registers.ip"
  //
  private static ArchitectureSpecific.CodeArray generateRestoreHardwareExceptionStateInstructions() {
    Assembler asm = new ArchitectureSpecific.Assembler(0);

    // restore LR
    //
    asm.emitLAddrOffset(REGISTER_ZERO, T0, ArchEntrypoints.registersLRField.getOffset());
    asm.emitMTLR(REGISTER_ZERO);

    // restore IP (hold it in CT register for a moment)
    //
    asm.emitLAddrOffset(REGISTER_ZERO, T0, ArchEntrypoints.registersIPField.getOffset());
    asm.emitMTCTR(REGISTER_ZERO);

    // restore fprs
    //
    asm.emitLAddrOffset(T1, T0, ArchEntrypoints.registersFPRsField.getOffset()); // T1 := registers.fprs[]
    for (int i = 0; i < NUM_FPRS; ++i) {
      asm.emitLFD(i, i << LOG_BYTES_IN_DOUBLE, T1);
    }

    // restore gprs
    //
    asm.emitLAddrOffset(T1, T0, ArchEntrypoints.registersGPRsField.getOffset()); // T1 := registers.gprs[]

    for (int i = FIRST_NONVOLATILE_GPR; i <= LAST_NONVOLATILE_GPR; ++i) {
      asm.emitLAddr(i, i << LOG_BYTES_IN_ADDRESS, T1);
    }

    for (int i = FIRST_SCRATCH_GPR; i <= LAST_SCRATCH_GPR; ++i) {
      asm.emitLAddr(i, i << LOG_BYTES_IN_ADDRESS, T1);
    }

    for (int i = FIRST_VOLATILE_GPR; i <= LAST_VOLATILE_GPR; ++i) {
      if (i != T1) asm.emitLAddr(i, i << LOG_BYTES_IN_ADDRESS, T1);
    }

    // restore specials
    //
    asm.emitLAddr(REGISTER_ZERO, REGISTER_ZERO << LOG_BYTES_IN_ADDRESS, T1);
    asm.emitLAddr(FP, FP << LOG_BYTES_IN_ADDRESS, T1);

    // restore last gpr
    //
    asm.emitLAddr(T1, T1 << LOG_BYTES_IN_ADDRESS, T1);

    // resume execution at IP
    //
    asm.emitBCCTR();

    return asm.makeMachineCode().getInstructions();
  }

  // Machine code used to save volatile registers.
  //
  // Registers taken at runtime:
  //   S0 == address of Registers object
  //
  // Registers returned at runtime:
  //   none
  //
  // Side effects at runtime:
  //   S1 destroyed
  //
  private static ArchitectureSpecific.CodeArray generateSaveVolatilesInstructions() {
    Assembler asm = new ArchitectureSpecific.Assembler(0);

    // save volatile fprs
    //
    asm.emitLAddrOffset(S1, S0, ArchEntrypoints.registersFPRsField.getOffset()); // S1 := registers.fprs[]
    for (int i = FIRST_VOLATILE_FPR; i <= LAST_VOLATILE_FPR; ++i) {
      asm.emitSTFD(i, i << LOG_BYTES_IN_DOUBLE, S1);
    }

    // save non-volatile gprs
    //
    asm.emitLAddrOffset(S1, S0, ArchEntrypoints.registersGPRsField.getOffset()); // S1 := registers.gprs[]
    for (int i = FIRST_VOLATILE_GPR; i <= LAST_VOLATILE_GPR; ++i) {
      asm.emitSTAddr(i, i << LOG_BYTES_IN_ADDRESS, S1);
    }

    // return to caller
    //
    asm.emitBCLR();

    return asm.makeMachineCode().getInstructions();
  }

  // Machine code used to save volatile registers.
  //
  // Registers taken at runtime:
  //   S0 == address of Registers object
  //
  // Registers returned at runtime:
  //   none
  //
  // Side effects at runtime:
  //   S1 destroyed
  //
  private static ArchitectureSpecific.CodeArray generateRestoreVolatilesInstructions() {
    Assembler asm = new ArchitectureSpecific.Assembler(0);

    // save volatile fprs
    //
    asm.emitLAddrOffset(S1, S0, ArchEntrypoints.registersFPRsField.getOffset()); // S1 := registers.fprs[]
    for (int i = FIRST_VOLATILE_FPR; i <= LAST_VOLATILE_FPR; ++i) {
      asm.emitLFD(i, i << LOG_BYTES_IN_DOUBLE, S1);
    }

    // save non-volatile gprs
    //
    asm.emitLAddrOffset(S1, S0, ArchEntrypoints.registersGPRsField.getOffset()); // S1 := registers.gprs[]
    for (int i = FIRST_VOLATILE_GPR; i <= LAST_VOLATILE_GPR; ++i) {
      asm.emitLAddr(i, i << LOG_BYTES_IN_ADDRESS, S1);
    }

    // return to caller
    //
    asm.emitBCLR();

    return asm.makeMachineCode().getInstructions();
  }
}

