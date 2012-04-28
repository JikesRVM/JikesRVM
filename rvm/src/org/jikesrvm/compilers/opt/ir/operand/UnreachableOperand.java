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


/**
 * This operand represents, in a phi function, a control-flow path that is
 * actually unreachable.
 *
 * @see Operand
 */
public final class UnreachableOperand extends ConstantOperand {

  @Override
  public Operand copy() {
    return new UnreachableOperand();
  }

  @Override
  public boolean similar(Operand op) {
    return op instanceof UnreachableOperand;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  @Override
  public String toString() {
    return "<unreachable>";
  }
}
