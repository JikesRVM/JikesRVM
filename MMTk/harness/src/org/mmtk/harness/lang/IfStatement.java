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
 * A conditional statement whereby one of two statements (either possibly an empty sequence)
 * is executed depending on the value of a condition expression.
 */
public class IfStatement implements Statement {
  /** The expression to be evaluated */
  private final Expression cond;
  /** The statement to execute if the condition is true */
  private final Statement ifTrue;
  /** The statement to execute if the condition is false */
  private final Statement ifFalse;

  /**
   * Create a new conditional statement.
   */
  public IfStatement(Expression cond, Statement ifTrue, Statement ifFalse) {
    this.cond = cond;
    this.ifTrue = ifTrue;
    this.ifFalse = ifFalse;
  }

  /**
   * Evaluate the condition, execute either the true or false statement and return.
   */
  public void exec(Env env) {
    Value condVal = cond.eval(env);

    env.check(condVal.type() == Type.BOOLEAN || condVal.type() == Type.OBJECT, "Condition must be object or boolean");
    env.gcSafePoint();
    if (condVal.getBoolValue()) {
      ifTrue.exec(env);
    } else {
      ifFalse.exec(env);
    }
    env.gcSafePoint();
  }
}
