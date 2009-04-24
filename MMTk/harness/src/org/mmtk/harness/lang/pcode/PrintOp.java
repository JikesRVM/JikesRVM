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

import java.util.List;

import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.ast.AST;
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.runtime.Value;

public final class PrintOp extends EnnaryOp {

  public PrintOp(AST source, List<Register> ops) {
    this(source,ops.toArray(new Register[0]));
  }

  public PrintOp(AST source, Register... ops) {
    super(source,"print", ops);
  }

  @Override
  public void exec(Env env) {
    StringBuffer buf = new StringBuffer();
    Value[] operandValues = getOperandValues(env.top());
    for (Value val : operandValues) {
      buf.append(val.toString());
    }
    System.err.println(buf.toString());
  }

}
