/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.harness.lang.pcode;

import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.ast.AST;
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.runtime.StackFrame;
import org.mmtk.harness.lang.type.Type;
import org.vmmagic.unboxed.ObjectReference;

public final class StoreFieldOp extends TernaryOp {

  public final Type fieldType;

  public StoreFieldOp(AST source, Register object, Register index, Register val, Type fieldType) {
    super(source,"storeField",object, index, val);
    this.fieldType = fieldType;
  }

  @Override
  public String toString() {
    return String.format("%s.%s[%s] <- %s", Register.nameOf(op1),
        fieldType == Type.OBJECT ? "object" : "int",
            Register.nameOf(op2), Register.nameOf(op3));
  }

  @Override
  public void exec(Env env) {
    StackFrame frame = env.top();

    int fieldIndex = getIndex(frame);
    ObjectReference object = getObjectObj(frame);

    if (fieldType == Type.INT) {
      env.storeDataField(object, fieldIndex, getValInt(frame));
    } else if (fieldType == Type.OBJECT) {
      env.storeReferenceField(object, fieldIndex, getValObject(frame));
    }
  }

  private ObjectReference getObjectObj(StackFrame frame) {
    return frame.get(op1).getObjectValue();
  }

  private int getIndex(StackFrame frame) {
    return frame.get(op2).getIntValue();
  }

  private int getValInt(StackFrame frame) {
    return frame.get(op3).getIntValue();
  }

  private ObjectReference getValObject(StackFrame frame) {
    return frame.get(op3).getObjectValue();
  }

}
