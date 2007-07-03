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
  boolean evaluate(OPT_DF_LatticeCell[] operands) {
    OPT_DominatorCell lhs = (OPT_DominatorCell) operands[0];
    OPT_IR ir = lhs.ir;
    OPT_BasicBlock bb = lhs.block;
    OPT_BitVector oldSet = lhs.dominators.dup();
    OPT_BitVector newDominators = new OPT_BitVector(ir.getMaxBasicBlockNumber() + 1);
    if (operands.length > 1) {
      if (operands[1] != null) {
        newDominators.or(((OPT_DominatorCell) operands[1]).dominators);
      }
    }
    for (int i = 2; i < operands.length; i++) {
      newDominators.and(((OPT_DominatorCell) operands[i]).dominators);
    }
    newDominators.set(bb.getNumber());
    lhs.dominators = newDominators;
    return !lhs.dominators.equals(oldSet);
  }

  /**
   * Return a String representation of the operator
   * @return "MEET"
   */
  public String toString() {
    return "MEET";
  }

  /**
   * A singleton instance of this class.
   */
  static final OPT_DominatorOperator MEET = new OPT_DominatorOperator();
}
