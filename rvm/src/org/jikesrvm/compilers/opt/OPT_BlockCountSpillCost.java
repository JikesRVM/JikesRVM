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
import org.jikesrvm.compilers.opt.ir.OPT_InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_MemoryOperand;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_Register;

/**
 * An object that returns an estimate of the relative cost of spilling a
 * symbolic register, based on basic block frequencies.
 */
class OPT_BlockCountSpillCost extends OPT_SpillCostEstimator {

  OPT_BlockCountSpillCost(OPT_IR ir) {
    calculate(ir);
  }

  /**
   * Calculate the estimated cost for each register.
   */
  void calculate(OPT_IR ir) {
    for (Enumeration<OPT_BasicBlock> blocks = ir.getBasicBlocks(); blocks.hasMoreElements();) {
      OPT_BasicBlock bb = blocks.nextElement();
      float freq = bb.getExecutionFrequency();
      for (OPT_InstructionEnumeration e = bb.forwardInstrEnumerator(); e.hasMoreElements();) {
        OPT_Instruction s = e.nextElement();
        double factor = freq;

        if (s.isMove()) factor *= OPT_SimpleSpillCost.MOVE_FACTOR;
        double baseFactor = factor;
        if (OPT_SimpleSpillCost.hasBadSizeMemoryOperand(s)) {
          baseFactor *= OPT_SimpleSpillCost.MEMORY_OPERAND_FACTOR;
        }

        // first deal with non-memory operands
        for (OPT_OperandEnumeration e2 = s.getRootOperands(); e2.hasMoreElements();) {
          OPT_Operand op = e2.nextElement();
          if (op.isRegister()) {
            OPT_Register r = op.asRegister().getRegister();
            if (r.isSymbolic()) {
              update(r, baseFactor);
            }
          }
        }
        // now handle memory operands
        factor *= OPT_SimpleSpillCost.MEMORY_OPERAND_FACTOR;
        for (OPT_OperandEnumeration e2 = s.getMemoryOperands(); e2.hasMoreElements();) {
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
}
