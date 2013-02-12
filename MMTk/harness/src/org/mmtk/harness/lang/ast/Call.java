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

import java.util.Collections;
import java.util.List;

import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.parser.Token;

/**
 * A call to a method.
 */
public class Call extends AbstractAST implements Statement, Expression {

  /** Method to call */
  private final Method method;

  /** Parameter expressions */
  private final List<Expression> params;

  private final boolean isExpression;

  /**
   * Call a method.
   */
  public Call(Token t, Method method, List<Expression> params, boolean isExpression) {
    super(t);
    this.method = method;
    this.params = params;
    this.isExpression = isExpression;
  }

  public Method getMethod() {
    return method.getMethod();
  }

  public List<Expression> getParams() {
    return Collections.unmodifiableList(params);
  }

  public boolean isExpression() {
    return isExpression;
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }
}
