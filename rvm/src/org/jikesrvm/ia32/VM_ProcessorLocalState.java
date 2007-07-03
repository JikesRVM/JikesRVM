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
package org.jikesrvm.ia32;

import org.jikesrvm.compilers.common.assembler.ia32.VM_Assembler;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Processor;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * This class provides a layer of abstraction that the rest of the VM must
 * use in order to access the current <code>VM_Processor</code> object.
 *
 * @see VM_Processor
 */
public abstract class VM_ProcessorLocalState {

  protected static final byte PROCESSOR_REGISTER = VM_RegisterConstants.ESI;

  /**
   * The C bootstrap program has placed a pointer to the initial
   * VM_Processor in ESI.
   */
  @Uninterruptible
  public
  static void boot() {
    // do nothing - everything is already set up.
  }

  /**
   * Return the current VM_Processor object
   */
  @Uninterruptible
  public static VM_Processor getCurrentProcessor() {
    return VM_Magic.getESIAsProcessor();
  }

  /**
   * Set the current VM_Processor object
   */
  @Uninterruptible
  public static void setCurrentProcessor(VM_Processor p) {
    VM_Magic.setESIAsProcessor(p);
  }

  /**
   * Emit an instruction sequence to move the value of a register into a field
   * in the current processor offset
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   * @param reg number of the register supplying the new value
   */
  public static void emitMoveRegToField(VM_Assembler asm, Offset offset, byte reg) {
    asm.emitMOV_RegDisp_Reg(PROCESSOR_REGISTER, offset, reg);
  }

  /**
   * Emit an instruction sequence to move an immediate value into a field
   * in the current processor offset
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   * @param imm immediate value
   */
  public static void emitMoveImmToField(VM_Assembler asm, Offset offset, int imm) {
    asm.emitMOV_RegDisp_Imm(PROCESSOR_REGISTER, offset, imm);
  }

  /**
   * Emit an instruction sequence to move the value of a field in the
   * current processor offset to a register
   *
   * @param asm assembler object
   * @param dest number of destination register
   * @param offset of field in the <code>VM_Processor</code> object
   */
  public static void emitMoveFieldToReg(VM_Assembler asm, byte dest, Offset offset) {
    asm.emitMOV_Reg_RegDisp(dest, PROCESSOR_REGISTER, offset);
  }

  /**
   * Emit an instruction sequence to compare the value of a field in the
   * current processor offset with an immediate value
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   * @param imm immediate value to compare with
   */
  public static void emitCompareFieldWithImm(VM_Assembler asm, Offset offset, int imm) {
    asm.emitCMP_RegDisp_Imm(PROCESSOR_REGISTER, offset, imm);
  }

  /**
   * Emit an instruction sequence to to an atomic compare and exchange on a field in the
   * current processor offset with an immediate value. Assumes EAX (T0) contains old value.
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   * @param srcReg register containing value to exchange
   */
  public static void emitCompareAndExchangeField(VM_Assembler asm, Offset offset, byte srcReg) {
    asm.emitLockNextInstruction();
    asm.emitCMPXCHG_RegDisp_Reg(PROCESSOR_REGISTER, offset, srcReg);
  }

  /**
   * Emit an instruction sequence to decrement the value of a field in the
   * current processor offset
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   */
  public static void emitDecrementField(VM_Assembler asm, Offset offset) {
    asm.emitDEC_RegDisp(PROCESSOR_REGISTER, offset);
  }

  /**
   * Emit an instruction sequence to PUSH the value of a field in the
   * current processor offset
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   */
  public static void emitPushField(VM_Assembler asm, Offset offset) {
    asm.emitPUSH_RegDisp(PROCESSOR_REGISTER, offset);
  }

  /**
   * Emit an instruction sequence to POP a value into a field in the
   * current processor offset
   *
   * @param asm assembler object
   * @param offset of field in the <code>VM_Processor</code> object
   */
  public static void emitPopField(VM_Assembler asm, Offset offset) {
    asm.emitPOP_RegDisp(PROCESSOR_REGISTER, offset);
  }

  /**
   * Emit an instruction sequence to PUSH a pointer to the current VM_Processor
   * object on the stack.
   *
   * @param asm assembler object
   */
  public static void emitPushProcessor(VM_Assembler asm) {
    asm.emitPUSH_Reg(PROCESSOR_REGISTER);
  }

  /**
   * Emit an instruction sequence to POP a value on the stack, and set the
   * current processor reference to be this value.
   *
   * @param asm assembler object
   */
  public static void emitPopProcessor(VM_Assembler asm) {
    asm.emitPOP_Reg(PROCESSOR_REGISTER);
  }

  /**
   * Emit an instruction sequence to store a pointer to the current VM_Processor
   * object at a location defined by [base]+offset
   *
   * @param asm assembler object
   * @param base number of base register
   * @param offset offset
   */
  public static void emitStoreProcessor(VM_Assembler asm, byte base, Offset offset) {
    asm.emitMOV_RegDisp_Reg(base, offset, PROCESSOR_REGISTER);
  }

  /**
   * Emit an instruction sequence to load current VM_Processor
   * object from a location defined by [base]+offset
   *
   * @param asm assembler object
   * @param base number of base register
   * @param offset offset
   */
  public static void emitLoadProcessor(VM_Assembler asm, byte base, Offset offset) {
    asm.emitMOV_Reg_RegDisp(PROCESSOR_REGISTER, base, offset);
  }
}
