/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.classloader.VM_TypeReference;
import org.vmmagic.unboxed.Offset;

/**
 * Represents a constant string operand.
 *
 * @see OPT_Operand
 */
public final class OPT_StringConstantOperand extends OPT_ObjectConstantOperand {

  /**
   * Construct a new string constant operand
   *
   * @param v the string constant
   * @param i JTOC offset of the string constant
   */
  public OPT_StringConstantOperand(String v, Offset i) {
    super (v,i);
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
    return new OPT_StringConstantOperand((String)value, offset);
  }

  /**
   * Return the {@link VM_TypeReference} of the value represented by the operand.
   * 
   * @return VM_TypeReference.JavaLangString
   */
  public VM_TypeReference getType() {
    return VM_TypeReference.JavaLangString;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return "string \""+ value + "\"";
  }
}
