/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.opt.OPT_Bits;
import com.ibm.JikesRVM.classloader.VM_TypeReference;
import com.ibm.JikesRVM.VM_Scheduler;

/**
 * Represents a constant int operand.
 *
 * @see OPT_Operand
 * @author John Whaley
 */
public final class OPT_IntConstantOperand extends OPT_ConstantOperand {

  /**
   * Value of this operand.
   */
  public int value;

  /**
   * Constructs a new int constant operand with the specified value.
   * Type will be determined by value.
   *
   * @param v value
   */
  public OPT_IntConstantOperand(int v) {
    value = v;
  }

  public VM_TypeReference getSpeculativeType() {
    if ((value == 0) || (value == 1))
      return VM_TypeReference.Boolean;
    else if (-128 <= value && value <= 127)
      return VM_TypeReference.Byte;
    else if (-32768 <= value && value <= 32767)
      return VM_TypeReference.Short;
    else
      return VM_TypeReference.Int;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
    return new OPT_IntConstantOperand(value);
  }

  /**
   * Return the lower 8 bits (as an int) of value
   */
  public int lower8() {
    return OPT_Bits.lower8(value);
  }

  /**
   * Return the lower 16 bits (as an int) of value
   */
  public int lower16() {
    return OPT_Bits.lower16(value);
  }

  /**
   * Return the upper 16 bits (as an int) of value
   */
  public int upper16() {
    return OPT_Bits.upper16(value);
  }
  
  /**
   * Return the upper 24 bits (as an int) of value
   */
  public int upper24() {
    return OPT_Bits.upper24(value);
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
    return (op instanceof OPT_IntConstantOperand) &&
           (value == ((OPT_IntConstantOperand)op).value);
  }

  public boolean equals(Object o) {
    return (o instanceof OPT_IntConstantOperand) &&
           (value == ((OPT_IntConstantOperand)o).value);
  }

  public int hashCode() {
    return value;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    if (value > 0xffff || value < -0xffff) {
      return "0x"+Integer.toHexString(value);
    } else {
      return Integer.toString(value);
    }
  }
}
