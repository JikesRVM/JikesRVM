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
 * An expression returning the hash code of an object.
 */
public class Hash implements Expression {
  /** The object expression */
  private final Expression object;

  /**
   * Calculate the hash code of an object.
   */
  public Hash(Expression object) {
    this.object = object;
  }

  /**
   * Evaluate the expression and calculate the hash code.
   */
  public Value eval(Env env) {
    Value objectVal = object.eval(env);

    env.check(objectVal.type() == Type.OBJECT, "Attempt to calculate hash code of non-object expression");

    int hashCode = env.hash(objectVal.getObjectValue());
    env.gcSafePoint();

    return new IntValue(hashCode);
  }
}
