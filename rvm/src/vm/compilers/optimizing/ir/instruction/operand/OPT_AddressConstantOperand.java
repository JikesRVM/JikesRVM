/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.opt.OPT_Bits;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_SizeConstants;

/**
 * Represents an address constant operand.
 *
 * @see OPT_Operand
 * @author John Whaley
 */
public final class OPT_AddressConstantOperand extends OPT_ConstantOperand {

  /**
   * Value of this operand.
   */
  public VM_Address value;

  /**
   * Constructs a new address constant operand with the specified value.
   *
   * @param v value
   */
  public OPT_AddressConstantOperand(VM_Address v) {
    value = v;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
    return new OPT_AddressConstantOperand(value);
  }

  /*
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code> 
   *           if they are not.
   */
  public boolean similar(OPT_Operand op) {
    return equals(op);
  }

  public boolean equals(Object o) {
    return (o instanceof OPT_AddressConstantOperand) &&
           (value.EQ(((OPT_AddressConstantOperand)o).value));
  }

  public int hashCode() {
    //-#if RVM_FOR_64_ADDR
    return (int)(value.toLong() >>> VM_SizeConstants.LOG_BYTES_IN_ADDRESS);
    //-#elif RVM_FOR_32_ADDR
    return value.toInt() >>> VM_SizeConstants.LOG_BYTES_IN_ADDRESS;  
    //-#endif
  }
  
  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    //-#if RVM_FOR_64_ADDR
    return "Addr 0x" + Long.toHexString(value.toLong());
    //-#elif RVM_FOR_32_ADDR
    return "Addr 0x" + Integer.toHexString(value.toInt());
    //-#endif
  }
}
