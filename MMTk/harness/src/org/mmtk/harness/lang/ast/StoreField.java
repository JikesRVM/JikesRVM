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
import org.mmtk.harness.lang.type.Type;

/**
 * Assign a field of an object.
 */
public class StoreField extends AbstractAST implements Statement {
  /** Field within the object (int) */
  private final Expression index;
  /** Type of the field being stored */
  private final Type type;
  /** Value to assign to the field */
  private final Expression value;
  private final Symbol symbol;

  /**
   * Assign the result of the given expression to the given field
   * of the object in stack frame slot 'slot'.
   */
  public StoreField(Token t, Symbol symbol, Type type, Expression index, Expression value) {
    super(t);
    this.symbol = symbol;
    this.index = index;
    this.type = type;
    this.value = value;
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }

  public Symbol getObjectSymbol() { return symbol; }
  public Expression getIndex() { return index; }
  public Type getFieldType() { return type; }
  public Expression getRhs() { return value; }
  public int getSlot() { return symbol.getLocation(); }
}
