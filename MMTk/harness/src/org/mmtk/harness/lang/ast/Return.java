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

import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.parser.Token;

public class Return extends AbstractAST implements Statement {

  private final Expression expr;

  public Return(Token t, Expression expr) {
    super(t);
    this.expr = expr;
  }

  public Return(Token t) {
    super(t);
    this.expr = null;
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }

  public Expression getRhs() { return expr; }
  public boolean hasReturnValue() { return expr != null; }
}
