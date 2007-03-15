/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2004
 */
package org.jikesrvm.opt;

import org.jikesrvm.*;
import org.jikesrvm.classloader.*;
import org.jikesrvm.opt.ir.*;

/*
 * @author Michael Hind
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
    return  regOp;
  }

  /**
   * Change the register operand.  New operand must represent the same register
   * This is done to promote something of WordType to ReferenceType for the purposes of GC mapping.
   */
  public void setRegisterOperand(OPT_RegisterOperand newRegOp) {
    if (VM.VerifyAssertions) VM._assert(regOp.register.number == newRegOp.register.number);
    regOp = newRegOp;
  }

  /**
   * Returns the register associated with this element
   * @return the register associated with this element
   */
  public OPT_Register getRegister() {
    return  regOp.register;
  }

  /**
   * Returns the register type associated with this element
   * @return the register type associated with this element
   */
  public VM_TypeReference getRegisterType() {
    return regOp.type;
  }

  /**
   * Returns the next element on this list
   * @return the next element on this list
   */
  public OPT_LiveSetElement getNext() {
    return  next;
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
    return  buf.toString();
  }
}



