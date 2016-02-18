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
package org.jikesrvm.compilers.opt.regalloc;

import java.util.Enumeration;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.GenericPhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.ia32.BURSManagedFPROperand;

/**
 * Insert Spill Code after register assignment.
 */
final class SpillCode extends CompilerPhase {
  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  @Override
  public boolean shouldPerform(OptOptions options) {
    return true;
  }

  @Override
  public String getName() {
    return "Spill Code";
  }

  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  /**
   *  Rewrites floating point registers to reflect changes in stack
   *  height induced by BURS.
   * <p>
   *  Side effect: update the fpStackHeight in MIRInfo.
   *
   *  @param ir the IR to process
   */
  private void rewriteFPStack(IR ir) {
    GenericPhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    for (Enumeration<BasicBlock> b = ir.getBasicBlocks(); b.hasMoreElements();) {
      BasicBlock bb = b.nextElement();

      // The following holds the floating point stack offset from its
      // 'normal' position.
      int fpStackOffset = 0;

      for (Enumeration<Instruction> inst = bb.forwardInstrEnumerator(); inst.hasMoreElements();) {
        Instruction s = inst.nextElement();
        for (Enumeration<Operand> ops = s.getOperands(); ops.hasMoreElements();) {
          Operand op = ops.nextElement();
          if (op.isRegister()) {
            RegisterOperand rop = op.asRegister();
            Register r = rop.getRegister();

            // Update MIR state for every physical FPR we see
            if (r.isPhysical() && r.isFloatingPoint() &&
                s.operator() != org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.DUMMY_DEF &&
                s.operator() != org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.DUMMY_USE) {
              int n = org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterSet.getFPRIndex(r);
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


  /**
   *  @param ir the IR
   */
  @Override
  public void perform(IR ir) {
    replaceSymbolicRegisters(ir);

    // Generate spill code if necessary
    if (ir.hasSysCall() || ir.MIRInfo.linearScanState.spilledSomething) {
      GenericStackManager stackMan = ir.stackManager;
      stackMan.insertSpillCode(ir.MIRInfo.linearScanState.active);
    }

    if (VM.BuildForIA32 && !VM.BuildForSSE2Full) {
      rewriteFPStack(ir);
    }
  }

  /**
   *  Iterates over the IR and replace each symbolic register with its
   *  allocated physical register.
   *
   *  @param ir the IR to process
   */
  private static void replaceSymbolicRegisters(IR ir) {
    RegisterAllocatorState regAllocState = ir.MIRInfo.regAllocState;

    for (Enumeration<Instruction> inst = ir.forwardInstrEnumerator(); inst.hasMoreElements();) {
      Instruction s = inst.nextElement();
      for (Enumeration<Operand> ops = s.getOperands(); ops.hasMoreElements();) {
        Operand op = ops.nextElement();
        if (op.isRegister()) {
          RegisterOperand rop = op.asRegister();
          Register r = rop.getRegister();
          if (r.isSymbolic() && !r.isSpilled()) {
            Register p = regAllocState.getMapping(r);
            if (VM.VerifyAssertions) VM._assert(p != null);
            rop.setRegister(p);
          }
        }
      }
    }
  }

}
