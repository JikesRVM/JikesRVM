/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

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
  OPT_ClassLoaderProxy.StringWrapper value;


  /**
   * Construct a new string constant operand
   *
   * @param val the string
   */
  OPT_StringConstantOperand(OPT_ClassLoaderProxy.StringWrapper val) {
    value = val;
  }


  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  OPT_Operand copy() {
    return new OPT_StringConstantOperand(value);
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
    return (op instanceof OPT_StringConstantOperand) &&
           (value.equals(((OPT_StringConstantOperand)op).value));
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return value.toString();
  }

}
