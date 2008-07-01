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
  /** GC stress - GC on every allocation */
  private static boolean gcEveryAlloc = false;

  /**
   * GC stress - GC after every allocation
   */
  public static void setGcEveryAlloc() {
    gcEveryAlloc = true;
  }

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
    int refCountVal = evalInt(env, refCount, "Number of reference fields must be an integer");
    int dataCountVal = evalInt(env,dataCount, "Number of data fields must be an integer");
    boolean doubleAlignVal = evalBoolVal(env, doubleAlign, "DoubleAlign must be a boolean");

    ObjectReference object = env.alloc(refCountVal, dataCountVal, doubleAlignVal);
    ObjectValue oval = new ObjectValue(object);
    if (gcEveryAlloc) {
      env.pushTemporary(oval);
      env.gc();
      env.popTemporary(oval);
    }
    return oval;
  }

  /**
   * Evaluate a boolean expression, type-check and return a boolean
   * @param env
   * @param doubleAlign2
   * @param message
   * @return
   */
  private boolean evalBoolVal(Env env, Expression doubleAlign2, String message) {
    Value doubleAlignVal = doubleAlign2.eval(env);

    env.check(doubleAlignVal.type() == Type.BOOLEAN, message);

    boolean doubleAlignBool = doubleAlignVal.getBoolValue();
    env.gcSafePoint();
    return doubleAlignBool;
  }

  /**
   * Evaluate an int expression, type-check and return an int
   * @param env
   * @param doubleAlign2
   * @param message
   * @return
   */
  private int evalInt(Env env, Expression refCount2, String message) {
    Value refCountVal = refCount2.eval(env);
    env.check(refCountVal.type() == Type.INT, message);
    int refCountInt = refCountVal.getIntValue();
    env.gcSafePoint();
    return refCountInt;
  }
}
