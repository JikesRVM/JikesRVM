/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

/**
 * A OPT_TypeOperand represents a type. 
 * Used in checkcast, instanceof, new, etc.
 * It will contain either a VM_Type (if the type can be resolved 
 * at compile time) or a VM_TypeReference (if the type cannot be resolved
 * at compile time).
 *
 * @see OPT_Operand
 * @see VM_Type
 * @see VM_TypeReference
 * 
 * @author John Whaley
 */
public final class OPT_TypeOperand extends OPT_Operand {

  /**
   * A type
   */
  private final VM_Type type;

  /**
   * The data type.
   */
  private final VM_TypeReference typeRef;

  /**
   * Create a new type operand with the specified type.
   */
  public OPT_TypeOperand(VM_Type typ) {
    type = typ;
    typeRef = type.getTypeRef();
  }

  /**
   * Create a new type operand with the specified type reference
   */
  public OPT_TypeOperand(VM_TypeReference tr) {
    type = null;
    typeRef = tr;
  }

  private OPT_TypeOperand(VM_Type t, VM_TypeReference tr) {
    type = t;
    typeRef = tr;
  }

  /**
   * @return the VM_TypeReference for this type operand
   */
  public final VM_TypeReference getTypeRef() {
    return typeRef;
  }

  /**
   * @return the VM_Type for this type operand -- may be null
   */
  public final VM_Type getVMType() {
    return type;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
    return new OPT_TypeOperand(type, typeRef);
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
    if (op instanceof OPT_TypeOperand) {
      OPT_TypeOperand that = (OPT_TypeOperand)op;
      return type == that.type && typeRef == that.typeRef;
    } else {
      return false;
    }
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    if (type != null) {
      return type.toString();
    } else {
      return typeRef.getName().toString();
    }
  }
}
