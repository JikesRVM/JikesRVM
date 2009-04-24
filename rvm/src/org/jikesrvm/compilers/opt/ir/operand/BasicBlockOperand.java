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
package org.jikesrvm.compilers.opt.ir.operand;

import org.jikesrvm.compilers.opt.ir.BasicBlock;

/**
 * Represents a basic block (used in LABEL and BBEND instructions)
 *
 * @see Operand
 */
public final class BasicBlockOperand extends Operand {

  /**
   * The basic block
   */
  public BasicBlock block;

  /**
   * Construct a new basic block operand with the given block.
   *
   * @param b the basic block
   */
  public BasicBlockOperand(BasicBlock b) {
    block = b;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   *
   * @return a copy of <code>this</code>
   */
  public Operand copy() {
    return new BasicBlockOperand(block);
  }

  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code>
   *           if they are not.
   */
  public boolean similar(Operand op) {
    return (op instanceof BasicBlockOperand) && (block == ((BasicBlockOperand) op).block);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return block.toString();
  }
}
