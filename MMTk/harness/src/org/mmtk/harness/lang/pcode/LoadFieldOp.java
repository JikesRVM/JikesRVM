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

import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.ast.Type;
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.runtime.IntValue;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.StackFrame;
import org.vmmagic.unboxed.ObjectReference;

public final class LoadFieldOp extends BinaryOp {

    public final Type fieldType;

    public LoadFieldOp(Register resultTemp, Register object, Register index, Type fieldType) {
      super("loadfield",resultTemp, object, index);
      this.fieldType = fieldType;
    }

    public ObjectReference getObject(StackFrame frame) { return frame.get(op1).getObjectValue(); }
    public int getIndex(StackFrame frame) { return frame.get(op2).getIntValue(); }

    public String toString() {
      return String.format("t%d <- t%d.%s[t%d]", getResult(), op1,
          fieldType == Type.OBJECT ? "object" : "int",  op2);
    }

    @Override
    public void exec(Env env) {
      StackFrame frame = env.top();
      switch (fieldType) {
        case INT:
          setResult(frame, new IntValue(env.loadDataField(getObject(frame), getIndex(frame))));
          break;
        case OBJECT:
          setResult(frame, new ObjectValue(env.loadReferenceField(getObject(frame), getIndex(frame))));
          break;
      }

    }

}
