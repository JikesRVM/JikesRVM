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
 * This operand represents the null constant.
 *
 * @see Operand
 */
public final class NullConstantOperand extends ConstantOperand {

  @Override
  public Operand copy() {
    return new NullConstantOperand();
  }

  /**
   * @return TypeReference.NULL_TYPE
   */
  @Override
  public TypeReference getType() {
    return TypeReference.NULL_TYPE;
  }

  /**
   * @return <code>true</code>
   */
  @Override
  public boolean isRef() {
    return true;
  }

  /**
   * @return <code>true</code>
   */
  @Override
  public boolean isDefinitelyNull() {
    return true;
  }

  @Override
  public boolean similar(Operand op) {
    return op instanceof NullConstantOperand;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  @Override
  public String toString() {
    return "<null>";
  }
}
