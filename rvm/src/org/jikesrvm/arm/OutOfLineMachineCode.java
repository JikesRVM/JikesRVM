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
package org.jikesrvm.arm;

import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;
import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_FLOAT;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.ALWAYS;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.NE;
import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.EQ;
import static org.jikesrvm.arm.BaselineConstants.T0;
import static org.jikesrvm.arm.BaselineConstants.T1;
import static org.jikesrvm.arm.BaselineConstants.T2;
import static org.jikesrvm.arm.BaselineConstants.T3;
import static org.jikesrvm.arm.RegisterConstants.R12;
import static org.jikesrvm.arm.RegisterConstants.SP;
import static org.jikesrvm.arm.RegisterConstants.LR;
import static org.jikesrvm.arm.RegisterConstants.PC;
import static org.jikesrvm.arm.RegisterConstants.FIRST_VOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_VOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_NONVOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_NONVOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_VOLATILE_DPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_VOLATILE_DPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_NONVOLATILE_DPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_NONVOLATILE_DPR;
import static org.jikesrvm.arm.RegisterConstants.NUM_VOLATILE_GPRS;
import static org.jikesrvm.arm.RegisterConstants.NUM_VOLATILE_DPRS;
import static org.jikesrvm.arm.RegisterConstants.LG_INSTRUCTION_WIDTH;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.CodeArray;
import org.jikesrvm.compilers.common.assembler.ForwardReference;
import org.jikesrvm.compilers.common.assembler.arm.Assembler;
import org.jikesrvm.arm.RegisterConstants.GPR;
import org.jikesrvm.arm.RegisterConstants.DPR;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.vmmagic.pragma.Entrypoint;

/**
 * A place to put hand written machine code typically invoked by Magic
 * methods.<p>
 *
 * Hand coding of small inline instruction sequences is typically handled by
 * each compiler's implementation of Magic methods.  A few Magic methods
 * are so complex that their implementations require many instructions.
 * But our compilers do not inline arbitrary amounts of machine code.
 * We therefore write such code blocks here, out of line.<p>
 *
 * These code blocks can be shared by all compilers. They can be branched to
 * via a jtoc offset (obtained from Entrypoints.XXXInstructionsMethod).
 */
public abstract class OutOfLineMachineCode {
  public static void init() {
    reflectiveMethodInvokerInstructions = generateReflectiveMethodInvokerInstructions();
    saveThreadStateInstructions = generateSaveThreadStateInstructions();
    restoreHardwareExceptionStateInstructions = generateRestoreHardwareExceptionStateInstructions();
    saveVolatilesInstructions = generateSaveVolatilesInstructions();
    restoreVolatilesInstructions = generateRestoreVolatilesInstructions();
  }

  @SuppressWarnings("unused")
  @Entrypoint
  private static CodeArray reflectiveMethodInvokerInstructions;
  @SuppressWarnings("unused")
  @Entrypoint
  private static CodeArray saveThreadStateInstructions;
  @SuppressWarnings("unused")
  @Entrypoint
  private static CodeArray restoreHardwareExceptionStateInstructions;
  @SuppressWarnings("unused")
  @Entrypoint
  private static CodeArray saveVolatilesInstructions;
  @Entrypoint
  private static CodeArray restoreVolatilesInstructions;

  /** Machine code for reflective method invocation.
   * See also: "BaselineCompilerImpl.genMethodInvocation()".
   *      and: "MachineReflection.java"
   *<pre>
   * Registers taken at runtime:
   *   T0 == address of gpr registers to be loaded     (WordArray)
   *   T1 == address of method entrypoint to be called
   *   T2 == address of fpr registers to be loaded     (double[])
   *   T3 == address of stack parameters to be written (WordArray) - first param is at the start of the array, and will be the LAST to be pushed onto the stack
   *   LR == return address
   *
   * Registers returned at runtime:
   *   standard return value conventions used
   *
   * Side effects at runtime:
   *   R12 and volatile registers clobbered
   * </pre>
   */
  private static CodeArray generateReflectiveMethodInvokerInstructions() {
    Assembler asm = new Assembler(0);

    // R12 is the only free register

    // load up fprs (we will emit all the instructions, but then jump past some of them, since
    //   at runtime we will only need to write to a subset of the fprs)
    asm.emitLDRimm(ALWAYS, R12, T2, ObjectModel.getArrayLengthOffset().toInt()); // R12 = number of dprs to be loaded
    asm.emitRSBimm(ALWAYS, R12, R12, NUM_VOLATILE_DPRS - 1);             // R12 = num dprs minus number to set (-1 because ARM PC is 2 instructions ahead) (yes, you read that right)
    asm.emitADDshift(ALWAYS, PC, PC, R12, LG_INSTRUCTION_WIDTH);         // Jump over R12 fpr instructions (to the one we want)

    if (VM.VerifyAssertions) VM._assert((LAST_VOLATILE_DPR.value() & 1) == 0); // Should be even
    for (int i = LAST_VOLATILE_DPR.value(); i >= FIRST_VOLATILE_DPR.value(); i -= 2) {
      // Note: LOG_BYTES_IN_FLOAT is correct, since i is already a multiple of 2
      asm.emitVLDR64(ALWAYS, DPR.lookup(i), T2, i << LOG_BYTES_IN_FLOAT);
    }

    // T2 and R12 are now free


    // Push parameters onto the stack
    asm.emitLDRimm(ALWAYS, T2, T3, ObjectModel.getArrayLengthOffset().toInt()); // T2 = number of spill words
    asm.emitCMPimm(ALWAYS, T2, 0);

    ForwardReference fr = asm.generateForwardBranch(EQ);                // If no spills to write skip this bit

    asm.emitADDshift(ALWAYS, T2, T3, T2, LOG_BYTES_IN_ADDRESS);         // T2 points past the end of the array, will move backwards from there

    int spillLoopLabel = asm.getMachineCodeIndex();
    asm.emitSUBimm(ALWAYS, T2, T2, BYTES_IN_ADDRESS);                   // Decrement T2 so it points at the next element to write
    asm.emitLDR(ALWAYS, R12, T3, T2);                                   // Load element into R12
    asm.emitPUSH(ALWAYS, R12);                                          // Write it
    asm.emitCMP(ALWAYS, T3, T2);                                        // Go back to start of loop if we are not at the last element
    asm.generateBackwardBranch(NE, spillLoopLabel);

    // end of the spill-writing loop

    fr.resolve(asm); // No spills written

    // R12, T2 and T3 are free

    asm.emitPUSH(ALWAYS, T1);    // Save method address

    // load up gprs (we will emit all the instructions, but then jump past some of them, since
    //   at runtime we will only need to write to a subset of the gprs)
    asm.emitLDRimm(ALWAYS, R12, T0, ObjectModel.getArrayLengthOffset().toInt()); // R12 = number of gprs to be loaded
    asm.emitRSBimm(ALWAYS, R12, R12, NUM_VOLATILE_GPRS - 1);             // R12 = num gprs minus number to set (-1 because ARM PC is 2 instructions ahead) (yes, you read that right)
    asm.emitADDshift(ALWAYS, PC, PC, R12, LG_INSTRUCTION_WIDTH);         // Jump over R12 fpr instructions (to the one we want)

    for (int i = LAST_VOLATILE_GPR.value(); i >= FIRST_VOLATILE_GPR.value(); i--) {
      asm.emitLDRimm(ALWAYS, GPR.lookup(i), T0, i << LOG_BYTES_IN_ADDRESS); // T0, which holds the array reference, is the last to be written
    }

    asm.emitPOP(ALWAYS, R12); // Load method address into R12

    // No free registers

    // invoke method
    asm.emitBX(ALWAYS, R12);     // branch to method code (preserve existing return address in LR)
    return asm.getMachineCodes();
  }

  /** Machine code to implement "Magic.saveThreadState()".
   *
   * Saves LR and all nonvolatile GPRs and FPRs
   * <pre>
   *   T0 == address of Registers object
   *
   * Side effects:
   *   T1 destroyed
   * </pre>
   */
  private static CodeArray generateSaveThreadStateInstructions() {
    Assembler asm = new Assembler(0);

    // save return address: registers.ip = LR
    asm.generateOffsetStore(ALWAYS, LR, T0, ArchEntrypoints.registersIPField.getOffset());

    // save non-volatile fprs
    asm.generateOffsetLoad(ALWAYS, T1, T0, ArchEntrypoints.registersFPRsField.getOffset()); // T1 = registers.fprs[]
    if (VM.VerifyAssertions) VM._assert((LAST_NONVOLATILE_DPR.value() & 1) == 0);
    for (int i = FIRST_NONVOLATILE_DPR.value(); i <= LAST_NONVOLATILE_DPR.value(); i += 2) {
      // Note: LOG_BYTES_IN_FLOAT is correct, since i is already a multiple of 2
      asm.emitVSTR64(ALWAYS, DPR.lookup(i), T1, i << LOG_BYTES_IN_FLOAT); // TODO: could use a single instruction for this
    }

    // save non-volatile gprs
    asm.generateOffsetLoad(ALWAYS, T1, T0, ArchEntrypoints.registersGPRsField.getOffset()); // T1 = registers.gprs[]
    for (int i = FIRST_NONVOLATILE_GPR.value(); i <= LAST_NONVOLATILE_GPR.value(); i++) {
      asm.emitSTRimm(ALWAYS, GPR.lookup(i), T1, i << LOG_BYTES_IN_ADDRESS); // TODO: could use a single instruction for this
    }

    // return to caller
    asm.emitBX(ALWAYS, LR);
    return asm.getMachineCodes();
  }

  /** Machine code to implement "Magic.restoreHardwareExceptionState()".
   * <pre>
   *   R12 == address of Registers object
   *
   * Side effects:
   *   all registers are restored except R12 and the higher FPRs (the optional ones)
   * </pre>
   */
  private static CodeArray generateRestoreHardwareExceptionStateInstructions() {
    Assembler asm = new Assembler(0);

    // restore fprs
    asm.emitLDRimm(ALWAYS, LR, R12, ArchEntrypoints.registersFPRsField.getOffset().toInt()); // LR = registers.fprs[]
    if (VM.VerifyAssertions) VM._assert((LAST_NONVOLATILE_DPR.value() & 1) == 0);
    for (int i = FIRST_VOLATILE_DPR.value(); i <= LAST_NONVOLATILE_DPR.value(); i += 2) {
      // Note: LOG_BYTES_IN_FLOAT is correct, since i is already a multiple of 2
      asm.emitVLDR64(ALWAYS, DPR.lookup(i), LR, i << LOG_BYTES_IN_FLOAT); // TODO: could use a single instruction for this
    }

    // restore gprs (up to R11)
    asm.emitLDRimm(ALWAYS, LR, R12, ArchEntrypoints.registersGPRsField.getOffset().toInt()); // LR = registers.gprs[]
    for (int i = FIRST_VOLATILE_GPR.value(); i <= LAST_NONVOLATILE_GPR.value(); i++) {
      asm.emitLDRimm(ALWAYS, GPR.lookup(i), LR, i << LOG_BYTES_IN_ADDRESS); // TODO: could use a single instruction for this
    }

    // restore SP
    asm.emitLDRimm(ALWAYS, SP, LR, SP.value() << LOG_BYTES_IN_ADDRESS);

    // restore LR
    asm.emitLDRimm(ALWAYS, LR, R12, ArchEntrypoints.registersLRField.getOffset().toInt());

    // restore PC (and branch)
    asm.emitLDRimm(ALWAYS, PC, R12, ArchEntrypoints.registersIPField.getOffset().toInt());
    return asm.getMachineCodes();
  }

  // Machine code used to save volatile registers.
  //
  // Registers taken at runtime:
  //   R12 == address of Registers object
  //
  private static CodeArray generateSaveVolatilesInstructions() {
    Assembler asm = new Assembler(0);

    asm.emitPUSH(ALWAYS, LR); // Save LR to use as scratch

    // save volatile fprs
    asm.emitLDRimm(ALWAYS, LR, R12, ArchEntrypoints.registersFPRsField.getOffset().toInt()); // LR := registers.fprs[]
    if (VM.VerifyAssertions) VM._assert((LAST_VOLATILE_DPR.value() & 1) == 0);
    for (int i = FIRST_VOLATILE_DPR.value(); i <= LAST_VOLATILE_DPR.value(); i += 2) {
      // Note: LOG_BYTES_IN_FLOAT is correct, since i is already a multiple of 2
      asm.emitVSTR64(ALWAYS, DPR.lookup(i), LR, i << LOG_BYTES_IN_FLOAT); // TODO: Can use store multiple instruction
    }

    // save volatile gprs
    asm.emitLDRimm(ALWAYS, LR, R12, ArchEntrypoints.registersGPRsField.getOffset().toInt()); // LR := registers.gprs[]
    for (int i = FIRST_VOLATILE_GPR.value(); i <= LAST_VOLATILE_GPR.value(); i++) {
      asm.emitSTRimm(ALWAYS, GPR.lookup(i), LR, i << LOG_BYTES_IN_ADDRESS); // TODO: Can use store multiple instruction
    }

    asm.emitPOP(ALWAYS, LR); // Restore LR

    // return to caller
    asm.emitBX(ALWAYS, LR);

    return asm.getMachineCodes();
  }

  /**
   * Machine code used to restore volatile registers.
   *
   * Registers taken at runtime:
   *   R12 == address of Registers object
   */
  private static CodeArray generateRestoreVolatilesInstructions() {
    Assembler asm = new Assembler(0);

    asm.emitPUSH(ALWAYS, LR); // Save LR to use as scratch

    // restore volatile fprs
    asm.emitLDRimm(ALWAYS, LR, R12, ArchEntrypoints.registersFPRsField.getOffset().toInt()); // LR := registers.fprs[]
    if (VM.VerifyAssertions) VM._assert((LAST_VOLATILE_DPR.value() & 1) == 0);
    for (int i = FIRST_VOLATILE_DPR.value(); i <= LAST_VOLATILE_DPR.value(); i += 2) {
      // Note: LOG_BYTES_IN_FLOAT is correct, since i is already a multiple of 2
      asm.emitVLDR64(ALWAYS, DPR.lookup(i), LR, i << LOG_BYTES_IN_FLOAT); // TODO: Can use load multiple instruction
    }

    // save volatile gprs
    asm.emitLDRimm(ALWAYS, LR, R12, ArchEntrypoints.registersGPRsField.getOffset().toInt()); // LR := registers.gprs[]
    for (int i = FIRST_VOLATILE_GPR.value(); i <= LAST_VOLATILE_GPR.value(); i++) {
      asm.emitLDRimm(ALWAYS, GPR.lookup(i), LR, i << LOG_BYTES_IN_ADDRESS); // TODO: Can use load multiple instruction
    }

    asm.emitPOP(ALWAYS, LR); // Restore LR

    // return to caller
    asm.emitBX(ALWAYS, LR);

    return asm.getMachineCodes();
  }
}

