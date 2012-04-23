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
package org.jikesrvm.compilers.opt.liveness;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

/*
 *
 * A simple class that holds an element in a LiveSet.
 */
final class LiveSetElement {

  /**
   * The register operand, i.e., the data
   */
  private RegisterOperand regOp;

  /**
   * The next field
   */
  private LiveSetElement next;

  /**
   * Construct an {@link LiveSetElement}.
   *
   * @param register    An {@link RegisterOperand}
   */
  LiveSetElement(RegisterOperand register) {
    regOp = register;
  }

  /**
   * Returns the register operand associated with this element
   * @return the register operand associated with this element
   */
  public RegisterOperand getRegisterOperand() {
    return regOp;
  }

  /**
   * Change the register operand.  New operand must represent the same register
   * This is done to promote something of WordType to ReferenceType for the purposes of GC mapping.
   */
  public void setRegisterOperand(RegisterOperand newRegOp) {
    if (VM.VerifyAssertions) VM._assert(regOp.getRegister().number == newRegOp.getRegister().number);
    regOp = newRegOp;
  }

  /**
   * Returns the register associated with this element
   * @return the register associated with this element
   */
  public Register getRegister() {
    return regOp.getRegister();
  }

  /**
   * Returns the register type associated with this element
   * @return the register type associated with this element
   */
  public TypeReference getRegisterType() {
    return regOp.getType();
  }

  /**
   * Returns the next element on this list
   * @return the next element on this list
   */
  public LiveSetElement getNext() {
    return next;
  }

  /**
   * Sets the next element field
   * @param newNext the next element field
   */
  public void setNext(LiveSetElement newNext) {
    next = newNext;
  }

  /**
   * Returns a string version of this element
   * @return a string version of this element
   */
  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder("");
    buf.append(regOp);
    return buf.toString();
  }
}
