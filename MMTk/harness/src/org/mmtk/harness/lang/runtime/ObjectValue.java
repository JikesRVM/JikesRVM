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
package org.mmtk.harness.lang.runtime;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.lang.type.Type;
import org.mmtk.plan.TraceLocal;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Expression consisting of a simple object value
 * <p>
 * At GC time, these objects form the root set.  For the benefit of collectors
 * like MarkCompact, we need to ensure that each root is only enumerated
 * once when enumerating roots, hence the rootDiscoveryPhase field.
 */
public class ObjectValue extends Value {

  /**
   * The null object - actually uses a subtype so that it can have type NULL
   */
  public static final ObjectValue NULL = NullValue.NULL;

  private static volatile int currentRootDiscoveryPhase = 1;

  /**
   * Start a new root discovery phase, enabling every root to be traced again.
   */
  public static void startRootDiscoveryPhase() {
    currentRootDiscoveryPhase++;
  }

  /**
   * The reference to the heap object
   *
   * Not final because it may change during GC.
   */
  private ObjectReference value;

  /**
   * When was this object last processed as a root ?
   */
  private int lastDiscoveryPhase = 0;

  /**
   * Construct an initially null object value
   */
  public ObjectValue() {
    this(ObjectReference.nullReference());
  }

  /**
   * An object value with the given initial value
   * @param value Initial value
   */
  public ObjectValue(ObjectReference value) {
    this.value = value;
  }

  /**
   * Get this value as an object.
   */
  @Override
  public ObjectReference getObjectValue() {
    return value;
  }

  /**
   * Get this value as a boolean.
   */
  @Override
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
   * @param trace The trace object
   */
  public void traceObject(TraceLocal trace) {
    assert false;
    if (lastDiscoveryPhase < currentRootDiscoveryPhase) {
      ObjectReference old = value;
      value = trace.traceObject(value, true);
      Trace.trace(Item.ROOTS,"ObjectValue.traceObject: %s->%s",old,value);
      lastDiscoveryPhase = currentRootDiscoveryPhase;
    }
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

  /**
   * Use the hash code of the underlying ObjectReference
   */
  @Override
  public int hashCode() {
    return value.hashCode();
  }

}
