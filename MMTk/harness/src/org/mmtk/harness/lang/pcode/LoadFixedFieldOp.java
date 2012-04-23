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
import org.mmtk.harness.lang.runtime.IntValue;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.StackFrame;
import org.mmtk.harness.lang.type.Type;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Load a field from an object, index given statically
 */
public final class LoadFixedFieldOp extends UnaryOp {

  /** The field type (int or object) */
  private final Type fieldType;
  private final int index;
  private final String fieldName;

  /**
   * Create an instruction for the operation
   *   resultTemp <- object.<fieldType>[index]
   *
   * @param source     Source file location (parser Token)
   * @param resultTemp Result destination
   * @param object     Object operand
   * @param index      Index operand
   * @param fieldType  Field type (int or object)
   */
  public LoadFixedFieldOp(AST source, Register resultTemp, Register object, int index, String fieldName, Type fieldType) {
    super(source,"loadfield",resultTemp, object);
    this.fieldType = fieldType;
    this.index = index;
    this.fieldName = fieldName;
  }

  /**
   * Get the object on which this instruction operates from the
   * location it occupies in the stack frame.
   *
   * @param frame
   * @return
   */
  private ObjectReference getObject(StackFrame frame) {
    return frame.get(operand).getObjectValue();
  }

  @Override
  public String toString() {
    return String.format("%s <- %s.%s", Register.nameOf(getResult()),
        Register.nameOf(operand),fieldName);
  }

  @Override
  public void exec(Env env) {
    StackFrame frame = env.top();
    if (fieldType == Type.INT) {
      setResult(frame, new IntValue(env.loadDataField(getObject(frame), index)));
    } else if (fieldType == Type.OBJECT) {
      setResult(frame, new ObjectValue(env.loadReferenceField(getObject(frame), index)));
    }
  }

}
