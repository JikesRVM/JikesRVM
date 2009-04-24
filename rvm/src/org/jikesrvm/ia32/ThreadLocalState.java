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
package org.jikesrvm.ia32;

import org.jikesrvm.compilers.common.assembler.ia32.Assembler;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;
import org.jikesrvm.ia32.RegisterConstants.GPR;

/**
 * This class provides a layer of abstraction that the rest of the VM must
 * use in order to access the current <code>RVMThread</code> object.
 *
 * @see RVMThread
 */
public abstract class ThreadLocalState {

  protected static final GPR THREAD_REGISTER = RegisterConstants.ESI;

  /**
   * The C bootstrap program has placed a pointer to the initial
   * RVMThread in ESI.
   */
  @Uninterruptible
  public
  static void boot() {
    // do nothing - everything is already set up.
  }

  /**
   * Return the current RVMThread object
   */
  @Uninterruptible
  public static RVMThread getCurrentThread() {
    return Magic.getESIAsThread();
  }

  /**
   * Set the current RVMThread object
   */
  @Uninterruptible
  public static void setCurrentThread(RVMThread p) {
    Magic.setESIAsThread(p);
  }

  /**
   * Emit an instruction sequence to move the value of a register into a field
   * in the current thread offset
   *
   * @param asm assembler object
   * @param offset of field in the <code>RVMThread</code> object
   * @param reg number of the register supplying the new value
   */
  public static void emitMoveRegToField(Assembler asm, Offset offset, GPR reg) {
    asm.emitMOV_RegDisp_Reg(THREAD_REGISTER, offset, reg);
  }

  /**
   * Emit an instruction sequence to move an immediate value into a field
   * in the current thread offset
   *
   * @param asm assembler object
   * @param offset of field in the <code>RVMThread</code> object
   * @param imm immediate value
   */
  public static void emitMoveImmToField(Assembler asm, Offset offset, int imm) {
    asm.emitMOV_RegDisp_Imm(THREAD_REGISTER, offset, imm);
  }

  /**
   * Emit an instruction sequence to move the value of a field in the
   * current thread offset to a register
   *
   * @param asm assembler object
   * @param dest number of destination register
   * @param offset of field in the <code>RVMThread</code> object
   */
  public static void emitMoveFieldToReg(Assembler asm, GPR dest, Offset offset) {
    asm.emitMOV_Reg_RegDisp(dest, THREAD_REGISTER, offset);
  }

  /**
   * Emit an instruction sequence to compare the value of a field in the
   * current thread offset with an immediate value
   *
   * @param asm assembler object
   * @param offset of field in the <code>RVMThread</code> object
   * @param imm immediate value to compare with
   */
  public static void emitCompareFieldWithImm(Assembler asm, Offset offset, int imm) {
    asm.emitCMP_RegDisp_Imm(THREAD_REGISTER, offset, imm);
  }

  /**
   * Emit an instruction sequence to to an atomic compare and exchange on a field in the
   * current thread offset with an immediate value. Assumes EAX (T0) contains old value.
   *
   * @param asm assembler object
   * @param offset of field in the <code>RVMThread</code> object
   * @param srcReg register containing value to exchange
   */
  public static void emitCompareAndExchangeField(Assembler asm, Offset offset, GPR srcReg) {
    asm.emitLockNextInstruction();
    asm.emitCMPXCHG_RegDisp_Reg(THREAD_REGISTER, offset, srcReg);
  }

  /**
   * Emit an instruction sequence to decrement the value of a field in the
   * current thread offset
   *
   * @param asm assembler object
   * @param offset of field in the <code>RVMThread</code> object
   */
  public static void emitDecrementField(Assembler asm, Offset offset) {
    asm.emitDEC_RegDisp(THREAD_REGISTER, offset);
  }

  /**
   * Emit an instruction sequence to PUSH the value of a field in the
   * current thread offset
   *
   * @param asm assembler object
   * @param offset of field in the <code>RVMThread</code> object
   */
  public static void emitPushField(Assembler asm, Offset offset) {
    asm.emitPUSH_RegDisp(THREAD_REGISTER, offset);
  }

  /**
   * Emit an instruction sequence to POP a value into a field in the
   * current thread offset
   *
   * @param asm assembler object
   * @param offset of field in the <code>RVMThread</code> object
   */
  public static void emitPopField(Assembler asm, Offset offset) {
    asm.emitPOP_RegDisp(THREAD_REGISTER, offset);
  }

  /**
   * Emit an instruction sequence to PUSH a pointer to the current RVMThread
   * object on the stack.
   *
   * @param asm assembler object
   */
  public static void emitPushThread(Assembler asm) {
    asm.emitPUSH_Reg(THREAD_REGISTER);
  }

  /**
   * Emit an instruction sequence to POP a value on the stack, and set the
   * current thread reference to be this value.
   *
   * @param asm assembler object
   */
  public static void emitPopThread(Assembler asm) {
    asm.emitPOP_Reg(THREAD_REGISTER);
  }

  /**
   * Emit an instruction sequence to store a pointer to the current RVMThread
   * object at a location defined by [base]+offset
   *
   * @param asm assembler object
   * @param base number of base register
   * @param offset offset
   */
  public static void emitStoreThread(Assembler asm, GPR base, Offset offset) {
    asm.emitMOV_RegDisp_Reg(base, offset, THREAD_REGISTER);
  }

  /**
   * Emit an instruction sequence to load current RVMThread
   * object from a location defined by [base]+offset
   *
   * @param asm assembler object
   * @param base number of base register
   * @param offset offset
   */
  public static void emitLoadThread(Assembler asm, GPR base, Offset offset) {
    asm.emitMOV_Reg_RegDisp(THREAD_REGISTER, base, offset);
  }
}

