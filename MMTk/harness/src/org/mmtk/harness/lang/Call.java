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
 * A call to a method.
 */
public class Call implements Statement {
  /** Method table */
  private final MethodTable methods;
  /** Method name */
  private final String methodName;
  /** Parameter expressions */
  private final List<Expression> params;

  /**
   * Call a method.
   */
  public Call(MethodTable methods, String methodName, List<Expression> params) {
    this.methods = methods;
    this.methodName = methodName;
    this.params = params;
  }

  /**
   * Run this statement.
   */
  public void exec(Env env) {
    Method method = methods.get(methodName);

    Value[] values = new Value[params.size()];
    for(int i=0; i < params.size(); i++) {
      values[i] = params.get(i).eval(env);
    }

    method.exec(env, values);
  }
}
