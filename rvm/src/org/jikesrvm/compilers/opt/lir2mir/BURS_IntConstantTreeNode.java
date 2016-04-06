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
package org.jikesrvm.compilers.opt.lir2mir;

import org.jikesrvm.compilers.opt.ir.Operators;

/**
 * A subclass of BURS_TreeNode for an IntConstantOperand.<p>
 *
 * It is very common for us to want to access the value of an
 * int constant during BURS, so we make it easy to do so by creating
 * a special kind of node.
 */
final class BURS_IntConstantTreeNode extends AbstractBURS_TreeNode {

  /** The int constant value associated with this tree node */
  final int value;

  /** Where costs and rules are stored */
  private final AbstractBURS_TreeNode delegate =
    AbstractBURS_TreeNode.create(Operators.INT_CONSTANT_opcode);

  /**
   * Constructor for interior node.
   *
   * @param val a constant int value
   */
  BURS_IntConstantTreeNode(int val) {
    super(Operators.INT_CONSTANT_opcode);
    value = val;
    setNumRegisters(0);
  }

  @Override
  public String toString() {
    return "INT_CONSTANT " + value;
  }

 /**
  * Gets the BURS rule number associated with this tree node for a given non-terminal
  *
  * @param goalNT the non-terminal we want to know the rule for (e.g. stm_NT)
  * @return the rule number
  */
  @Override
  public int rule(int goalNT) {
    return delegate.rule(goalNT);
  }

  @Override
  public char getCost(int goalNT) {
    return delegate.getCost(goalNT);
  }

  @Override
  public void setCost(int goalNT, char cost) {
    delegate.setCost(goalNT, cost);;
  }

  @Override
  public void initCost() {
    delegate.initCost();
  }

  @Override
  public void writePacked(int word, int mask, int shiftedValue) {
    delegate.writePacked(word, mask, shiftedValue);
  }

  @Override
  public int readPacked(int word, int shift, int mask) {
    return delegate.readPacked(word, shift, mask);
  }
}
