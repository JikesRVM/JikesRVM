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
import org.mmtk.harness.vm.ObjectModel;
import org.mmtk.plan.TraceLocal;
import org.mmtk.vm.ReferenceProcessor.Semantics;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Moral equivalent of java.lang.ref.Reference
 */
public abstract class ReferenceValue extends Value {

  private ObjectReference ref;
  private final Semantics semantics;
  private boolean cleared = false;

  protected ReferenceValue(ObjectReference ref, Semantics semantics) {
    this.ref = ref;
    this.semantics = semantics;
  }

  public void clear() {
    Trace.trace(Item.REFERENCES, "Clearing reference %s", this);
    cleared = true;
  }

  /**
   * @see org.mmtk.harness.lang.runtime.Value#getObjectValue()
   */
  public ObjectReference getObjectValue() {
    return cleared ? ObjectReference.nullReference() : ref;
  }

  /**
   * @return The reference semantics
   */
  public Semantics getSemantics() {
    return semantics;
  }

  /**
   * @see org.mmtk.harness.lang.runtime.Value#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  /**
   * GC-time processing of the contained object
   * @param trace The MMTk trace
   */
  public void traceObject(TraceLocal trace) {
    String before = ObjectModel.getString(ref);
    ref = trace.traceObject(ref, true);
    Trace.trace(Item.REFERENCES, "Forwarded reference %x: %s reference (%s -> %s)",
        System.identityHashCode(this), semantics, before, ObjectModel.getString(ref));
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String toString() {
    return String.format("%x: %s reference (-> %s)", System.identityHashCode(this), semantics, ObjectModel.getString(ref));
  }
}

