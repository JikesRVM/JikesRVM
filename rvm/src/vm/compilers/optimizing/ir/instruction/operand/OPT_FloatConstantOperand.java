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
   * Offset in JTOC where this float constant lives (-1 for constants
   * generated from constant folding).
   */
  int offset;

  /**
   * Constructs a new float constant operand with the specified value.
   *
   * @param v value
   */
  OPT_FloatConstantOperand(float v) {
    value = v;
    if (v == 0.f) {
       offset = VM_Entrypoints.zeroFloat.getOffset() >> 2;
    } else if (v == 1.f) {
       offset = VM_Entrypoints.oneFloat.getOffset() >> 2;
    } else if (v == 2.f) {
       offset = VM_Entrypoints.twoFloat.getOffset() >> 2;
    }
  }

  /**
   * Constructs a new float constant operand with the specified value and JTOC offset.
   *
   * @param v value
   * @param i offset in the jtoc
   */
  OPT_FloatConstantOperand(float v, int i) {
    value  = v;
    offset = i;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  OPT_Operand copy() {
    return new OPT_FloatConstantOperand(value,offset);
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
