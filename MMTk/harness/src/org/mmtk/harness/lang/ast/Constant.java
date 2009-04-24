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
import org.mmtk.harness.lang.parser.Token;
import org.mmtk.harness.lang.runtime.Value;

public class Constant extends AbstractAST implements Expression {

  public final Value value;

  /** A constant with no program location (eg for default values */
  public Constant(Value value) {
    super(0,0);
    this.value = value;
    assert value != null;
  }

  public Constant(Token t, Value value) {
    super(t);
    this.value = value;
    assert value != null;
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }

}
