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
 * While loop
 */
public class WhileStatement extends AbstractAST implements Statement {

  private final Expression cond;
  private final Statement body;

  /**
   * Constructor
   * @param cond
   * @param body
   */
  public WhileStatement(Token t, Expression cond, Statement body) {
    super(t);
    this.cond = cond;
    this.body = body;
  }

  public Expression getCond() { return cond; }
  public Statement getBody() { return body; }
  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }
}
