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
package org.mmtk.harness.lang.ast;

import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.parser.Token;

/**
 * Unary expressions.
 */
public class UnaryExpression extends AbstractAST implements Expression {
  /** The operator */
  private Operator op;
  /** The expression */
  private Expression expr;

  /**
   * Create a unary expression.
   * @param op The operation.
   * @param expr The single argument (unary, y'know)
   */
  public UnaryExpression(Token t, Operator op, Expression expr) {
    super(t);
    this.op = op;
    this.expr = expr;
  }

  public Operator getOperator() { return op; }
  public Expression getOperand() { return expr; }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }
}
