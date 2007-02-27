/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2006
 */
package com.ibm.jikesrvm.ppc.opt.ir;

import static com.ibm.jikesrvm.opt.ir.OPT_Operators.*;

import com.ibm.jikesrvm.opt.OPT_OptimizingCompilerException;
import com.ibm.jikesrvm.opt.ir.MIR_CondBranch;
import com.ibm.jikesrvm.opt.ir.MIR_CondBranch2;
import com.ibm.jikesrvm.opt.ir.MIR_Load;
import com.ibm.jikesrvm.opt.ir.OPT_Instruction;
import com.ibm.jikesrvm.opt.ir.OPT_IntConstantOperand;
import com.ibm.jikesrvm.opt.ir.OPT_MachineSpecificIR;
import com.ibm.jikesrvm.opt.ir.OPT_Operand;

/**
 * Wrappers around PowerPC-specific IR common to both 32 & 64 bit
 * 
 *
 * @author Steve Blackburn
 */
public abstract class OPT_MachineSpecificIRPowerPC extends OPT_MachineSpecificIR {
  /**
   * Wrappers around 32-bit PowerPC-specific IR
   */
  public static final class PPC32 extends OPT_MachineSpecificIRPowerPC {
    public static final PPC32 singleton = new PPC32();
    @Override
    public boolean mayEscapeThread(OPT_Instruction instruction) {
      switch (instruction.getOpcode()) {
      case DCBST_opcode:case DCBT_opcode:case DCBTST_opcode:
      case DCBZ_opcode:case DCBZL_opcode:case ICBI_opcode:
        return false;
      default:
        throw  new OPT_OptimizingCompilerException("OPT_SimpleEscapge: Unexpected " + instruction);
      }
    }
    @Override
    public boolean mayEscapeMethod(OPT_Instruction instruction) {
      return mayEscapeThread(instruction); // at this stage we're no more specific
    }
  }
  
  /**
   * Wrappers around 64-bit PowerPC-specific IR
   */
  public static final class PPC64 extends OPT_MachineSpecificIRPowerPC {
    public static final PPC64 singleton = new PPC64();
    @Override
    public final boolean mayEscapeThread(OPT_Instruction instruction) {
      switch (instruction.getOpcode()) {
      case DCBST_opcode:case DCBT_opcode:case DCBTST_opcode:
      case DCBZ_opcode:case DCBZL_opcode:case ICBI_opcode:
        return false;
      case LONG_OR_opcode: case LONG_AND_opcode: case LONG_XOR_opcode:
      case LONG_SUB_opcode:case LONG_SHL_opcode: case LONG_ADD_opcode:
      case LONG_SHR_opcode:case LONG_USHR_opcode:case LONG_NEG_opcode:
      case LONG_MOVE_opcode: case LONG_2ADDR_opcode:
        return true;
      default:
        throw  new OPT_OptimizingCompilerException("OPT_SimpleEscapge: Unexpected " + instruction);
      }
    }
    @Override
    public boolean mayEscapeMethod(OPT_Instruction instruction) {
      return mayEscapeThread(instruction); // at this stage we're no more specific
    }
  }
  
  
  /* 
   * Generic (32/64 neutral) PowerPC support
   */
 
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
