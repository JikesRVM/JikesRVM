/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_IR;

/**
 * This class implements the MEET operation for the 
 * dataflow equations for the dominator calculation.
 */
class OPT_DominatorOperator extends OPT_DF_Operator {

  /** 
   * Evaluate an equation with the MEET operation 
   * @param operands the lhs(operands[0]) and rhs(operands[1])
   *       of the equation.
   * @return true if the value of the lhs changes. false otherwise
   */
  boolean evaluate (OPT_DF_LatticeCell[] operands) {
    OPT_DominatorCell lhs = (OPT_DominatorCell)operands[0];
    OPT_IR ir = lhs.ir;
    OPT_BasicBlock bb = lhs.block;
    OPT_BitVector oldSet = (OPT_BitVector)lhs.dominators.clone();
    OPT_BitVector newDominators = new OPT_BitVector(ir.getMaxBasicBlockNumber()+1);
    if (operands.length > 1) {
      if (operands[1] != null) {
        newDominators.or(((OPT_DominatorCell)operands[1]).dominators);
      }
    }
    for (int i = 2; i < operands.length; i++) {
      newDominators.and(((OPT_DominatorCell)operands[i]).dominators);
    }
    newDominators.set(bb.getNumber());
    lhs.dominators = newDominators;
    return !lhs.dominators.equals(oldSet);
  }

  /** 
   * Return a String representation of the operator
   * @return "MEET"
   */
  public String toString () {
    return  "MEET";
  }
  /**
   * A singleton instance of this class.
   */
  static final OPT_DominatorOperator MEET = new OPT_DominatorOperator();
}
