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
import org.mmtk.harness.lang.type.Field;
import org.mmtk.harness.lang.type.UserType;

/**
 * Assign a field of an object.
 */
public class StoreNamedField extends AbstractAST implements Statement {
  /** Value to assign to the field */
  private final Expression value;
  /** Object variable */
  private final Symbol symbol;
  /** Field */
  private final String field;

  /**
   * Assign the result of the given expression to the given field
   * of the object in stack frame slot 'slot'.
   */
  public StoreNamedField(Token t, Symbol symbol, String field, Expression value) {
    super(t);
    this.symbol = symbol;
    this.field = field;
    this.value = value;
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }

  public Symbol getObjectSymbol() { return symbol; }
  public String getFieldName() { return field; }
  public Expression getRhs() { return value; }
  public int getSlot() { return symbol.getLocation(); }

  /** Not safe unless the checker has checked this AST node */
  public Field getField() { return ((UserType)(symbol.getType())).getField(field); }
}
