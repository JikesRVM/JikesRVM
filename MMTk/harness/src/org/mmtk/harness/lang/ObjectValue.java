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

import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.plan.TraceLocal;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Expression consisting of a simple object value
 */
public class ObjectValue extends Value {
  /** Hand out unique IDs for object variables */
  private static int last_id = 0;

  /** The reference to the heap object */
  private ObjectReference value;

  /** A unique ID for this object value */
  private final int id;

  /**
   * Construct an initially null object value
   */
  public ObjectValue() {
    this(ObjectReference.nullReference());
  }

  /**
   * Object equality.
   */
  @Override
  public boolean equals(Object other) {
    return (other instanceof ObjectValue && value.toAddress().EQ(((ObjectValue)other).value.toAddress()));
  }

  /**
   * An object value with the given initial value
   * @param value
   */
  public ObjectValue(ObjectReference value) {
    this.value = value;
    this.id = last_id++;
  }

  /**
   * Copy the value from the given new value.
   */
  public void copyFrom(Value newValue) {
    Trace.trace(Item.OBJECT, "copy %d(%s) from %d(%s)", id, value.toString(),
        ((ObjectValue)newValue).id, newValue.toString());
    this.value = newValue.getObjectValue();
  }

  /**
   * Get this value as an object.
   */
  public ObjectReference getObjectValue() {
    return value;
  }

  /**
   * Get this value as a boolean.
   */
  public boolean getBoolValue() {
    return !value.isNull();
  }

  /**
   * Prints the address of the object.
   */
  @Override
  public String toString() {
    return value.toString();
  }

  /**
   * The type of this value
   */
  @Override
  public Type type() {
    return Type.OBJECT;
  }

  /**
   * GC-time processing of the contained object
   */
  public void traceObject(TraceLocal trace) {
    value = trace.traceObject(value, true);
  }

  /**
   * @see java.lang.Object#clone()
   */
  @Override
  public ObjectValue clone() {
    return new ObjectValue(value);
  }


}
