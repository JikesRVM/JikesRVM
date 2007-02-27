/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.opt.ir;

import com.ibm.jikesrvm.classloader.VM_TypeReference;

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
   * Return the {@link VM_TypeReference} of the value represented by the operand.
   * 
   * @return VM_TypeReference.VALIDATION_TYPE
   */
  public final VM_TypeReference getType() {
	 return VM_TypeReference.VALIDATION_TYPE;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
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
  public boolean similar(OPT_Operand op) {
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
