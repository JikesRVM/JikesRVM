/*
 * (C) Copyright Ian Rogers, The University of Manchester 2006
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.classloader.VM_TypeReference;
import org.vmmagic.unboxed.Offset;

/**
 * Represents a constant class operand.
 *
 * @see OPT_Operand
 * @author Ian Rogers
 */
public final class OPT_ClassConstantOperand extends OPT_ConstantOperand {

  /**
   * The type reference for the class. We use the type reference so
   * that we don't need to resolve the class and so we don't face boot
   * image problems where the java.lang.Class is confused.
   */
  public VM_TypeReference value;

  /**
   * Offset in JTOC where this class constant lives.
   */
  public Offset offset;

  /**
   * Construct a new class constant operand
   *
   * @param v the class constant
   * @param i JTOC offset of the class constant
   */
  public OPT_ClassConstantOperand(VM_TypeReference v, Offset i) {
    value = v;
    offset = i;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
    return new OPT_ClassConstantOperand(value, offset);
  }

  /**
   * Return the {@link VM_TypeReference} of the value represented by the operand.
   * 
   * @return VM_TypeReference.JavaLangClass
   */
  public final VM_TypeReference getType() {
	 return VM_TypeReference.JavaLangClass;
  }

  /**
   * Does the operand represent a value of the reference data type?
   * 
   * @return <code>true</code>
   */
  public final boolean isRef() {
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
	 return (op instanceof OPT_ClassConstantOperand) &&
		value.equals(((OPT_ClassConstantOperand)op).value);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
	 return "\""+ value + "\"";
  }
}
