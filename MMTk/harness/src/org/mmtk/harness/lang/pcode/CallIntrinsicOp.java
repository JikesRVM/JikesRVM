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

import java.util.List;

import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.ast.IntrinsicMethod;
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.runtime.StackFrame;
import org.mmtk.harness.lang.runtime.Value;

public final class CallIntrinsicOp extends CallOp {

  public final IntrinsicMethod method;

  public CallIntrinsicOp(Register resultTemp, IntrinsicMethod method, List<Register> params) {
    super(resultTemp, params);
    this.method = method;
  }

  public CallIntrinsicOp(IntrinsicMethod method, List<Register> params) {
    super(params);
    this.method = method;
  }

  public String toString() {
    return super.toString().replace("call","call "+method.getName());
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


}
