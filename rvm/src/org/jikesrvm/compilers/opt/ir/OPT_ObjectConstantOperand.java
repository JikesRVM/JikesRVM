/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_BootstrapClassLoader;
import org.jikesrvm.classloader.VM_TypeReference;
import org.vmmagic.unboxed.Offset;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;

/**
 * Represents a constant object operand (for example, from an
 * initialized static final).
 *
 * @see OPT_Operand
 */
public class OPT_ObjectConstantOperand extends OPT_ConstantOperand {

  /**
   * The non-null object value
   */
  public final Object value;

  /**
   * Offset in JTOC where this object constant lives.
   */
  public final Offset offset;

  /**
   * Can this object be moved in memory?
   */
  public final boolean moveable;

  /**
   * Construct a new object constant operand
   *
   * @param v the object constant
   * @param i JTOC offset of the object constant
   */
  public OPT_ObjectConstantOperand(Object v, Offset i) {
    if (VM.VerifyAssertions) VM._assert(v != null);
    value = v;
    offset = i;
    // prior to writing the boot image we don't know where objects will reside,
    // so we must treat them as moveable when writing the boot image
    moveable = !VM.runningVM || !MM_Interface.willNeverMove(v);
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   *
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
    return new OPT_ObjectConstantOperand(value, offset);
  }

  /**
   * Return the {@link VM_TypeReference} of the value represented by the operand.
   *
   * @return type reference for type of object
   */
  public VM_TypeReference getType() {
    if (VM.runningVM) {
      return java.lang.JikesRVMSupport.getTypeForClass(value.getClass()).getTypeRef();
    } else {
      Class<?> rc = value.getClass();
      String className = rc.getName();
      VM_Atom classAtom = VM_Atom.findOrCreateAsciiAtom(className.replace('.', '/'));
      if (className.startsWith("[")) {
        // an array
        return VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(), classAtom);
      } else {
        // a class
        VM_Atom classDescriptor = classAtom.descriptorFromClassName();
        return VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(), classDescriptor);
      }
    }
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
   * Is the operand a moveable {@link OPT_ObjectConstantOperand}?
   *
   * @return moveable
   */
  public boolean isMoveableObjectConstant() {
    return moveable;
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
    return (op instanceof OPT_ObjectConstantOperand) && value.equals(((OPT_ObjectConstantOperand) op).value);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return "object \"" + value + "\"";
  }
}
