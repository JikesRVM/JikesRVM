/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Represents a constant long operand.
 *
 * @see OPT_Operand
 * @author John Whaley
 */
public final class OPT_LongConstantOperand extends OPT_ConstantOperand {

  /**
   * Value of this operand.
   */
  long value;

  /**
   * Index in JTOC where this long constant lives. (0 for constants
   * obtained from constant folding)
   */
  int index;

  /**
   * Constructs a new long constant operand with the specified value.
   *
   * @param v value
   */
  public OPT_LongConstantOperand(long v) {
    value = v;
  }

  /**
   * Constructs a new long constant operand with the specified value and JTOC index.
   *
   * @param v value
   * @param i index in the jtoc
   */
  OPT_LongConstantOperand(long v, int i) {
    value = v;
    index = i;
  }

  /**
   * Return the lower 32 bits (as an int) of value
   */
  int lower32() {
    return OPT_Bits.lower32(value);
  }

  /**
   * Return the upper 32 bits (as an int) of value
   */
  int upper32() {
    return OPT_Bits.upper32(value);
  }
  
  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  OPT_Operand copy() {
    return new OPT_LongConstantOperand(value, index);
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
    return (op instanceof OPT_LongConstantOperand) &&
           (value == ((OPT_LongConstantOperand)op).value);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return Long.toString(value)+"L";
  }

}
