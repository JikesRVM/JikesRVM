/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2006
 */
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.VM;

/**
 * Represents a constant code operand, found for example, from an
 * OPT_TIBConstantOperand. NB we don't use an object constant operand
 * because: 1) code doesn't form part of the object literals 2) we
 * need to support replacement
 *
 * @see OPT_Operand
 * @author Ian Rogers
 */
public final class OPT_CodeConstantOperand extends OPT_ConstantOperand {

  /**
   * The non-null method for the code represent
   */
  public final VM_Method value;

  /**
   * Construct a new code constant operand
   *
   * @param v the method of this TIB
   */
  public OPT_CodeConstantOperand(VM_Method v) {
    if (VM.VerifyAssertions) VM._assert(v != null);
    value = v;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
    return new OPT_CodeConstantOperand(value);
  }

  /**
   * Return the {@link VM_TypeReference} of the value represented by the operand.
   * 
   * @return VM_TypeReference.JavaLangObjectArray
   */
  public VM_TypeReference getType() {
    return VM_TypeReference.CodeArray;
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
    return (op instanceof OPT_CodeConstantOperand) &&
      value == ((OPT_CodeConstantOperand)op).value;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return "code \""+ value + "\"";
  }
}
