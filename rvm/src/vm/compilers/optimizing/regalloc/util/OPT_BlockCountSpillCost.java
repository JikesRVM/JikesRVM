/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;
import java.util.Enumeration;

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
    for (Enumeration blocks = ir.getBasicBlocks(); blocks.hasMoreElements(); ) {
      OPT_BasicBlock bb = (OPT_BasicBlock)blocks.nextElement();
      float freq = bb.getExecutionFrequency();
      for (Enumeration e = bb.forwardInstrEnumerator(); e.hasMoreElements(); ) {
        OPT_Instruction s = (OPT_Instruction)e.nextElement();
        double factor = freq;

        if (s.isMove()) factor *= OPT_SimpleSpillCost.MOVE_FACTOR;
        double baseFactor = factor;
        if (OPT_SimpleSpillCost.hasBadSizeMemoryOperand(s)) {
          baseFactor *= OPT_SimpleSpillCost.MEMORY_OPERAND_FACTOR;
        }

        // first deal with non-memory operands
        for (Enumeration e2 = s.getRootOperands(); e2.hasMoreElements(); ) {
          OPT_Operand op = (OPT_Operand)e2.nextElement();
          if (op.isRegister()) {
            OPT_Register r = op.asRegister().register;
            if (r.isSymbolic()) {
              update(r,baseFactor);
            }
          }
        }
        // now handle memory operands
        factor *= OPT_SimpleSpillCost.MEMORY_OPERAND_FACTOR;
        for (Enumeration e2 = s.getMemoryOperands(); e2.hasMoreElements(); ) {
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
