/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.Enumeration;

/**
 * An object that returns an estimate of the relative cost of spilling a 
 * symbolic register, based on loop depth.
 *
 * @author Stephen Fink
 */
class OPT_LoopDepthSpillCost extends OPT_SpillCostEstimator {

  private OPT_LoopDepthEdgeCounts loopDepth = new OPT_LoopDepthEdgeCounts();

  OPT_LoopDepthSpillCost(OPT_IR ir) {
    loopDepth.initialize(ir);
    loopDepth.updateCFGFrequencies(ir);
    calculate(ir);
  }

  /**
   * Calculate the estimated cost for each register.  
   */
  void calculate(OPT_IR ir) {
    for (Enumeration blocks = ir.getBasicBlocks(); blocks.hasMoreElements(); ) {
      OPT_BasicBlock bb = (OPT_BasicBlock)blocks.nextElement();
      for (Enumeration e = bb.forwardInstrEnumerator(); e.hasMoreElements(); ) {
        OPT_Instruction s = (OPT_Instruction)e.nextElement();

        double factor = loopDepth.getBasicBlockFrequency(bb);
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
