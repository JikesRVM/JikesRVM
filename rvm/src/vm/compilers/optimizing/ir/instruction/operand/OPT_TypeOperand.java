/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;

/**
 * A OPT_TypeOperand represents a data type. Used in checkcast, instanceof,
 * new, etc.
 *
 * @see OPT_Operand
 * @see VM_Type
 * 
 * @author John Whaley
 */
public final class OPT_TypeOperand extends OPT_Operand {

  /**
   * The data type.
   */
  public VM_Type type;

  /**
   * Create a new type operand with the specified data type.
   */
  public OPT_TypeOperand(VM_Type typ) {
    type = typ;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
    return new OPT_TypeOperand(type);
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
    return (op instanceof OPT_TypeOperand) && (type == ((OPT_TypeOperand)op).type);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return type.getName().toString();
  }
}
