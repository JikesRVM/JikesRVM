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

import java.util.List;

import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.parser.Token;

/**
 * Asserts a condition and then prints output and exits if the assertion fails.
 */
public class Assert extends AbstractAST implements Statement {
  /** The expression to check */
  private final Expression cond;
  /** The expression to print */
  private final List<Expression> exprs;

  /**
   * Constructor
   * @param slot Stack frame slot of the variable
   */
  public Assert(Token t, Expression cond, List<Expression> exprs) {
    super(t);
    this.cond = cond;
    this.exprs = exprs;
  }

  public Object accept(Visitor v) {
    return v.visit(this);
  }

  public Expression getPredicate() { return cond; }
  public List<Expression> getOutputs() { return exprs; }
}
