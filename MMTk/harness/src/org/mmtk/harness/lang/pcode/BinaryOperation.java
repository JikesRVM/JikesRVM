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
import org.mmtk.harness.lang.ast.Operator;
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.runtime.StackFrame;

public final class BinaryOperation extends BinaryOp {

  public final Operator op;

  public BinaryOperation(Register resultTemp, Register op1, Register op2, Operator op) {
    super(op.toString(),resultTemp, op1, op2);
    this.op = op;
  }

  public String toString() {
    return String.format("t%d <- t%d %s t%d", getResult(), op1, name, op2);
  }

  @Override
  public void exec(Env env) {
    StackFrame frame = env.top();
    setResult(frame, op.operate(frame.get(op1),frame.get(op2)));
  }

}