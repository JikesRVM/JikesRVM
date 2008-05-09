/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ir.operand;

import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.vmmagic.unboxed.Offset;

/**
 * Represents a constant double operand.
 *
 * @see Operand
 */

public final class DoubleConstantOperand extends ConstantOperand implements VM_SizeConstants {

  /**
   * Value of this operand.
   */
  public double value;

  /**
   * Offset in JTOC where this double constant lives. (0 for constants
   * obtained from constant folding)
   */
  public Offset offset;

  /**
   * Constructs a new double constant operand with the specified value.
   *
   * @param v value
   */
  public DoubleConstantOperand(double v) {
    value = v;
    if (v == 0.) {
      offset = VM_Entrypoints.zeroDoubleField.getOffset();
    } else if (v == 1.) {
      offset = VM_Entrypoints.oneDoubleField.getOffset();
    } else {
      offset = Offset.zero();
    }
  }

  /**
   * Constructs a new double constant operand with the specified value and JTOC offset.
   *
   * @param v value
   * @param i offset in the jtoc
   */
  public DoubleConstantOperand(double v, Offset i) {
    value = v;
    offset = i;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   *
   * @return a copy of <code>this</code>
   */
  public Operand copy() {
    return new DoubleConstantOperand(value, offset);
  }

  /**
   * Return the {@link VM_TypeReference} of the value represented by the operand.
   *
   * @return VM_TypeReference.Double
   */
  public VM_TypeReference getType() {
    return VM_TypeReference.Double;
  }

  /**
   * Does the operand represent a value of the double data type?
   *
   * @return <code>true</code>
   */
  public boolean isDouble() {
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
    return (op instanceof DoubleConstantOperand) && (value == ((DoubleConstantOperand) op).value);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return Double.toString(value) + "D";
  }

}
