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
import org.mmtk.harness.lang.ast.IntrinsicMethod;
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.runtime.StackFrame;
import org.mmtk.harness.lang.runtime.Value;

/**
 * Call an intrinsic method
 */
public final class CallIntrinsicOp extends CallOp {

  /**
   * The method to call - reuse the AST element
   */
  public final IntrinsicMethod method;

  /** An intrinsic call with a return value */
  public CallIntrinsicOp(AST source, Register resultTemp, IntrinsicMethod method, List<Register> params) {
    super(source,resultTemp, params);
    this.method = method;
  }

  /** An intrinsic call without a return value */
  public CallIntrinsicOp(AST source, IntrinsicMethod method, List<Register> params) {
    super(source,params);
    this.method = method;
  }

  @Override
  public void exec(Env env) {
    StackFrame frame = env.top();
    Value[] actuals = getOperandValues(frame);
    if (hasResult) {
      setResult(frame,method.eval(env,actuals));
    } else {
      method.exec(env, actuals);
    }
  }

  @Override
  public String toString() {
    return super.toString().replace("call","call "+method.getName());
  }
}
