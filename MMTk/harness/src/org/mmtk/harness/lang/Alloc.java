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

import org.vmmagic.unboxed.ObjectReference;

public class Alloc implements Expression {
  /** Number of reference fields */
  private final Expression refCount;
  /** Number of data fields */
  private final Expression dataCount;
  /** Double align the object? */
  private final Expression doubleAlign;

  /**
   * Allocate an object.
   */
  public Alloc(Expression refCount, Expression dataCount, Expression doubleAlign) {
    this.refCount = refCount;
    this.dataCount = dataCount;
    this.doubleAlign = doubleAlign;
  }

  /**
   * Perform the allocation by calling MMTk.
   */
  public Value eval(Env env) {
    Value refCountVal = refCount.eval(env);
    env.gcSafePoint();

    Value dataCountVal = dataCount.eval(env);
    env.gcSafePoint();

    Value doubleAlignVal = doubleAlign.eval(env);
    env.gcSafePoint();

    env.check(refCountVal.type() == Type.INT, "Number of reference fields must be an integer");
    env.check(dataCountVal.type() == Type.INT, "Number of data fields must be an integer");
    env.check(doubleAlignVal.type() == Type.BOOLEAN, "DoubleAlign must be a boolean");

    ObjectReference object = env.alloc(refCountVal.getIntValue(), dataCountVal.getIntValue(), doubleAlignVal.getBoolValue());

    return new ObjectValue(object);
  }
}
