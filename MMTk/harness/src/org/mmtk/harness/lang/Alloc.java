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

  /** Call site ID */
  private final int site;
  /** Number of reference fields */
  private final Expression refCount;
  /** Number of data fields */
  private final Expression dataCount;
  /** Double align the object? */
  private final Expression doubleAlign;

  /**
   * Allocate an object.
   */
  public Alloc(int site, Expression refCount, Expression dataCount, Expression doubleAlign) {
    this.site = site;
    this.refCount = refCount;
    this.dataCount = dataCount;
    this.doubleAlign = doubleAlign;
  }

  /**
   * Perform the allocation by calling MMTk.
   */
  public Value eval(Env env) {
    int refCountVal = env.evalInt(refCount, "Number of reference fields must be an integer");
    int dataCountVal = env.evalInt(dataCount, "Number of data fields must be an integer");
    boolean doubleAlignVal = env.evalBoolVal(doubleAlign, "DoubleAlign must be a boolean");

    ObjectReference object = env.alloc(refCountVal, dataCountVal, doubleAlignVal,site);
    ObjectValue oval = new ObjectValue(object);
    if (gcEveryAlloc) {
      env.pushTemporary(oval);
      env.gc();
      env.popTemporary(oval);
    }
    return oval;
  }
}
