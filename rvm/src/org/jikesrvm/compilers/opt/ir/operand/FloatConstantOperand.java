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

import org.jikesrvm.SizeConstants;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.runtime.Entrypoints;
import org.vmmagic.unboxed.Offset;

/**
 * Represents a constant float operand.
 *
 * @see Operand
 */
public final class FloatConstantOperand extends ConstantOperand implements SizeConstants {

  /**
   * Value of this operand.
   */
  public float value;

  /**
   * Offset in JTOC where this float constant lives (0 for constants
   * generated from constant folding).
   */
  public Offset offset;

  /**
   * Constructs a new float constant operand with the specified value.
   *
   * @param v value
   */
  public FloatConstantOperand(float v) {
    value = v;
    if (v == 0.f) {
      offset = Entrypoints.zeroFloatField.getOffset();
    } else if (v == 1.f) {
      offset = Entrypoints.oneFloatField.getOffset();
    } else if (v == 2.f) {
      offset = Entrypoints.twoFloatField.getOffset();
    } else {
      offset = Offset.zero();
    }
  }

  /**
   * Constructs a new float constant operand with the specified value and JTOC offset.
   *
   * @param v value
   * @param i offset in the jtoc
   */
  public FloatConstantOperand(float v, Offset i) {
    value = v;
    offset = i;
  }

  @Override
  public Operand copy() {
    return new FloatConstantOperand(value, offset);
  }

  /**
   * @return {@link TypeReference#Float}
   */
  @Override
  public TypeReference getType() {
    return TypeReference.Float;
  }

  /**
   * @return <code>true</code>
   */
  @Override
  public boolean isFloat() {
    return true;
  }

  @Override
  public boolean similar(Operand op) {
    return (op instanceof FloatConstantOperand) && (value == ((FloatConstantOperand) op).value);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  @Override
  public String toString() {
    return Float.toString(value);
  }
}
