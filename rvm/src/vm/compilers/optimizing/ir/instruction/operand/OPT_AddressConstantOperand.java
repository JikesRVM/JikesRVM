/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.opt.OPT_Bits;
import org.vmmagic.unboxed.*;
import com.ibm.JikesRVM.VM_SizeConstants;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.classloader.VM_TypeReference;

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
  public Address value;

  /**
   * Constructs a new address constant operand with the specified value.
   *
   * @param v value
   */
  public OPT_AddressConstantOperand(Address v) {
    value = v;
  }

  /**
   * Constructs a new address constant operand with the specified offset value.
   *
   * @param v value
   * TODO: make a separte OPT_OffsetConstantOperand 
	*/
  public OPT_AddressConstantOperand(Offset v) {
    value = v.toWord().toAddress();
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
    return new OPT_AddressConstantOperand(value);
  }

  /**
   * Return the {@link VM_TypeReference} of the value represented by the operand.
   * 
   * @return VM_TypeReference.Address
   */
  public final VM_TypeReference getType() {
	 return VM_TypeReference.Address;
  }

  /**
   * Does the operand represent a value of the address data type?
   * 
   * @return <code>true</code>
   */
  public final boolean isAddress() {
	 return true;
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
    return equals(op);
  }

  public boolean equals(Object o) {
    return (o instanceof OPT_AddressConstantOperand) &&
           (value.EQ(((OPT_AddressConstantOperand)o).value));
  }

  public int hashCode() {
    return value.toWord().rshl(VM_SizeConstants.LOG_BYTES_IN_ADDRESS).toInt();  
  }
  
  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return "Addr " + VM.addressAsHexString(value);
  }
}
