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
package org.mmtk.harness.lang.ast;

import java.util.List;

import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.parser.Token;

/**
 * A conditional statement whereby one of two statements (either possibly an empty sequence)
 * is executed depending on the value of a condition expression.
 */
public class IfStatement extends AbstractAST implements Statement {
  /** The expressions to be evaluated */
  private final List<Expression> conds;
  /** The statement to execute if the corresponding condition is true */
  private final List<Statement> stmts;

  /**
   * Create a new conditional statement.
   */
  public IfStatement(Token t, List<Expression> conds, List<Statement> stmts) {
    super(t);
    this.conds = conds;
    this.stmts = stmts;
    assert conds.size() == stmts.size() || conds.size() +1 == stmts.size() :
      "mismatch between conditions and statements for a conditional";
  }

  public List<Expression> getConds() {
    return conds;
  }

  public List<Statement> getStmts() {
    return stmts;
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }
}
