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
package org.mmtk.harness.lang.runtime;

import org.mmtk.harness.lang.type.Type;
import org.mmtk.plan.TraceLocal;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Expression consisting of a simple object value
 */
public class ObjectValue extends Value {

  /**
   * The null object - actually uses a subtype so that it can have type NULL
   */
  public static final ObjectValue NULL = NullValue.NULL;

  /**
   * The reference to the heap object
   *
   * Not final because it may change during GC
   */
  private ObjectReference value;

  /**
   * Construct an initially null object value
   */
  public ObjectValue() {
    this(ObjectReference.nullReference());
  }

  /**
   * An object value with the given initial value
   * @param value
   */
  public ObjectValue(ObjectReference value) {
    this.value = value;
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
    if (value.isNull()) {
      return "null";
    }
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

  @Override
  public Object marshall(Class<?> klass) {
    if (klass.isAssignableFrom(ObjectValue.class)) {
      return this;
    }
    throw new RuntimeException("Can't marshall an object into a Java Object");
  }

  /**
   * Object equality.
   */
  @Override
  public boolean equals(Object other) {
    return (other instanceof ObjectValue && value.equals(((ObjectValue)other).value));
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

}
