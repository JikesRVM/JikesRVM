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
package org.mmtk.harness.lang.pcode;

import org.mmtk.harness.Harness;
import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.StackFrame;
import org.vmmagic.unboxed.ObjectReference;

public final class AllocOp extends TernaryOp {

  private final int site;

  public AllocOp(Register resultTemp, Register dataCount, Register refCount, Register doubleAlign,int site) {
    super("alloc",resultTemp, dataCount, refCount, doubleAlign);
    this.site = site;
  }

  public int getDataCount(StackFrame frame) {
    return frame.get(op1).getIntValue();
  }
  public int getRefCount(StackFrame frame) {
    return frame.get(op2).getIntValue();
  }
  public boolean getDoubleAlign(StackFrame frame) {
    return frame.get(op3).getBoolValue();
  }

  @Override
  public void exec(Env env) {
    StackFrame frame = env.top();
    ObjectReference object = env.alloc(getRefCount(frame), getDataCount(frame), getDoubleAlign(frame),site);
    setResult(frame,new ObjectValue(object));
    if (Harness.gcEveryAlloc()) {
      env.gc();
    }
  }

}
