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

import org.jikesrvm.classloader.TypeReference;

/**
 * This operand represents a "true" guard.
 * Eg non-nullness of the result of an allocation or
 * boundcheck eliminate via analysis of the loop induction variables.
 *
 * @see Operand
 */
public final class TrueGuardOperand extends ConstantOperand {

  /**
   * Return the {@link TypeReference} of the value represented by the operand.
   *
   * @return TypeReference.VALIDATION_TYPE
   */
  @Override
  public TypeReference getType() {
    return TypeReference.VALIDATION_TYPE;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   *
   * @return a copy of <code>this</code>
   */
  @Override
  public Operand copy() {
    return new TrueGuardOperand();
  }

  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code>
   *           if they are not.
   */
  @Override
  public boolean similar(Operand op) {
    return op instanceof TrueGuardOperand;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  @Override
  public String toString() {
    return "<TRUEGUARD>";
  }
}
