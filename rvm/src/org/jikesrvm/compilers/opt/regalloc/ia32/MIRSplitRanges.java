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
package org.jikesrvm.compilers.opt.regalloc.ia32;

import java.util.Enumeration;

import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.MIR_LowTableSwitch;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterTools;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

/**
 * This class splits live ranges for certain special cases to ensure
 * correctness during IA32 register allocation.
 */
public class MIRSplitRanges extends CompilerPhase implements Operators {

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

  /**
   * Return the name of this phase
   * @return "Live Range Splitting"
   */
  @Override
  public final String getName() {
    return "MIR Range Splitting";
  }

  /**
   * The main method.<p>
   *
   * We split live ranges for registers around PEIs which have catch
   * blocks.  Suppose we have a
   * PEI s which uses a symbolic register r1.  We must ensure that after
   * register allocation, r1 is NOT assigned to a scratch location in s,
   * since this would mess up code in the catch block that uses r1.<p>
   *
   * So, instead, we introduce a new temporary r2 which holds the value of
   * r1.  The live range for r2 spans only the instruction s.  Later, we
   * will ensure that r2 is never spilled.<p>
   *
   * TODO: This could be implemented more efficiently.
   *
   * @param ir the governing IR
   */
  @Override
  public final void perform(IR ir) {

    java.util.HashMap<Register, Register> newMap = new java.util.HashMap<Register, Register>(5);

    for (Enumeration<BasicBlock> be = ir.getBasicBlocks(); be.hasMoreElements();) {
      BasicBlock bb = be.nextElement();
      for (Enumeration<Instruction> ie = bb.forwardInstrEnumerator(); ie.hasMoreElements();) {
        Instruction s = ie.nextElement();;

        // clear the cache of register assignments
        newMap.clear();

        // Split live ranges at PEIs and a few special cases to
        // make sure we can pin values that must be in registers.
        // NOTE: Any operator that is an IA32 special case that must have
        //       a particular operand in a register must be mentioned both
        //       here and in RegisterRestrictions!
        if (s.isPEI() && s.operator != IR_PROLOGUE) {
          if (bb.hasApplicableExceptionalOut(s) || !RegisterRestrictions.SCRATCH_IN_PEI) {
            splitAllLiveRanges(s, newMap, ir, false);
          }
        }

        // handle special cases for IA32
        //  (1) Some operands must be in registers
        switch (s.getOpcode()) {
          case MIR_LOWTABLESWITCH_opcode: {
            RegisterOperand rOp = MIR_LowTableSwitch.getIndex(s);
            RegisterOperand temp = findOrCreateTemp(rOp, newMap, ir);
            // NOTE: Index as marked as a DU because LowTableSwitch is
            //       going to destroy the value in the register.
            //       By construction (see ConvertToLowLevelIR), no one will
            //       ever read the value computed by a LowTableSwitch.
            //       Therefore, don't insert a move instruction after the
            //       LowTableSwitch (which would cause IR verification
            //       problems anyways, since LowTableSwitch is a branch).
            insertMoveBefore(temp, rOp.copyRO(), s); // move r into 'temp' before s
            rOp.setRegister(temp.getRegister());
          }
          break;
        }
      }
    }
  }

  /**
   * Split the live ranges of all register operands of an instruction
   * @param s      the instruction to process
   * @param newMap a mapping from symbolics to temporaries
   * @param ir  the containing IR
   * @param rootOnly only consider root operands?
   */
  private static void splitAllLiveRanges(Instruction s, java.util.HashMap<Register, Register> newMap,
                                         IR ir, boolean rootOnly) {
    // walk over each USE
    for (Enumeration<Operand> u = rootOnly ? s.getRootUses() : s.getUses(); u.hasMoreElements();) {
      Operand use = u.nextElement();
      if (use.isRegister()) {
        RegisterOperand rUse = use.asRegister();
        RegisterOperand temp = findOrCreateTemp(rUse, newMap, ir);
        // move 'use' into 'temp' before s
        insertMoveBefore(temp, rUse.copyRO(), s);
      }
    }
    // walk over each DEF (by defintion defs == root defs)
    for (Enumeration<Operand> d = s.getDefs(); d.hasMoreElements();) {
      Operand def = d.nextElement();
      if (def.isRegister()) {
        RegisterOperand rDef = def.asRegister();
        RegisterOperand temp = findOrCreateTemp(rDef, newMap, ir);
        // move 'temp' into 'r' after s
        insertMoveAfter(rDef.copyRO(), temp, s);
      }
    }
    // Now go back and replace the registers.
    for (Enumeration<Operand> ops = rootOnly ? s.getRootOperands() : s.getOperands(); ops.hasMoreElements();) {
      Operand op = ops.nextElement();
      if (op.isRegister()) {
        RegisterOperand rOp = op.asRegister();
        Register r = rOp.getRegister();
        Register newR = newMap.get(r);
        if (newR != null) {
          rOp.setRegister(newR);
        }
      }
    }
  }

  /**
   * Find or create a temporary register to cache a symbolic register.
   *
   * @param rOp the symbolic register
   * @param map a mapping from symbolics to temporaries
   * @param ir the governing IR
   */
  private static RegisterOperand findOrCreateTemp(RegisterOperand rOp,
                                                      java.util.HashMap<Register, Register> map, IR ir) {
    Register tReg = map.get(rOp.getRegister());
    if (tReg == null) {
      RegisterOperand tOp = ir.regpool.makeTemp(rOp.getType());
      map.put(rOp.getRegister(), tOp.getRegister());
      return tOp;
    } else {
      return new RegisterOperand(tReg, rOp.getType());
    }
  }

  /**
   * Insert an instruction to move r1 into r2 before instruction s
   */
  private static void insertMoveBefore(RegisterOperand r2, RegisterOperand r1, Instruction s) {
    Instruction m = PhysicalRegisterTools.makeMoveInstruction(r2, r1);
    s.insertBefore(m);
  }

  /**
   * Insert an instruction to move r1 into r2 after instruction s
   */
  private static void insertMoveAfter(RegisterOperand r2, RegisterOperand r1, Instruction s) {
    Instruction m = PhysicalRegisterTools.makeMoveInstruction(r2, r1);
    s.insertAfter(m);
  }
}
