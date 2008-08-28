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
import org.mmtk.harness.lang.runtime.PcodeInterpreter;
import org.mmtk.harness.lang.runtime.Value;

public class SpawnOp extends EnnaryOp implements ResolvableOp {

  private CompiledMethod method;

  public SpawnOp(CompiledMethod method, List<Register> ops) {
    super("spawn", ops);
    this.method = method;
  }

  @Override
  public void exec(Env env) {
    // Call for the child
    env.beginChild();
    SpawnedEnv child = new SpawnedEnv(getOperandValues(env.top()));
    child.start();
  }

  private class SpawnedEnv extends Env {
    private final Value[] values;

    public SpawnedEnv(Value...values) {
      super(null);
      this.values = values;
    }

    public void run() {
      new PcodeInterpreter(this,method).exec(values);
      end();
    }
  }

  @Override
  public void resolve() {
    if (!method.isResolved()) {
      Trace.trace(Item.COMPILER,"Resolving call to method %s%n", method.getName());
      method = method.resolve();
    }
  }

}
