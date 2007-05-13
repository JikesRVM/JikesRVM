/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2006
 */
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OPT_LiveIntervalElement;

/**
 * Generic wrappers around machine-specific IR
 */
public abstract class OPT_MachineSpecificIR {
  /* common to all ISAs */
  public abstract boolean isConditionOperand(OPT_Operand operand);

  public abstract void mutateMIRCondBranch(OPT_Instruction cb);

  public abstract boolean isHandledByRegisterUnknown(char opcode);

  public abstract boolean mayEscapeThread(OPT_Instruction instruction);

  public abstract boolean mayEscapeMethod(OPT_Instruction instruction);

  /* unique to IA32 */
  public boolean isAdviseESP(OPT_Operator operator) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  public boolean isFClear(OPT_Operator operator) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  public boolean isFNInit(OPT_Operator operator) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  public boolean isBURSManagedFPROperand(OPT_Operand operand) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  public int getBURSManagedFPRValue(OPT_Operand operand) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return -1;
  }

  public boolean mutateFMOVs(OPT_LiveIntervalElement live, OPT_Register register, int dfnbegin, int dfnend) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  public void rewriteFPStack(OPT_IR ir) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /* unique to PowerPC */
  public boolean isPowerPCTrapOperand(OPT_Operand operand) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }

  public boolean canFoldNullCheckAndLoad(OPT_Instruction s) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return false;
  }
}
