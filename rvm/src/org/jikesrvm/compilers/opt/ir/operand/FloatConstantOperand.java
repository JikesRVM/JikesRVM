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

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   *
   * @return a copy of <code>this</code>
   */
  public Operand copy() {
    return new FloatConstantOperand(value, offset);
  }

  /**
   * Return the {@link TypeReference} of the value represented by the operand.
   *
   * @return TypeReference.Float
   */
  public TypeReference getType() {
    return TypeReference.Float;
  }

  /**
   * Does the operand represent a value of the float data type?
   *
   * @return <code>true</code>
   */
  public boolean isFloat() {
    return true;
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
    return (op instanceof FloatConstantOperand) && (value == ((FloatConstantOperand) op).value);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return Float.toString(value);
  }
}
