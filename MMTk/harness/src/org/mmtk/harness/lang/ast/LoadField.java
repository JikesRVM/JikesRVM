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
 * An expression returning the value of a field in an object.
 */
public class LoadField extends AbstractAST implements Expression {
  /** Stack slot of object variable */
  private final int slot;
  /** Field within the object (int) */
  private final Expression index;
  /** Type of the field being read */
  private final Type fieldType;
  private final Symbol symbol;

  /**
   * Load a field and store the loaded value into the stack.
   */
  public LoadField(Token t, Symbol symbol, Type fieldType, Expression index) {
    super(t);
    this.symbol = symbol;
    this.slot = symbol.getLocation();
    this.index = index;
    this.fieldType = fieldType;
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }

  /* Getters */
  public Symbol getObjectSymbol() { return symbol;  }
  public Expression getIndex() { return index; }
  public Type getFieldType() { return fieldType; }
  public int getSlot() { return slot; }
}
