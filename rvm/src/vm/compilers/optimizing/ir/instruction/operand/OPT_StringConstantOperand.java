/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

/**
 * Represents a constant string operand.
 *
 * @see OPT_Operand
 * @author John Whaley
 */
public final class OPT_StringConstantOperand extends OPT_ConstantOperand {

  /**
   * The string value
   */
  public String value;

  /**
   * Index in JTOC where this string constant lives.
   */
  public int index;

  /**
   * Construct a new string constant operand
   *
   * @param v the string constant
   * @param i JTOC index of the string constant
   */
  public OPT_StringConstantOperand(String v, int i) {
    value = v;
    index = i;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
    return new OPT_StringConstantOperand(value, index);
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
    return (op instanceof OPT_StringConstantOperand) &&
      (value.equals(((OPT_StringConstantOperand)op).value));
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return "\""+ value + "\"";
  }
}
