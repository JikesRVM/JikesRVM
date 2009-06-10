/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ir.operand;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.BootstrapClassLoader;
import org.jikesrvm.classloader.TypeReference;
import org.vmmagic.unboxed.Offset;
import org.jikesrvm.mm.mminterface.MemoryManager;

/**
 * Represents a constant object operand (for example, from an
 * initialized static final).
 *
 * @see Operand
 */
public class ObjectConstantOperand extends ConstantOperand {

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
  private final boolean movable;

  /**
   * Construct a new object constant operand
   *
   * @param v the object constant
   * @param i JTOC offset of the object constant
   */
  public ObjectConstantOperand(Object v, Offset i) {
    if (VM.VerifyAssertions) VM._assert(v != null);
    value = v;
    offset = i;
    // prior to writing the boot image we don't know where objects will reside,
    // so we must treat them as movable when writing the boot image
    movable = !VM.runningVM || !MemoryManager.willNeverMove(v);
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   *
   * @return a copy of <code>this</code>
   */
  public Operand copy() {
    return new ObjectConstantOperand(value, offset);
  }

  /**
   * Return the {@link TypeReference} of the value represented by the operand.
   *
   * @return type reference for type of object
   */
  public TypeReference getType() {
    if (VM.runningVM) {
      return java.lang.JikesRVMSupport.getTypeForClass(value.getClass()).getTypeRef();
    } else {
      Class<?> rc = value.getClass();
      String className = rc.getName();
      Atom classAtom = Atom.findOrCreateAsciiAtom(className.replace('.', '/'));
      if (className.startsWith("[")) {
        // an array
        return TypeReference.findOrCreate(BootstrapClassLoader.getBootstrapClassLoader(), classAtom);
      } else {
        // a class
        Atom classDescriptor = classAtom.descriptorFromClassName();
        return TypeReference.findOrCreate(BootstrapClassLoader.getBootstrapClassLoader(), classDescriptor);
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
   * Is the operand a movable {@link ObjectConstantOperand}?
   *
   * @return movable
   */
  public boolean isMovableObjectConstant() {
    return movable;
  }


  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code>
   *           if they are not.
   */
  public boolean similar(Operand op) {
    return (op instanceof ObjectConstantOperand) && value.equals(((ObjectConstantOperand) op).value);
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
