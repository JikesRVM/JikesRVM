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
import org.mmtk.harness.lang.parser.Symbol;
import org.mmtk.harness.lang.parser.Token;
import org.mmtk.harness.lang.type.Type;

/**
 * An assignment of an expression to a stack variable.
 */
public class Assignment extends AbstractAST implements Statement {
  /** The expression to assign */
  private final Expression expr;
  /** The original symbol */
  private final Symbol symbol;

  /**
   * Create a new assignment of the given expression to the specified variable.
   */
  public Assignment(Token t, Symbol variable, Expression expr) {
    super(t);
    this.symbol = variable;
    this.expr = expr;
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }

  public int getSlot() { return symbol.getLocation(); }
  public Expression getRhs() { return expr; }
  public Symbol getSymbol() { return symbol; }
  public Type getType() { return symbol.getType(); }
}
