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
 * Abstract superclass of methods implemented in a variety of ways
 */
public abstract class Method implements Statement, Expression {

  /** The name of this block */
  protected final String name;
  /** Number of parameters */
  protected final int params;

  /**
   * Constructor.
   * @param name
   * @param params
   */
  protected Method(String name, int params) {
    this.name = name;
    this.params = params;
  }

  /**
   * Execute the statements in the method.
   */
  public void exec(Env env) throws ReturnException {
    exec(env, new Value[] {});
  }

  /**
   * {@link Expression#eval(Env)}
   * @param env
   * @return
   */
  public Value eval(Env env) {
    return eval(env, new Value[] {});
  }

  /**
   * Sub-class method to execute the method as a statement.
   * @param env
   * @param values
   */
  public abstract void exec(Env env, Value...values);

  /**
   * Sub-class method to evaluate the method as an expression.
   * @param env
   * @param values
   */
  public abstract Value eval(Env env, Value...values);

  /**
   * Get the name of this method.
   */
  public String getName() {
    return name;
  }

}
