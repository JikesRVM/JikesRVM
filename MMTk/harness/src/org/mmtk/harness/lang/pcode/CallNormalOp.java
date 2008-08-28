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
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.lang.compiler.CompiledMethod;
import org.mmtk.harness.lang.compiler.Register;

public final class CallNormalOp extends CallOp implements ResolvableOp {

  private CompiledMethod method;

  public CallNormalOp(Register resultTemp, CompiledMethod method, List<Register> params) {
    super(resultTemp, params);
    this.method = method;
  }

  public CallNormalOp(CompiledMethod method, List<Register> params) {
    super(params);
    this.method = method;
  }

  public CompiledMethod getMethod() {
    return method;
  }

  public String toString() {
    return super.toString().replace("call","call "+method.getName());
  }

  @Override
  public void resolve() {
    if (!method.isResolved()) {
      Trace.trace(Item.COMPILER,"Resolving call to method %s%n", method.getName());
      method = method.resolve();
    }
  }

  @Override
  public void exec(Env env) {
  }

  @Override
  public boolean isCall() {
    return true;
  }


}
