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

public class Alloc implements Statement {
  /** Stack slot to store object variable */
  private final int slot;
  /** Number of reference fields */
  private final Expression refCount;
  /** Number of data fields */
  private final Expression dataCount;
  /** Double align the object? */
  private final Expression doubleAlign;

  /**
   * Allocate an object into the given stack frame slot, with numbers of
   * reference and data fields given by expressions.
   */
  public Alloc(int slot, Expression refCount, Expression dataCount, Expression doubleAlign) {
    this.slot = slot;
    this.refCount = refCount;
    this.dataCount = dataCount;
    this.doubleAlign = doubleAlign;
  }

  /**
   * Run this statement. Evaluate the expression arguments, then call MMTk
   * (via the environment) to allocate the object.
   */
  public void exec(Env env) {
    Value refCountVal = refCount.eval(env);
    Value dataCountVal = dataCount.eval(env);
    Value doubleAlignVal = doubleAlign.eval(env);

    env.check(env.top().getType(slot) == Type.OBJECT, "Attempt to store object in non-object variable");
    env.check(refCountVal.type() == Type.INT, "Number of reference fields must be an integer");
    env.check(dataCountVal.type() == Type.INT, "Number of data fields must be an integer");
    env.check(doubleAlignVal.type() == Type.BOOLEAN, "DoubleAlign must be a boolean");

    ObjectReference object = env.alloc(refCountVal.getIntValue(), dataCountVal.getIntValue(), doubleAlignVal.getBoolValue());

    if (slot >= 0) {
      ObjectValue value = new ObjectValue(object);
      env.top().set(slot, value);
    }
  }
}
