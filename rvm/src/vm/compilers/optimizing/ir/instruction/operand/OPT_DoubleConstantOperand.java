/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Represents a constant double operand.
 *
 * @see OPT_Operand
 * @author John Whaley
 * @modified Mauricio Serrano 6/6/98
 */

public final class OPT_DoubleConstantOperand extends OPT_ConstantOperand {

  /**
   * Value of this operand.
   */
  double value;

  /**
   * Index in JTOC where this double constant lives. (0 for constants
   * obtained from constant folding)
   */
  int index;

  /**
   * Constructs a new double constant operand with the specified value.
   *
   * @param v value
   */
  public OPT_DoubleConstantOperand(double v) {
    value = v;
    if (v == 0.) {
      index = VM_Entrypoints.zeroDoubleField.getOffset() >> 2;
    } else if (v == 1.) {
      index = VM_Entrypoints.oneDoubleField.getOffset() >> 2;
    }
  }

  /**
   * Constructs a new double constant operand with the specified value and JTOC index.
   *
   * @param v value
   * @param i index in the jtoc
   */
  OPT_DoubleConstantOperand(double v, int i) {
    value = v;
    index = i;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  OPT_Operand copy() {
    return new OPT_DoubleConstantOperand(value, index);
  }

  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code> 
   *           if they are not.
   */
  boolean similar(OPT_Operand op) {
    return (op instanceof OPT_DoubleConstantOperand)&&
           (value == ((OPT_DoubleConstantOperand)op).value);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return Double.toString(value)+"D";
  }

}
