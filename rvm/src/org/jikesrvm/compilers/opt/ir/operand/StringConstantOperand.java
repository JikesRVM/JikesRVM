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
import org.vmmagic.unboxed.Offset;

/**
 * Represents a constant string operand.
 *
 * @see Operand
 */
public final class StringConstantOperand extends ObjectConstantOperand {

  /**
   * Construct a new string constant operand
   *
   * @param v the string constant
   * @param i JTOC offset of the string constant
   */
  public StringConstantOperand(String v, Offset i) {
    super(v, i);
  }

  @Override
  public Operand copy() {
    return new StringConstantOperand((String) value, offset);
  }

  /**
   * @return {@link TypeReference#JavaLangString}
   */
  @Override
  public TypeReference getType() {
    return TypeReference.JavaLangString;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  @Override
  public String toString() {
    return "string \"" + value + "\"";
  }
}
