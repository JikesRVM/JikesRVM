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
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.regalloc.LiveIntervalElement;

/**
 * Generic wrappers around machine-specific IR
 */
public abstract class MachineSpecificIR {
  /* common to all ISAs */
  public abstract boolean isConditionOperand(Operand operand);

  public abstract void mutateMIRCondBranch(Instruction cb);

  public abstract boolean isHandledByRegisterUnknown(char opcode);

  public abstract boolean mayEscapeThread(Instruction instruction);

  public abstract boolean mayEscapeMethod(Instruction instruction);

  /* unique to IA32 */
  public boolean isAdviseESP(Operator operator) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  public boolean isFClear(Operator operator) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  public boolean isFNInit(Operator operator) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  public boolean isBURSManagedFPROperand(Operand operand) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  public int getBURSManagedFPRValue(Operand operand) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return -1;
  }

  public boolean mutateFMOVs(LiveIntervalElement live, Register register, int dfnbegin, int dfnend) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  public void rewriteFPStack(IR ir) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /* unique to PowerPC */
  public boolean isPowerPCTrapOperand(Operand operand) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  public boolean canFoldNullCheckAndLoad(Instruction s) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }
}
