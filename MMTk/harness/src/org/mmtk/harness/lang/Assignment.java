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

/**
 * An assignment of an expression to a stack variable.
 */
public class Assignment implements Statement {
  /** The slot to assign to in the stack frame */
  private int slot;
  /** The expression to assign */
  private Expression expr;

  /**
   * Create a new assignment of the given expression to the specified variable.
   */
  public Assignment(int slot, Expression expr) {
    this.slot = slot;
    this.expr = expr;
  }

  /**
   * Perform the assignment.
   */
  public void exec(Env env) {
    env.top().set(slot, expr.eval(env));
  }
}
