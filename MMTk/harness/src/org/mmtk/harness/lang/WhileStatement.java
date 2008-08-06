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

/**
 * While loop
 */
public class WhileStatement implements Statement {

  private Expression cond;
  private Statement body;

  /**
   * Constructor
   * @param cond
   * @param body
   */
  public WhileStatement(Expression cond, Statement body) {
    this.cond = cond;
    this.body = body;
  }

  public void exec(Env env) throws ReturnException {
    while (cond.eval(env).getBoolValue()) {
      body.exec(env);
      env.gcSafePoint();
    }
  }
}
