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

/**
 * An expression returning the value of a field in an object.
 */
public class LoadNamedField extends AbstractAST implements Expression {
  /** Stack slot of object variable */
  private final int slot;
  /** Field within the object */
  private final String fieldName;
  private final Symbol symbol;

  /**
   * Load a field and store the loaded value into the stack.
   */
  public LoadNamedField(Token t, Symbol symbol, String fieldName) {
    super(t);
    this.symbol = symbol;
    this.slot = symbol.getLocation();
    this.fieldName = fieldName;
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }
  public Symbol getObjectSymbol() {    return symbol;  }
  public String getFieldName() { return fieldName; }
  public int getSlot() { return slot; }
}
