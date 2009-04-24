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
package org.mmtk.harness.lang.pcode;

import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.ast.AST;

public class ExpectOp extends NullaryOp {
  private final Class<?> expectedThrowable;

  public ExpectOp(AST source, Class<?> expectedThrowable) {
    super(source,"expect");
    this.expectedThrowable = expectedThrowable;
  }

  @Override
  public void exec(Env env) {
    env.setExpectedThrowable(expectedThrowable);
  }

  @Override
  public String toString() {
    return super.toString() + expectedThrowable.getCanonicalName();
  }
}
