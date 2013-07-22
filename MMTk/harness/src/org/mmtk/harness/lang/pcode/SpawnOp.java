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
import org.mmtk.harness.lang.runtime.PcodeInterpreter;
import org.mmtk.harness.lang.runtime.Value;
import org.mmtk.harness.scheduler.Schedulable;
import org.mmtk.harness.scheduler.Scheduler;
import org.vmmagic.unboxed.harness.Clock;

public class SpawnOp extends EnnaryOp implements ResolvableOp {

  private CompiledMethod method;

  public SpawnOp(AST source, CompiledMethod method, List<Register> ops) {
    super(source,"spawn", ops);
    this.method = method;
  }

  @Override
  public void exec(Env env) {
    Clock.stop();
    Scheduler.scheduleMutator(new SpawnedMethod(getOperandValues(env.top())));
    Clock.start();
  }

  private final class SpawnedMethod implements Schedulable {

    /** The method parameters */
    private final Value[] values;

    public SpawnedMethod(Value...values) {
      this.values = values;
    }

    @Override
    public void execute(Env env) {
      new PcodeInterpreter(env,method).exec(values);
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
