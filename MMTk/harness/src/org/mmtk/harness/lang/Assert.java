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

/**
 * Asserts a condition and then prints output and exits if the assertion fails.
 */
public class Assert implements Statement {
  /** The expression to check */
  private final Expression cond;
  /** The expression to print */
  private final List<Expression> exprs;

  /**
   * Constructor
   * @param slot Stack frame slot of the variable
   */
  public Assert(Expression cond, List<Expression> exprs) {
    this.cond = cond;
    this.exprs = exprs;
  }

  /**
   * Execute the statement
   */
  public void exec(Env env) {
    Value condVal = cond.eval(env);
    env.gcSafePoint();

    if (!condVal.getBoolValue()) {
      StringBuilder output = new StringBuilder();
      for(Expression expr: exprs) {
        output.append(expr.eval(env));
        env.gcSafePoint();
      }
      System.err.println(output);
      System.exit(1);
    }
  }
}
