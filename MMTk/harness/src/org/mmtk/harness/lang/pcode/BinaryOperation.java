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
import org.mmtk.harness.lang.ast.Operator;
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.runtime.StackFrame;

/**
 * An arithmetic/logical operation with 2 operands.
 */
public final class BinaryOperation extends BinaryOp {

  /** The operator */
  public final Operator op;

  /**
   * Constructs an operation that computes
   * <pre>
   *   resultTemp <- op1 op op2
   * </pre>
   * @param source
   * @param resultTemp
   * @param op1
   * @param op2
   * @param op
   */
  public BinaryOperation(AST source, Register resultTemp, Register op1, Register op2, Operator op) {
    super(source, op.toString(),resultTemp, op1, op2);
    this.op = op;
  }

  @Override
  public void exec(Env env) {
    StackFrame frame = env.top();
    setResult(frame, op.operate(frame.get(op1),frame.get(op2)));
  }

  @Override
  public String toString() {
    return String.format("[%s] %s <- %s %s %s", formatGcMap(),
        Register.nameOf(getResult()),
        Register.nameOf(op1), name, Register.nameOf(op2));
  }
}
