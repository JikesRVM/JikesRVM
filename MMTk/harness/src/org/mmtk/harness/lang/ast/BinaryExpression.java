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
 * A binary expression.
 */
public class BinaryExpression extends AbstractAST implements Expression {
  /** The left hand side of the expression. */
  private final Expression lhs;
  /** The right hand side of the expression */
  private final Expression rhs;
  /** The operator */
  private final Operator op;

  /**
   * Create a binary expression.
   */
  public BinaryExpression(Token t, Expression lhs, Operator op, Expression rhs) {
    super(t);
    this.lhs = lhs;
    this.op = op;
    this.rhs = rhs;
  }

  public Expression getLhs() { return lhs; }
  public Expression getRhs() { return rhs; }
  public Operator getOperator() { return op; }

  public Object accept(Visitor v) {
    return v.visit(this);
  }
}
