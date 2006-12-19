/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2006
 */
package com.ibm.jikesrvm.opt.ir;

import static com.ibm.jikesrvm.opt.ir.OPT_Operators.*;

import com.ibm.jikesrvm.VM;
import com.ibm.jikesrvm.opt.OPT_OptimizingCompilerException;

import org.vmmagic.pragma.*;

/**
 * Wrappers around PowerPC-specific IR common to both 32 & 64 bit
 * 
 * $Id: OPT_IA32ConditionOperand.java 10996 2006-11-16 23:37:12Z dgrove-oss $
 * 
 * @author Steve Blackburn
 */
public abstract class OPT_PowerPCMachineSpecificIR extends OPT_MachineSpecificIR {
  /* common to all ISAs */ 
  @Override
  public final boolean isConditionOperand(OPT_Operand operand) {
    return operand instanceof OPT_PowerPCConditionOperand;
  }
  @Override
  public final void mutateMIRCondBranch(OPT_Instruction cb) {
    MIR_CondBranch.mutate(cb, PPC_BCOND,
        MIR_CondBranch2.getValue(cb), 
        MIR_CondBranch2.getCond1(cb), 
        MIR_CondBranch2.getTarget1(cb),
        MIR_CondBranch2.getBranchProfile1(cb));
  }
  @Override
  public final boolean isHandledByRegisterUnknown(char opcode) {
    switch (opcode) {
    case DCBST_opcode: case DCBT_opcode: case DCBTST_opcode: 
    case DCBZ_opcode: case DCBZL_opcode: case ICBI_opcode:
      return true;
    default:
      return false;
    }
  }
  /* unique to PowerPC */
  @Override
  public final boolean isPowerPCTrapOperand(OPT_Operand operand) {
    return operand instanceof OPT_PowerPCConditionOperand; 
  }
  @Override
  public final boolean canFoldNullCheckAndLoad(OPT_Instruction s) {
    if (MIR_Load.conforms(s)) {
      OPT_Operand offset = MIR_Load.getOffset(s);
      if (offset instanceof OPT_IntConstantOperand) {
        return ((OPT_IntConstantOperand)offset).value < 0;
      }
    }
    return false;
  }
}
