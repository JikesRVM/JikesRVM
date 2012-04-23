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
package org.jikesrvm.compilers.opt.controlflow;

import org.jikesrvm.compilers.opt.dfsolver.DF_LatticeCell;
import org.jikesrvm.compilers.opt.dfsolver.DF_Operator;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.util.BitVector;

/**
 * This class implements the MEET operation for the
 * dataflow equations for the dominator calculation.
 */
class DominatorOperator extends DF_Operator {
  /**
   * A singleton instance of this class.
   */
  static final DominatorOperator MEET = new DominatorOperator();

  /**
   * Evaluate an equation with the MEET operation
   * @param operands the lhs(operands[0]) and rhs(operands[1])
   *       of the equation.
   * @return true if the value of the lhs changes. false otherwise
   */
  @Override
  public boolean evaluate(DF_LatticeCell[] operands) {
    DominatorCell lhs = (DominatorCell) operands[0];
    IR ir = lhs.ir;
    BasicBlock bb = lhs.block;
    BitVector oldSet = lhs.dominators.dup();
    BitVector newDominators = new BitVector(ir.getMaxBasicBlockNumber() + 1);
    if (operands.length > 1) {
      if (operands[1] != null) {
        newDominators.or(((DominatorCell) operands[1]).dominators);
      }
    }
    for (int i = 2; i < operands.length; i++) {
      newDominators.and(((DominatorCell) operands[i]).dominators);
    }
    newDominators.set(bb.getNumber());
    lhs.dominators = newDominators;
    return !lhs.dominators.equals(oldSet);
  }

  /**
   * Return a String representation of the operator
   * @return "MEET"
   */
  @Override
  public String toString() {
    return "MEET";
  }
}
