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
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.lang.ast.AST;
import org.mmtk.harness.lang.compiler.CompiledMethod;
import org.mmtk.harness.lang.compiler.Register;

/**
 * A pseudo-op that calls a method written in the script language.
 */
public final class CallNormalOp extends CallOp implements ResolvableOp {

  private CompiledMethod method;

  /** A method call that returns a result */
  public CallNormalOp(AST source, Register resultTemp, CompiledMethod method, List<Register> params) {
    super(source,resultTemp, params);
    this.method = method;
  }

  /** A method call without a return value */
  public CallNormalOp(AST source, CompiledMethod method, List<Register> params) {
    super(source,params);
    this.method = method;
  }

  /** Accessor for the enclosed method */
  public CompiledMethod getMethod() {
    return method;
  }

  /**
   * Replace compiled-method-proxies with their resolved versions.
   */
  @Override
  public void resolve() {
    if (!method.isResolved()) {
      Trace.trace(Item.COMPILER,"Resolving call to method %s%n", method.getName());
      method = method.resolve();
    }
  }

  /**
   * All the fun happens in @link{org.mmtk.harness.lang.runtime.PcodeInterpreter}
   */
  @Override
  public void exec(Env env) {
  }

  @Override
  public boolean isCall() {
    return true;
  }

  @Override
  public String toString() {
    return super.toString().replace("call","call "+method.getName());
  }
}

