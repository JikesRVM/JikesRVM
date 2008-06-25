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

/**
 * Assign a field of an object.
 */
public class StoreField implements Statement {
  /** Stack slot of object variable */
  private final int slot;
  /** Field within the object (int) */
  private final Expression index;
  /** Type of the field being stored */
  private final Type type;
  /** Value to assign to the field */
  private final Expression value;

  /**
   * Assign the result of the given expression to the given field
   * of the object in stack frame slot 'slot'.
   */
  public StoreField(int slot, Type type, Expression index, Expression value) {
    this.slot = slot;
    this.index = index;
    this.type = type;
    this.value = value;
  }

  /**
   * Evaluate the field index expression, and then the value expression.
   * Store the result into the appropriate field.
   */
  public void exec(Env env) {
    Value fieldVal = index.eval(env);
    Value assignVal = value.eval(env);

    env.check(env.top().getType(slot) == Type.OBJECT, "Attempt to store field of non-object variable");
    env.check(fieldVal.type() == Type.INT, "Index must be an integer.");
    env.check(assignVal.type() == type, "Attempt to store invalid value: Expected " + type + ", found " + assignVal.type());

    int fieldIndex = fieldVal.getIntValue();
    ObjectReference object = env.top().get(slot).getObjectValue();

    switch (type) {
      case INT: {
        int value = assignVal.getIntValue();
        env.storeDataField(object, fieldIndex, value);
        break;
      }
      case OBJECT: {
        ObjectReference value = assignVal.getObjectValue();
        env.storeReferenceField(object, fieldIndex, value);
        break;
      }
    }
  }
}
