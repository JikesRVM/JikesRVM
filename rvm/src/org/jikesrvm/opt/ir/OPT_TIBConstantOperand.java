/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2006
 */
package org.jikesrvm.opt.ir;

import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.VM;

/**
 * Represents a constant TIB operand, found for example, from an
 * OPT_ObjectConstantOperand. NB we don't use an object constant
 * operand because: 1) TIBs don't form part of the object literals 2)
 * loads on the contents of a tib can be turned into constant moves,
 * whereas for arrays in general this isn't the case. We don't use
 * OPT_TypeOperand as the type of the operand is VM_Type, whereas a
 * TIBs type is Object[].
 *
 * @see OPT_Operand
 * @author Ian Rogers
 */
public final class OPT_TIBConstantOperand extends OPT_ConstantOperand {

  /**
   * The non-null type for this tib
   */
  public final VM_Type value;

  /**
   * Construct a new TIB constant operand
   *
   * @param v the type of this TIB
   */
  public OPT_TIBConstantOperand(VM_Type v) {
	 if (VM.VerifyAssertions) VM._assert(v != null);
    value = v;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
    return new OPT_TIBConstantOperand(value);
  }

  /**
   * Return the {@link VM_TypeReference} of the value represented by the operand.
   * 
   * @return VM_TypeReference.JavaLangObjectArray
   */
  public VM_TypeReference getType() {
	 return VM_TypeReference.JavaLangObjectArray;
  }

  /**
   * Does the operand represent a value of the reference data type?
   * 
   * @return <code>true</code>
   */
  public boolean isRef() {
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
	 return (op instanceof OPT_TIBConstantOperand) &&
		value == ((OPT_TIBConstantOperand)op).value;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
	 return "tib \""+ value + "\"";
  }
}
