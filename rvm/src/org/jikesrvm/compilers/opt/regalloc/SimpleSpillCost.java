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
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.MemoryOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;

/**
 * An object that returns an estimate of the relative cost of spilling a
 * symbolic register.
 */
class SimpleSpillCost extends SpillCostEstimator {

  SimpleSpillCost(IR ir) {
    calculate(ir);
  }

  /**
   * Calculate the estimated cost for each register.
   */
  void calculate(IR ir) {
    final double moveFactor = ir.options.REGALLOC_SIMPLE_SPILL_COST_MOVE_FACTOR;
    final double memoryOperandFactor = ir.options.REGALLOC_SIMPLE_SPILL_COST_MEMORY_OPERAND_FACTOR;
    for (Enumeration<BasicBlock> e = ir.getBasicBlocks(); e.hasMoreElements();) {
      BasicBlock bb = e.nextElement();
      for (Enumeration<Instruction> ie = bb.forwardInstrEnumerator(); ie.hasMoreElements();) {
        Instruction s = ie.nextElement();
        double factor = (bb.getInfrequent()) ? 0.0 : 1.0;
        if (s.isMove()) {
          factor *= moveFactor;
        }
        double baseFactor = factor;
        if (hasBadSizeMemoryOperand(s)) {
          baseFactor *= memoryOperandFactor;
        }
        // first deal with non-memory operands
        for (Enumeration<Operand> e2 = s.getRootOperands(); e2.hasMoreElements();) {
          Operand op = e2.nextElement();
          if (op.isRegister()) {
            Register r = op.asRegister().getRegister();
            if (r.isSymbolic()) {
              update(r, baseFactor);
            }
          }
        }
        // now handle memory operands
        factor *= memoryOperandFactor;
        for (Enumeration<Operand> e2 = s.getMemoryOperands(); e2.hasMoreElements();) {
          MemoryOperand M = (MemoryOperand) e2.nextElement();
          if (M.base != null) {
            Register r = M.base.getRegister();
            if (r.isSymbolic()) {
              update(r, factor);
            }
          }
          if (M.index != null) {
            Register r = M.index.getRegister();
            if (r.isSymbolic()) {
              update(r, factor);
            }
          }
        }
      }
    }
  }

  /**
   * Does instruction s have a memory operand of an inconvenient size?
   * NOTE: This is pretty intel-specific.  Refactor to arch/ tree.
   */
  static boolean hasBadSizeMemoryOperand(Instruction s) {
    for (Enumeration<Operand> e = s.getMemoryOperands(); e.hasMoreElements();) {
      MemoryOperand M = (MemoryOperand) e.nextElement();
      if (M.size != 4) return true;
    }
    return false;
  }
}
