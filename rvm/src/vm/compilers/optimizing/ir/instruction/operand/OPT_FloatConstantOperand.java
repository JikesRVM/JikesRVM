/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Represents a constant float operand.
 *
 * @see OPT_Operand
 * @author John Whaley
 */
public final class OPT_FloatConstantOperand extends OPT_ConstantOperand {

  /**
   * Value of this operand.
   */
  float value;

  /**
   * Index in JTOC where this float constant lives (0 for constants
   * generated from constant folding).
   */
  int index;

  /**
   * Constructs a new float constant operand with the specified value.
   *
   * @param v value
   */
  public OPT_FloatConstantOperand(float v) {
    value = v;
    if (v == 0.f) {
      index = VM_Entrypoints.zeroFloatField.getOffset() >> 2;
    } else if (v == 1.f) {
      index = VM_Entrypoints.oneFloatField.getOffset() >> 2;
    } else if (v == 2.f) {
      index = VM_Entrypoints.twoFloatField.getOffset() >> 2;
    }
  }

  /**
   * Constructs a new float constant operand with the specified value and JTOC index.
   *
   * @param v value
   * @param i index in the jtoc
   */
  OPT_FloatConstantOperand(float v, int i) {
    value = v;
    index = i;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  OPT_Operand copy() {
    return new OPT_FloatConstantOperand(value,index);
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
    return (op instanceof OPT_FloatConstantOperand) &&
	   (value == ((OPT_FloatConstantOperand)op).value);
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
