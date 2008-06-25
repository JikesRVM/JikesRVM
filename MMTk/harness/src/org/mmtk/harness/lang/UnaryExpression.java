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
 * Unary expressions.
 */
public class UnaryExpression implements Expression {
  /** The operator */
  private Operator op;
  /** The expression */
  private Expression expr;

  /**
   * Create a unary expression.
   * @param op The operation.
   * @param expr The single argument (unary, y'know)
   */
  public UnaryExpression(Operator op, Expression expr) {
    this.op = op;
    this.expr = expr;
  }

  /**
   * Evaluate the expression.
   */
  public Value eval(Env env) {
    Value rhsVal = expr.eval(env);


    switch (op) {
      case NOT:
        env.check(rhsVal.type() == Type.BOOLEAN, "Expected boolean for NOT unary operator");
        return new BoolValue(!rhsVal.getBoolValue());
      case MINUS:
        env.check(rhsVal.type() == Type.INT, "Expected integer for MINUS unary operator");
        return new IntValue(-rhsVal.getIntValue());
    }

    env.notReached();
    return null;
  }


}
