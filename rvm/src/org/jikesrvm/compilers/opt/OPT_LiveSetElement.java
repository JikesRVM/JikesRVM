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
package org.jikesrvm.compilers.opt;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;

/*
 *
 * A simple class that holds an element in a LiveSet.
 */
final class OPT_LiveSetElement {

  /**
   * The register operand, i.e., the data
   */
  private OPT_RegisterOperand regOp;

  /**
   * The next field
   */
  private OPT_LiveSetElement next;

  /**
   * Construct an {@link OPT_LiveSetElement}.
   *
   * @param register    An {@link OPT_RegisterOperand}
   */
  OPT_LiveSetElement(OPT_RegisterOperand register) {
    regOp = register;
  }

  /**
   * Returns the register operand associated with this element
   * @return the register operand associated with this element
   */
  public OPT_RegisterOperand getRegisterOperand() {
    return regOp;
  }

  /**
   * Change the register operand.  New operand must represent the same register
   * This is done to promote something of WordType to ReferenceType for the purposes of GC mapping.
   */
  public void setRegisterOperand(OPT_RegisterOperand newRegOp) {
    if (VM.VerifyAssertions) VM._assert(regOp.getRegister().number == newRegOp.getRegister().number);
    regOp = newRegOp;
  }

  /**
   * Returns the register associated with this element
   * @return the register associated with this element
   */
  public OPT_Register getRegister() {
    return regOp.getRegister();
  }

  /**
   * Returns the register type associated with this element
   * @return the register type associated with this element
   */
  public VM_TypeReference getRegisterType() {
    return regOp.getType();
  }

  /**
   * Returns the next element on this list
   * @return the next element on this list
   */
  public OPT_LiveSetElement getNext() {
    return next;
  }

  /**
   * Sets the next element field
   * @param newNext the next element field
   */
  public void setNext(OPT_LiveSetElement newNext) {
    next = newNext;
  }

  /**
   * Returns a string version of this element
   * @return a string version of this element
   */
  public String toString() {
    StringBuilder buf = new StringBuilder("");
    buf.append(regOp);
    return buf.toString();
  }
}



