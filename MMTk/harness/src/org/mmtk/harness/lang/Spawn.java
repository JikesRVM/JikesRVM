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
package org.mmtk.harness.lang;

import java.util.List;
import org.mmtk.harness.lang.parser.MethodTable;

/**
 * Create a new thread to call a method.
 */
public class Spawn implements Statement {
  /** Method table */
  private final MethodTable methods;
  /** Method name */
  private final String methodName;
  /** Parameter expressions */
  private final List<Expression> params;

  /**
   * Call a method.
   */
  public Spawn(MethodTable methods, String methodName, List<Expression> params) {
    this.methods = methods;
    this.methodName = methodName;
    this.params = params;
  }

  /**
   * Run this statement.
   */
  public void exec(Env env) {
    Value[] values = new Value[params.size()];
    for(int i=0; i < params.size(); i++) {
      values[i] = params.get(i).eval(env);
      // GC may occur between evaluating each parameter
      env.pushTemporary(values[i]);
      env.gcSafePoint();
    }

    // Call for the child
    env.beginChild();
    SpawnedEnv child = new SpawnedEnv(values);
    child.start();
  }

  private class SpawnedEnv extends Env {
    private final Value[] values;

    public SpawnedEnv(Value...values) {
      super(null);
      this.values = values;
    }

    public void run() {
      // No GC safe points before parameters are saved in the callee's stack
      for(int i=params.size() - 1; i >= 0; i--) {
        popTemporary(values[i]);
      }
      methods.get(methodName).exec(this, values);
      end();
    }
  }
}
