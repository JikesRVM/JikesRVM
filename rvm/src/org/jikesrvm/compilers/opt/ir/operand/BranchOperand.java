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

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Label;

/**
 * Represents a branch target.
 *
 * @see Operand
 */
public final class BranchOperand extends Operand {

  /**
   * Target of this branch.
   */
  public Instruction target;

  /**
   * Construct a new branch operand with the given target.
   * <STRONG> Precondition: </STRONG> targ must be a Label instruction.
   *
   * @param targ target of branch
   */
  public BranchOperand(Instruction targ) {
    if (VM.VerifyAssertions) VM._assert(Label.conforms(targ));
    target = targ;
  }

  /**
   * Returns a copy of this branch operand.
   *
   * @return a copy of this operand
   */
  public Operand copy() {
    return new BranchOperand(target);
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
    return (op instanceof BranchOperand) && (target == ((BranchOperand) op).target);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return "LABEL" + Label.getBlock(target).block.getNumber();
  }

}




