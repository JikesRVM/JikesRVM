/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.Enumeration;

/**
 * An object that returns an estimate of the relative cost of spilling a 
 * symbolic register.
 *
 * @author Stephen Fink
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
    for (Enumeration e = ir.forwardInstrEnumerator(); e.hasMoreElements(); ) {
      OPT_Instruction s = (OPT_Instruction)e.nextElement();
      double factor = 1.0;
      if (s.isMove()) factor *= MOVE_FACTOR;
      // first deal with non-memory operands
      for (Enumeration e2 = s.getRootOperands(); e2.hasMoreElements(); ) {
        OPT_Operand op = (OPT_Operand)e2.nextElement();
        if (op.isRegister()) {
          OPT_Register r = op.asRegister().register;
          if (r.isSymbolic()) {
            update(r,factor);
          }
        }
      }
      // now handle memory operands
      factor *= MEMORY_OPERAND_FACTOR;
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
