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
 * An expression returning the value of a field in an object.
 */
public class LoadField implements Expression {
  /** Stack slot of object variable */
  private final int slot;
  /** Field within the object (int) */
  private final Expression index;
  /** Type of the field being read */
  private final Type type;

  /**
   * Load a field and store the loaded value into the stack.
   */
  public LoadField(int slot, Type type, Expression index) {
    this.slot = slot;
    this.index = index;
    this.type = type;
  }

  /**
   * Evaluate the expression.  Dereference the object variable,
   * then load the slot in the object.
   */
  public Value eval(Env env) {
    Value fieldVal = index.eval(env);

    env.check(env.top().getType(slot) == Type.OBJECT, "Attempt to load field of non-object variable");
    env.check(fieldVal.type() == Type.INT, "Index must be an integer");

    int fieldIndex = fieldVal.getIntValue();
    ObjectReference object = env.top().get(slot).getObjectValue();

    switch (type) {
      case INT:
        return new IntValue(env.loadDataField(object, fieldIndex));
      case OBJECT:
        return new ObjectValue(env.loadReferenceField(object, fieldIndex));
    }

    env.notReached();
    return null;
  }
}
