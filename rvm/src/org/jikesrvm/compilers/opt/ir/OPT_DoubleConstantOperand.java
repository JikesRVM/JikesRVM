/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.vmmagic.unboxed.Offset;

/**
 * Represents a constant double operand.
 *
 * @see OPT_Operand
 */

public final class OPT_DoubleConstantOperand extends OPT_ConstantOperand implements VM_SizeConstants {

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
  public OPT_DoubleConstantOperand(double v) {
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
  public OPT_DoubleConstantOperand(double v, Offset i) {
    value = v;
    offset = i;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   *
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
    return new OPT_DoubleConstantOperand(value, offset);
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
  public boolean similar(OPT_Operand op) {
    return (op instanceof OPT_DoubleConstantOperand) &&
           (value == ((OPT_DoubleConstantOperand) op).value);
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
