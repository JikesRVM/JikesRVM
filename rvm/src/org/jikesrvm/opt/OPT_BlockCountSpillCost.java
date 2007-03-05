/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.opt;

import org.jikesrvm.opt.ir.*;
import java.util.Enumeration;
import java.util.Iterator;

/**
 * An object that returns an estimate of the relative cost of spilling a 
 * symbolic register, based on basic block frequencies.
 *
 * @author Stephen Fink
 */
class OPT_BlockCountSpillCost extends OPT_SpillCostEstimator {

  OPT_BlockCountSpillCost(OPT_IR ir) {
    calculate(ir);
  }

  /**
   * Calculate the estimated cost for each register.  
   */
  void calculate(OPT_IR ir) {
    for (Enumeration<OPT_BasicBlock> blocks = ir.getBasicBlocks(); blocks.hasMoreElements(); ) {
      OPT_BasicBlock bb = blocks.nextElement();
      float freq = bb.getExecutionFrequency();
      for (Iterator<OPT_Instruction> e = bb.forwardInstrEnumerator(); e.hasNext(); ) {
        OPT_Instruction s = e.next();
        double factor = freq;

        if (s.isMove()) factor *= OPT_SimpleSpillCost.MOVE_FACTOR;
        double baseFactor = factor;
        if (OPT_SimpleSpillCost.hasBadSizeMemoryOperand(s)) {
          baseFactor *= OPT_SimpleSpillCost.MEMORY_OPERAND_FACTOR;
        }

        // first deal with non-memory operands
        for (OPT_OperandEnumeration e2 = s.getRootOperands(); e2.hasMoreElements(); ) {
          OPT_Operand op = e2.nextElement();
          if (op.isRegister()) {
            OPT_Register r = op.asRegister().register;
            if (r.isSymbolic()) {
              update(r,baseFactor);
            }
          }
        }
        // now handle memory operands
        factor *= OPT_SimpleSpillCost.MEMORY_OPERAND_FACTOR;
        for (OPT_OperandEnumeration e2 = s.getMemoryOperands(); e2.hasMoreElements(); ) {
          OPT_MemoryOperand M = (OPT_MemoryOperand)e2.nextElement();
          if (M.base != null) {
            OPT_Register r = M.base.register;
            if (r.isSymbolic()) {
              update(r,factor);
            }
          }
          if (M.index != null) {
            OPT_Register r = M.index.register;
            if (r.isSymbolic()) {
              update(r,factor);
            }
          }
        }
      }
    }
  }
}
