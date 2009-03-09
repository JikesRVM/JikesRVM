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
import org.mmtk.harness.lang.parser.Symbol;
import org.mmtk.harness.lang.parser.Token;

/**
 * The value of a variable
 */
public class Variable extends AbstractAST implements Expression {
  private final Symbol symbol;

  /**
   * Constructor
   * @param slot
   */
  public Variable(Token t, Symbol symbol) {
    super(t);
    this.symbol = symbol;
  }

  /**
   * Return the slot of the variable this expression refers to.
   */
  public int getSlot() {
    return symbol.getLocation();
  }
  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }

  public Symbol getSymbol() { return symbol; }
}
