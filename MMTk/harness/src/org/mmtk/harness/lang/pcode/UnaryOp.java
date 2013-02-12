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
package org.mmtk.harness.lang.pcode;

import org.mmtk.harness.lang.ast.AST;
import org.mmtk.harness.lang.compiler.Register;

public abstract class UnaryOp extends PseudoOp {

  protected final int operand;

  public UnaryOp(AST source, String name, Register resultTemp, Register operand) {
    super(source, 1, name, resultTemp);
    this.operand = operand.getIndex();
  }

  public UnaryOp(AST source, String name, Register operand) {
    super(source, 1, name);
    this.operand = operand.getIndex();
  }

  @Override
  public String toString() {
    return String.format("%s(%s)", super.toString(), Register.nameOf(operand));
  }
}
