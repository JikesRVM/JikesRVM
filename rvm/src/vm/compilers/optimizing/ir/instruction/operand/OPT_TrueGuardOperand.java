/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * This operand represents a "true" guard.
 * Eg non-nullness of the result of an allocation or
 * boundcheck eliminate via analysis of the loop induction variables.
 * 
 * @see OPT_Operand
 * @author Dave Grove
 */
public final class OPT_TrueGuardOperand extends OPT_ConstantOperand {

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  OPT_Operand copy() {
    return new OPT_TrueGuardOperand();
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
    return op instanceof OPT_TrueGuardOperand;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return "<TRUEGUARD>";
  }
}
