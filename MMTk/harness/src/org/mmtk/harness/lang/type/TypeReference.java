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
package org.mmtk.harness.lang.type;

import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.parser.Token;
import org.mmtk.harness.lang.parser.TypeTable;
import org.mmtk.harness.lang.runtime.Value;

/**
 * An unresolved reference to a type.
 */
public class TypeReference implements UserType {

  private final TypeTable table;
  private final String name;

  /**
   * Create a type reference
   * @param table Table from which the type will eventually be resolved.
   * @param name The type name
   */
  public TypeReference(TypeTable table, String name) {
    this.table = table;
    this.name = name;
    // CHECKSTYLE:OFF
    assert name != "int" && name != "boolean" && name != "string" && name != "void" 
      && name != "weakref";
    // CHECKSTYLE:ON
  }

  public UserType resolve() {
    return (UserType)table.get(getName());
  }

  @Override
  public Value initialValue() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void defineField(String fieldName, Type type) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Field getField(String fieldName) {
    return resolve().getField(fieldName);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean isCompatibleWith(Type rhs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }

  @Override
  public int getColumn() {
    return resolve().getColumn();
  }

  @Override
  public int getLine() {
    return resolve().getLine();
  }

  @Override
  public String sourceLocation(String prefix) {
    throw new UnsupportedOperationException();
  }

  /**
   * We don't create references to non-object types
   */
  @Override
  public boolean isObject() {
    return true;
  }

  @Override
  public int dataFieldCount() {
    return resolve().dataFieldCount();
  }

  @Override
  public int referenceFieldCount() {
    return resolve().referenceFieldCount();
  }

  @Override
  public Token getToken() {
    return resolve().getToken();
  }

}
