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
package org.jikesrvm.compilers.opt.ir.ppc;

import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch2;
import org.jikesrvm.compilers.opt.ir.MIR_Load;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.MachineSpecificIR;
import static org.jikesrvm.compilers.opt.ir.Operators.DCBST_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DCBTST_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DCBT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DCBZL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DCBZ_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.ICBI_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_2ADDR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_ADD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_AND_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_NEG_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_OR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_SHL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_SHR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_SUB_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_USHR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_XOR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_BCOND;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.ppc.PowerPCConditionOperand;

/**
 * Wrappers around PowerPC-specific IR common to both 32 & 64 bit
 */
public abstract class MachineSpecificIRPowerPC extends MachineSpecificIR {
  /**
   * Wrappers around 32-bit PowerPC-specific IR
   */
  public static final class PPC32 extends MachineSpecificIRPowerPC {
    public static final PPC32 singleton = new PPC32();

    @Override
    public boolean mayEscapeThread(Instruction instruction) {
      switch (instruction.getOpcode()) {
        case DCBST_opcode:
        case DCBT_opcode:
        case DCBTST_opcode:
        case DCBZ_opcode:
        case DCBZL_opcode:
        case ICBI_opcode:
          return false;
        default:
          throw new OptimizingCompilerException("SimpleEscapge: Unexpected " + instruction);
      }
    }

    @Override
    public boolean mayEscapeMethod(Instruction instruction) {
      return mayEscapeThread(instruction); // at this stage we're no more specific
    }
  }

  /**
   * Wrappers around 64-bit PowerPC-specific IR
   */
  public static final class PPC64 extends MachineSpecificIRPowerPC {
    public static final PPC64 singleton = new PPC64();

    @Override
    public boolean mayEscapeThread(Instruction instruction) {
      switch (instruction.getOpcode()) {
        case DCBST_opcode:
        case DCBT_opcode:
        case DCBTST_opcode:
        case DCBZ_opcode:
        case DCBZL_opcode:
        case ICBI_opcode:
          return false;
        case LONG_OR_opcode:
        case LONG_AND_opcode:
        case LONG_XOR_opcode:
        case LONG_SUB_opcode:
        case LONG_SHL_opcode:
        case LONG_ADD_opcode:
        case LONG_SHR_opcode:
        case LONG_USHR_opcode:
        case LONG_NEG_opcode:
        case LONG_MOVE_opcode:
        case LONG_2ADDR_opcode:
          return true;
        default:
          throw new OptimizingCompilerException("SimpleEscapge: Unexpected " + instruction);
      }
    }

    @Override
    public boolean mayEscapeMethod(Instruction instruction) {
      return mayEscapeThread(instruction); // at this stage we're no more specific
    }
  }

  /*
  * Generic (32/64 neutral) PowerPC support
  */

  /* common to all ISAs */

  @Override
  public final boolean isConditionOperand(Operand operand) {
    return operand instanceof PowerPCConditionOperand;
  }

  @Override
  public final void mutateMIRCondBranch(Instruction cb) {
    MIR_CondBranch.mutate(cb,
                          PPC_BCOND,
                          MIR_CondBranch2.getValue(cb),
                          MIR_CondBranch2.getCond1(cb),
                          MIR_CondBranch2.getTarget1(cb),
                          MIR_CondBranch2.getBranchProfile1(cb));
  }

  @Override
  public final boolean isHandledByRegisterUnknown(char opcode) {
    switch (opcode) {
      case DCBST_opcode:
      case DCBT_opcode:
      case DCBTST_opcode:
      case DCBZ_opcode:
      case DCBZL_opcode:
      case ICBI_opcode:
        return true;
      default:
        return false;
    }
  }

  /* unique to PowerPC */
  @Override
  public final boolean isPowerPCTrapOperand(Operand operand) {
    return operand instanceof PowerPCConditionOperand;
  }

  @Override
  public final boolean canFoldNullCheckAndLoad(Instruction s) {
    if (MIR_Load.conforms(s)) {
      Operand offset = MIR_Load.getOffset(s);
      if (offset instanceof IntConstantOperand) {
        return ((IntConstantOperand) offset).value < 0;
      }
    }
    return false;
  }
}
