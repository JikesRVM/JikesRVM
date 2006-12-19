/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2006
 */
package com.ibm.jikesrvm.opt.ir;

import java.util.Enumeration;

import org.vmmagic.pragma.Inline;

import com.ibm.jikesrvm.VM;
import com.ibm.jikesrvm.classloader.VM_TypeReference;
import com.ibm.jikesrvm.opt.OPT_LiveIntervalElement;
import com.ibm.jikesrvm.opt.OPT_OptimizingCompilerException;

import static com.ibm.jikesrvm.opt.ir.OPT_Operators.*;

/**
 * Wrappers around IA32-specific IR common to both 32 & 64 bit
 * 
 * $Id: OPT_IA32ConditionOperand.java 10996 2006-11-16 23:37:12Z dgrove-oss $
 * 
 * @author Steve Blackburn
 */
public abstract class OPT_IAMachineSpecificIR extends OPT_MachineSpecificIR {
  /* common to all ISAs */
  @Override
  public boolean isConditionOperand(OPT_Operand operand) {
    return operand instanceof OPT_IA32ConditionOperand;
  }
  @Override
  public void mutateMIRCondBranch(OPT_Instruction cb) {
    MIR_CondBranch.mutate(cb, IA32_JCC,
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
  public boolean isAdviseESP(OPT_Operator operator) {
    return operator == ADVISE_ESP; 
  }
  @Override
  public boolean isFClear(OPT_Operator operator) {
    return operator == IA32_FCLEAR; 
  }
  @Override
  public boolean isFNInit(OPT_Operator operator) {
    return operator == IA32_FNINIT; 
  }
  
  @Override
  public boolean isBURSManagedFPROperand(OPT_Operand operand) {
    return operand instanceof OPT_BURSManagedFPROperand; 
  }
  @Override
  public int getBURSManagedFPRValue(OPT_Operand operand) {
    return Integer.valueOf(((OPT_BURSManagedFPROperand)operand).regNum);
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
  public boolean mutateFMOVs(OPT_LiveIntervalElement live, OPT_Register register,
      int dfnbegin, int dfnend) {
    OPT_Instruction end = live.getEnd();
    if (end != null && end.operator == IA32_FMOV) {
      if (dfnend == dfnbegin) {
        // if end, an FMOV, both begins and ends the live range,
        // then end is dead.  Change it to a NOP and return null. 
        Empty.mutate(end, NOP);
        return false;
      } else {
        if (!end.isPEI()) {
          if (VM.VerifyAssertions) {                    
            OPT_Operand value = MIR_Move.getValue(end);
            VM._assert(value.isRegister());
            VM._assert(MIR_Move.getValue(end).asRegister().register == register);
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
  public void rewriteFPStack(OPT_IR ir) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    for (Enumeration b = ir.getBasicBlocks(); b.hasMoreElements(); ) {
      OPT_BasicBlock bb = (OPT_BasicBlock)b.nextElement();

      // The following holds the floating point stack offset from its
      // 'normal' position.
      int fpStackOffset = 0;

      for (OPT_InstructionEnumeration inst = bb.forwardInstrEnumerator(); 
           inst.hasMoreElements();) {
        OPT_Instruction s = inst.next();
        for (OPT_OperandEnumeration ops = s.getOperands(); 
             ops.hasMoreElements(); ) {
          OPT_Operand op = ops.next();
          if (op.isRegister()) {
            OPT_RegisterOperand rop = op.asRegister();
            OPT_Register r = rop.register;

            // Update MIR state for every phyiscal FPR we see
            if (r.isPhysical() && r.isFloatingPoint() &&
                s.operator() != DUMMY_DEF && 
                s.operator() != DUMMY_USE) {
              int n = OPT_PhysicalRegisterSet.getFPRIndex(r);
              if (fpStackOffset != 0) {
                n += fpStackOffset;
                rop.register = phys.getFPR(n);
              }
              ir.MIRInfo.fpStackHeight = 
                Math.max(ir.MIRInfo.fpStackHeight, n+1);
            }
          } else if (op instanceof OPT_BURSManagedFPROperand) {
            int regNum = ((OPT_BURSManagedFPROperand)op).regNum;
            s.replaceOperand(op, new OPT_RegisterOperand(phys.getFPR(regNum), 
                                                         VM_TypeReference.Double));
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
