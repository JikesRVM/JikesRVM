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

  @Override
  public Operand copy() {
    return new BasicBlockOperand(block);
  }

  @Override
  public boolean similar(Operand op) {
    return (op instanceof BasicBlockOperand) && (block == ((BasicBlockOperand) op).block);
  }

  @Override
  public String toString() {
    return block.toString();
  }
}
