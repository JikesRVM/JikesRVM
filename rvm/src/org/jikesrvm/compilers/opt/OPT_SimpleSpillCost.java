/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import java.util.Enumeration;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_MemoryOperand;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_Register;

/**
 * An object that returns an estimate of the relative cost of spilling a
 * symbolic register.
 */
class OPT_SimpleSpillCost extends OPT_SpillCostEstimator {

  // modify the following factor to adjust the spill penalty in move
  // instructions
  public static final double MOVE_FACTOR = 1.0;

  // registers used in memory operands may hurt more than 'normal', since
  // they will definitely use a scratch register.
  // rationale for 5: 5 instructions affected when using a scratch register.
  // (2 to save physical register, 1 to load scratch, 1 to dump scratch, and
  // the original)
  public static final double MEMORY_OPERAND_FACTOR = 5.0;

  OPT_SimpleSpillCost(OPT_IR ir) {
    calculate(ir);
  }

  /**
   * Calculate the estimated cost for each register.
   */
  void calculate(OPT_IR ir) {
    for (Enumeration<OPT_BasicBlock> e = ir.getBasicBlocks(); e.hasMoreElements();) {
      OPT_BasicBlock bb = e.nextElement();
      for (Enumeration<OPT_Instruction> ie = bb.forwardInstrEnumerator(); ie.hasMoreElements();) {
        OPT_Instruction s = ie.nextElement();
        double factor = (bb.getInfrequent()) ? 0.0 : 1.0;
        if (s.isMove()) {
          factor *= MOVE_FACTOR;
        }
        double baseFactor = factor;
        if (hasBadSizeMemoryOperand(s)) {
          baseFactor *= MEMORY_OPERAND_FACTOR;
        }
        // first deal with non-memory operands
        for (Enumeration<OPT_Operand> e2 = s.getRootOperands(); e2.hasMoreElements();) {
          OPT_Operand op = e2.nextElement();
          if (op.isRegister()) {
            OPT_Register r = op.asRegister().getRegister();
            if (r.isSymbolic()) {
              update(r, baseFactor);
            }
          }
        }
        // now handle memory operands
        factor *= MEMORY_OPERAND_FACTOR;
        for (Enumeration<OPT_Operand> e2 = s.getMemoryOperands(); e2.hasMoreElements();) {
          OPT_MemoryOperand M = (OPT_MemoryOperand) e2.nextElement();
          if (M.base != null) {
            OPT_Register r = M.base.getRegister();
            if (r.isSymbolic()) {
              update(r, factor);
            }
          }
          if (M.index != null) {
            OPT_Register r = M.index.getRegister();
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
  static boolean hasBadSizeMemoryOperand(OPT_Instruction s) {
    for (Enumeration<OPT_Operand> e = s.getMemoryOperands(); e.hasMoreElements();) {
      OPT_MemoryOperand M = (OPT_MemoryOperand) e.nextElement();
      if (M.size != 4) return true;
    }
    return false;
  }
}
