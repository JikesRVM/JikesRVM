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
 * <p>
 * In the MMTk harness, Reference types aren't heap objects.  This has the up side
 * that we can debug problems with forwarding of referents more easily, but means
 * that we don't exercise the method getForwardedReference, just getForwardedReferent.
 * <p>
 * TODO make this more comprehensive.
 */
public abstract class ReferenceValue extends Value {

  private static int nextId = 0x100001;

  private final int id = nextId++;
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

  @Override
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
   * GC-time processing of the contained object.
   * <p>
   * Corresponds to the core of the processReference method of the MMTk ReferenceProcessor
   * for the WEAK reference type.
   *
   * @param trace The MMTk trace
   */
  public void processReference(TraceLocal trace) {
    if (cleared)
      return;
    ObjectReference newRef = trace.getForwardedReferent(ref);
    if (Trace.isEnabled(Item.REFERENCES)) {
      Trace.trace(Item.REFERENCES, "Forwarded reference %x: %s reference (%s -> %s)",
          id, semantics, ObjectModel.getString(ref), ObjectModel.getString(newRef));
    }
    ref = newRef;
  }

  /**
   * Deferred GC-time processing of the contained object, used in collectors
   * like MarkCompact which determine liveness separately from copying.
   * <p>
   * Corresponds to the core of the forward method of the MMTk ReferenceProcessor
   * for the WEAK reference type.
   *
   * @param trace The MMTk trace
   */
  public void forwardReference(TraceLocal trace) {
    if (cleared)
      return;
    /*
     * Currently, do exactly the same thing for process as for forward.
     *
     * TODO - perhaps make this different, only really relevant when we have different semantics
     * for the different types of references.
     */
    Trace.trace(Item.REFERENCES, "Forwarding reference %x: %s reference %s",
        System.identityHashCode(this), semantics,
        ObjectModel.getString(ref));
    processReference(trace);
  }

  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String toString() {
    return String.format("%x: %s reference (-> %s)", id, semantics, ObjectModel.getString(ref));
  }
}

