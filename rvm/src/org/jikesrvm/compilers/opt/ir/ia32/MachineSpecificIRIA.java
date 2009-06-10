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
package org.jikesrvm.compilers.opt.ir.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch2;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.MachineSpecificIR;
import org.jikesrvm.compilers.opt.ir.OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.Operator;
import static org.jikesrvm.compilers.opt.ir.Operators.ADVISE_ESP;
import static org.jikesrvm.compilers.opt.ir.Operators.DUMMY_DEF;
import static org.jikesrvm.compilers.opt.ir.Operators.DUMMY_USE;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_CURRENT_PROCESSOR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FCLEAR;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FMOV;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FMOV_ENDING_LIVE_RANGE;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FNINIT;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_JCC;
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
import static org.jikesrvm.compilers.opt.ir.Operators.NOP;
import static org.jikesrvm.compilers.opt.ir.Operators.PREFETCH_opcode;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.ia32.BURSManagedFPROperand;
import org.jikesrvm.compilers.opt.ir.operand.ia32.IA32ConditionOperand;
import org.jikesrvm.compilers.opt.regalloc.LiveIntervalElement;

/**
 * Wrappers around IA32-specific IR common to both 32 & 64 bit
 */
public abstract class MachineSpecificIRIA extends MachineSpecificIR {

  /**
   * Wrappers around IA32-specific IR (32-bit specific)
   */
  public static final class IA32 extends MachineSpecificIRIA {
    public static final IA32 singleton = new IA32();

    /* common to all ISAs */
    @Override
    public boolean mayEscapeThread(Instruction instruction) {
      switch (instruction.getOpcode()) {
        case PREFETCH_opcode:
          return false;
        case GET_CURRENT_PROCESSOR_opcode:
          return true;
        default:
          throw new OptimizingCompilerException("SimpleEscape: Unexpected " + instruction);
      }
    }

    @Override
    public boolean mayEscapeMethod(Instruction instruction) {
      return mayEscapeThread(instruction); // at this stage we're no more specific
    }
  }

  /**
   * Wrappers around EMT64-specific IR (64-bit specific)
   */
  public static final class EM64T extends MachineSpecificIRIA {
    public static final EM64T singleton = new EM64T();

    /* common to all ISAs */
    @Override
    public boolean mayEscapeThread(Instruction instruction) {
      switch (instruction.getOpcode()) {
        case PREFETCH_opcode:
          return false;
        case GET_CURRENT_PROCESSOR_opcode:
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
  * Generic (32/64 neutral) IA support
  */

  /* common to all ISAs */

  @Override
  public boolean isConditionOperand(Operand operand) {
    return operand instanceof IA32ConditionOperand;
  }

  @Override
  public void mutateMIRCondBranch(Instruction cb) {
    MIR_CondBranch.mutate(cb,
                          IA32_JCC,
                          MIR_CondBranch2.getCond1(cb),
                          MIR_CondBranch2.getTarget1(cb),
                          MIR_CondBranch2.getBranchProfile1(cb));
  }

  @Override
  public boolean isHandledByRegisterUnknown(char opcode) {
    return (opcode == PREFETCH_opcode);
  }

  /* unique to IA */
  @Override
  public boolean isAdviseESP(Operator operator) {
    return operator == ADVISE_ESP;
  }

  @Override
  public boolean isFClear(Operator operator) {
    return operator == IA32_FCLEAR;
  }

  @Override
  public boolean isFNInit(Operator operator) {
    return operator == IA32_FNINIT;
  }

  @Override
  public boolean isBURSManagedFPROperand(Operand operand) {
    return operand instanceof BURSManagedFPROperand;
  }

  @Override
  public int getBURSManagedFPRValue(Operand operand) {
    return ((BURSManagedFPROperand) operand).regNum;
  }

  /**
   * Mutate FMOVs that end live ranges
   *
   * @param live The live interval for a basic block/reg pair
   * @param register The register for this live interval
   * @param dfnbegin The (adjusted) begin for this interval
   * @param dfnend The (adjusted) end for this interval
   */
  @Override
  public boolean mutateFMOVs(LiveIntervalElement live, Register register, int dfnbegin, int dfnend) {
    Instruction end = live.getEnd();
    if (end != null && end.operator == IA32_FMOV) {
      if (dfnend == dfnbegin) {
        // if end, an FMOV, both begins and ends the live range,
        // then end is dead.  Change it to a NOP and return null.
        Empty.mutate(end, NOP);
        return false;
      } else {
        if (!end.isPEI()) {
          if (VM.VerifyAssertions) {
            Operand value = MIR_Move.getValue(end);
            VM._assert(value.isRegister());
            VM._assert(MIR_Move.getValue(end).asRegister().getRegister() == register);
          }
          end.operator = IA32_FMOV_ENDING_LIVE_RANGE;
        }
      }
    }
    return true;
  }

  /**
   *  Rewrite floating point registers to reflect changes in stack
   *  height induced by BURS.
   *
   *  Side effect: update the fpStackHeight in MIRInfo
   */
  public void rewriteFPStack(IR ir) {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    for (BasicBlockEnumeration b = ir.getBasicBlocks(); b.hasMoreElements();) {
      BasicBlock bb = b.nextElement();

      // The following holds the floating point stack offset from its
      // 'normal' position.
      int fpStackOffset = 0;

      for (InstructionEnumeration inst = bb.forwardInstrEnumerator(); inst.hasMoreElements();) {
        Instruction s = inst.next();
        for (OperandEnumeration ops = s.getOperands(); ops.hasMoreElements();) {
          Operand op = ops.next();
          if (op.isRegister()) {
            RegisterOperand rop = op.asRegister();
            Register r = rop.getRegister();

            // Update MIR state for every physical FPR we see
            if (r.isPhysical() && r.isFloatingPoint() && s.operator() != DUMMY_DEF && s.operator() != DUMMY_USE) {
              int n = PhysicalRegisterSet.getFPRIndex(r);
              if (fpStackOffset != 0) {
                n += fpStackOffset;
                rop.setRegister(phys.getFPR(n));
              }
              ir.MIRInfo.fpStackHeight = Math.max(ir.MIRInfo.fpStackHeight, n + 1);
            }
          } else if (op instanceof BURSManagedFPROperand) {
            int regNum = ((BURSManagedFPROperand) op).regNum;
            s.replaceOperand(op, new RegisterOperand(phys.getFPR(regNum), TypeReference.Double));
          }
        }
        // account for any effect s has on the floating point stack
        // position.
        if (s.operator().isFpPop()) {
          fpStackOffset--;
        } else if (s.operator().isFpPush()) {
          fpStackOffset++;
        }
        if (VM.VerifyAssertions) VM._assert(fpStackOffset >= 0);
      }
    }
  }
}
