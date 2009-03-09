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

import java.util.List;

import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.parser.Token;
import org.mmtk.harness.lang.type.Type;

/**
 * Abstract superclass of methods implemented in a variety of ways
 */
public abstract class Method extends AbstractAST implements Statement, Expression, Comparable<Method> {

  /** The name of this block */
  protected final String name;

  /** Number of parameters */
  protected final int params;

  protected final Type returnType;

  /**
   * Constructor.
   * @param name
   * @param params
   */
  protected Method(Token t, String name, int params, Type returnType) {
    super(t);
    this.name = name;
    this.params = params;
    this.returnType = returnType;
  }

  protected Method(String name, int params, Type returnType) {
    super(0,0);
    this.name = name;
    this.params = params;
    this.returnType = returnType;
  }

  /**
   * Get the name of this method.
   */
  public String getName() {
    return name;
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }

  public int getParamCount() {
    return params;
  }

  public abstract List<Type> getParamTypes();

  public Type getReturnType() { return returnType; }

  public Method getMethod() {
    return this;
  }

  @Override
  public int compareTo(Method m) {
    return name.compareTo(m.getName());
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Method) {
      return compareTo((Method)o) == 0;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }
}
