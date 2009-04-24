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

/**
 * Load a field from an object, the field offset known at compile
 * time.
 */
public final class StoreFixedFieldOp extends BinaryOp {

  /** Type of field - INT or OBJECT */
  private final Type fieldType;
  /** Index of field */
  private final int index;
  /** Name of the field - for error messages etc */
  private final String fieldName;

  /**
   * Store a value to a field of an object
   * @param source The source code element corresponding to the operation
   * @param object The register that points to the object
   * @param index The field index within the object's reference or non-reference fields
   * @param fieldName Name of the field
   * @param val The value to store
   * @param fieldType Type of field (INT or OBJECT)
   */
  public StoreFixedFieldOp(AST source, Register object, int index, String fieldName, Register val, Type fieldType) {
    super(source,"storeField",object, val);
    this.fieldType = fieldType;
    this.index = index;
    this.fieldName = fieldName;
    assert fieldType == Type.OBJECT || fieldType == Type.INT;
  }

  public String toString() {
    return String.format("%s.%s <- %s",
        Register.nameOf(op1),fieldName,Register.nameOf(op2));
  }

  @Override
  public void exec(Env env) {
    StackFrame frame = env.top();

    ObjectReference object = getObjectObj(frame);

    if (fieldType == Type.INT) {
      env.storeDataField(object, index, getValInt(frame));
    } else if (fieldType == Type.OBJECT) {
      env.storeReferenceField(object, index, getValObject(frame));
    }
  }

  private ObjectReference getObjectObj(StackFrame frame) {
    return frame.get(op1).getObjectValue();
  }

  private int getValInt(StackFrame frame) {
    return frame.get(op2).getIntValue();
  }

  private ObjectReference getValObject(StackFrame frame) {
    return frame.get(op2).getObjectValue();
  }

}
