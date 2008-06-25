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
package org.mmtk.harness.lang;

import java.util.List;

/**
 * Prints the value of an expression
 */
public class PrintStatement implements Statement {
  /** The expression to print */
  private final List<Expression> exprs;

  /**
   * Constructor
   * @param slot Stack frame slot of the variable
   */
  public PrintStatement(List<Expression> exprs) {
    this.exprs = exprs;
  }

  /**
   * Execute the statement
   */
  public void exec(Env env) {
    StringBuilder output = new StringBuilder();
    for(Expression expr: exprs) {
      output.append(expr.eval(env));
    }
    System.err.println(output);
  }
}
