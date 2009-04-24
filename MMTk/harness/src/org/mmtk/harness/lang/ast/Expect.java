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

/**
 * An expectation for an exception (e.g. OutOfMemory).
 */
public class Expect extends AbstractAST implements Statement {
  private final Class<?> expectedThrowable;

  /**
   * Constructor
   */
  public Expect(Token t, String name) {
    super(t);
    try {
      expectedThrowable = Class.forName("org.mmtk.harness.Mutator$" + name);
    } catch (ClassNotFoundException cnfe) {
      throw new RuntimeException(cnfe);
    }
  }

  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }

  public Class<?> getExpected() {
    return expectedThrowable;
  }
}
