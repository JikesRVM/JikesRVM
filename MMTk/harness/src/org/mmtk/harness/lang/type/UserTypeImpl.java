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
package org.mmtk.harness.lang.type;

import java.util.HashMap;
import java.util.Map;

import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.ast.AST;
import org.mmtk.harness.lang.ast.AbstractAST;
import org.mmtk.harness.lang.parser.Token;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.Value;

/**
 * A user-defined object type
 */
public class UserTypeImpl extends AbstractType implements UserType {

  /** The fields of the type */
  private final Map<String,Field> fields = new HashMap<String,Field>();

  private final AST ast;

  private int referenceFields = 0;
  private int dataFields = 0;

  /**
   * Create a user-defined type
   * @param tok Source-code token
   * @param name Name of the type
   */
  public UserTypeImpl(Token tok, String name) {
    super(name);
    this.ast = new AbstractAST(tok);
  }

  /**
   * Define a new field
   * @param fieldName Name of the field
   * @param fieldType Type of the field
   */
  public void defineField(String fieldName, Type fieldType) {
    if (fields.containsKey(fieldName)) {
      throw new RuntimeException("Type "+getName()+" already contains a field called "+fieldName);
    }
    int offset = fieldType.isObject() ? referenceFields++ : dataFields++;
    Field field = new Field(fieldName,fieldType,offset);
    fields.put(fieldName, field);
  }

  @Override
  public Value initialValue() {
    return ObjectValue.NULL;
  }

  @Override
  public Field getField(String name) {
    return fields.get(name);
  }

  /**
   * @return number of reference fields
   */
  public int referenceFieldCount() {
    return referenceFields;
  }

  /**
   * @return number of data fields
   */
  public int dataFieldCount() {
    return dataFields;
  }

  @Override
  public boolean isCompatibleWith(Type rhs) {
    if (rhs == this || rhs == Type.NULL) {
      return true;
    }
    return false;
  }

  @Override
  public boolean isObject() {
    return true;
  }

  /*
   * Delegate AST-nature to the 'ast' object
   */

  @Override public Object accept(Visitor v) { return v.visit(this); }
  @Override public int getColumn() { return ast.getColumn(); }
  @Override public int getLine() { return ast.getLine(); }
  @Override public String sourceLocation(String prefix) { return ast.sourceLocation(prefix); }

  @Override public Token getToken() { return ast.getToken();  }
}
