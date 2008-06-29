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
 * A method is a set of variable declarations followed by a statement.
 */
public class Method implements Statement, Expression {
  /** The name of this block */
  private final String name;
  /** Number of parameters */
  private final int params;
  /** The variable declarations */
  private final List<Declaration> decls;
  /** The statement this block will execute */
  private final Statement body;

  /**
   * Create a new method.
   */
  public Method(String name, int params, List<Declaration> decls, Statement body) {
    this.name = name;
    this.params = params;
    this.decls = decls;
    this.body = body;
  }

  /**
   * Execute the statements in the method.
   */
  public void exec(Env env) throws ReturnException {
    exec(env, new Value[] {});
  }

  /**
   * Get the name of this method.
   */
  public String getName() {
    return name;
  }

  /**
   * Execute the statements in the method (with passed parameters).
   */
  public void exec(Env env, Value...values) {
    StackFrame frame = new StackFrame(decls);
    env.push(frame);
    setParams(env, values);
    env.gcSafePoint();
    /* values could be trashed from here down ... */
    try {
      body.exec(env);
    } catch (ReturnException e) {
      // Ignore return values
      env.popTemporary(e.getResult());
    }
    env.gcSafePoint();
    env.pop();
  }

  public Value eval(Env env) {
    return eval(env, new Value[] {});
  }

  /**
   * Execute the statements in the method (with passed parameters).
   */
  public Value eval(Env env, Value...values) {
    StackFrame frame = new StackFrame(decls);
    env.push(frame);
    setParams(env, values);
    env.gcSafePoint();
    /* values could be trashed from here down ... */
    try {
      body.exec(env);
    } catch (ReturnException e) {
      Value result = e.getResult();
      env.pop();
      env.gcSafePoint();
      env.popTemporary(result);
      return result;
    }
    env.check(false, "method didn't return a value");
    return null;
  }

  private void setParams(Env env, Value... values) {
    env.check(values.length == params, "Invalid number of parameters");
    for(int i=0; i<values.length; i++) {
      Type expected = env.top().getType(i);
      Type actual = values[i].type();
      env.check(expected == actual, "Method " + name + " parameter " + i + " expected " + expected + " found " + actual);
      env.top().set(i, values[i]);
    }
  }
}
