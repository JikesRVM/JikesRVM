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
package org.mmtk.harness.lang.pcode;

import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.runtime.StackFrame;
import org.mmtk.harness.lang.runtime.Value;

public final class ReturnOp extends NullaryOp {

  public final int operand;

  public ReturnOp(Register operand) {
    super("return");
    this.operand = operand.getIndex();
  }

  public ReturnOp() {
    super("return");
    this.operand = -1;
  }

  @Override
  public void exec(Env env) {
  }

  public boolean hasOperand() {
    return operand != -1;
  }

  public Value getOperand(StackFrame frame) {
    return frame.get(operand);
  }

  public boolean affectsControlFlow() {
    return true;
  }

  @Override
  public boolean isReturn() {
    return true;
  }

  public String toString() {
    return "return " + (operand != -1 ? "t"+operand : "");
  }
}
