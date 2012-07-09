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
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;

/**
 * Represents a constant TIB operand, found for example, from an
 * ObjectConstantOperand.<p>
 * NB: we don't use an object constant operand because:
 * <ol>
 *   <li>TIBs don't form part of the object literals
 *   <li>Loads on the contents of a TIB can be turned into constant moves,
 *       whereas for arrays in general this isn't the case.
 * </ol>
 * <p>
 * NB: we don't use TypeOperand as the type of the operand is RVMType, whereas a
 * TIBs type is {@code Object[]}.
 *
 * @see Operand
 */
public final class TIBConstantOperand extends ConstantOperand {

  /**
   * The non-{@code null} type for this TIB
   */
  public final RVMType value;

  /**
   * Construct a new TIB constant operand
   *
   * @param v the type of this TIB
   */
  public TIBConstantOperand(RVMType v) {
    if (VM.VerifyAssertions) VM._assert(v != null);
    value = v;
  }

  @Override
  public Operand copy() {
    return new TIBConstantOperand(value);
  }

  /**
   * @return {@link TypeReference#TIB}
   */
  @Override
  public TypeReference getType() {
    return TypeReference.TIB;
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
    return (op instanceof TIBConstantOperand) && value == ((TIBConstantOperand) op).value;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  @Override
  public String toString() {
    return "tib \"" + value + "\"";
  }
}
