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

import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.ast.AST;
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.runtime.StackFrame;
import org.mmtk.harness.lang.runtime.Value;

import static org.mmtk.harness.lang.runtime.StackFrame.NO_SUCH_SLOT;

/**
 * Return from a method, with or without a return value.
 */
public final class ReturnOp extends NullaryOp {

  /** The operand slot */
  public final int operand;

  /** A return op with a return value */
  public ReturnOp(AST source, Register operand) {
    super(source,"return");
    this.operand = operand.getIndex();
  }

  /** A return op with no return value, ie from a void method */
  public ReturnOp(AST source) {
    super(source,"return");
    this.operand = NO_SUCH_SLOT;
  }

  @Override
  public void exec(Env env) {
  }

  /** Does the return instruction return a value ? */
  public boolean hasOperand() {
    return operand != NO_SUCH_SLOT;
  }

  /** Get the return value
   * @param frame The current stack frame
   * @return The value of the operand
   */
  public Value getOperand(StackFrame frame) {
    assert hasOperand();
    return frame.get(operand);
  }

  /** Is this a branch-like instruction */
  @Override
  public boolean affectsControlFlow() {
    return true;
  }

  /** Is this a return instruction */
  @Override
  public boolean isReturn() {
    return true;
  }

  /** Format this instruction for printing */
  public String toString() {
    return "return " + (operand != NO_SUCH_SLOT ? "t"+operand : "");
  }
}
