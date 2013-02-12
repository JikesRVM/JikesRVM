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
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;

/**
 * Represents a constant code operand, found for example, from an
 * TIBConstantOperand.<p>
 *
 * NB we don't use an object constant operand
 * because
 * <ol>
 *   <li>code doesn't form part of the object literals
 *   <li>we need to support replacement
 * </ol>
 *
 * @see Operand
 */
public final class CodeConstantOperand extends ConstantOperand {

  /**
   * The non-{@code null} method for the code represent
   */
  public final RVMMethod value;

  /**
   * Construct a new code constant operand
   *
   * @param v the method of this TIB
   */
  public CodeConstantOperand(RVMMethod v) {
    if (VM.VerifyAssertions) VM._assert(v != null);
    value = v;
  }

  @Override
  public Operand copy() {
    return new CodeConstantOperand(value);
  }

  /**
   * @return {@link TypeReference#CodeArray}
   */
  @Override
  public TypeReference getType() {
    return TypeReference.CodeArray;
  }

  /**
   * @return <code>true</code>
   */
  @Override
  public boolean isRef() {
    return true;
  }

  @Override
  public boolean similar(Operand op) {
    return (op instanceof CodeConstantOperand) && value == ((CodeConstantOperand) op).value;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  @Override
  public String toString() {
    return "code \"" + value + "\"";
  }
}
