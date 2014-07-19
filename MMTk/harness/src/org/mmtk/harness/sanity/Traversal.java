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
package org.mmtk.harness.sanity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.mmtk.harness.Harness;
import org.mmtk.harness.Mutator;
import org.mmtk.harness.Mutators;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.vm.ActivePlan;
import org.mmtk.harness.vm.ObjectModel;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * Perform a traversal of the heap, calling appropriate methods on
 * the HeapVisitor object supplied.
 */
public final class Traversal {

  private static final boolean VERBOSE = false;

  /**
   * Traverse the heap.
   * @param visitor The heap visitor
   */
  public static void traverse(HeapVisitor visitor) {
    new Traversal(visitor);
  }

  private final Set<ObjectReference> blackSet = new HashSet<ObjectReference>();
  private final List<ObjectReference> markStack = new ArrayList<ObjectReference>();
  private final HeapVisitor visitor;

  /**
   * Perform the traversal.
   * @param visitor
   */
  private Traversal(HeapVisitor visitor) {
    this.visitor = visitor;
    traceRoots();
    doClosure();
  }

  /**
   * Scan an object, calling the appropriate visitor method
   * @param object
   */
  private void scan(ObjectReference object) {
    if (VERBOSE) {
      Trace.trace(Item.SANITY, "scanning object %s", ObjectModel.getString(object));
    }
    for (int i=0; i < ObjectModel.getRefs(object); i++) {
      Address slot = ObjectModel.getRefSlot(object, i);
      ObjectReference ref = loadReferenceSlot(slot);
      if (!ref.isNull()) {
        visitor.visitPointer(object, slot, ref);
        traceObject(ref,false);
      }
    }
  }

  private ObjectReference loadReferenceSlot(Address slot) {
    if (Harness.sanityUsesReadBarrier.getValue()) {
      return ActivePlan.plan.loadObjectReference(slot);
    }
    return slot.loadObjectReference();
  }

  /**
   * Trace an object, calling the appropriate visitor method
   * @param object
   * @param root
   */
  private void traceObject(ObjectReference object, boolean root) {
    if (VERBOSE) {
      Trace.trace(Item.SANITY, "tracing object %s", ObjectModel.getString(object));
    }
    if (object.isNull()) return;
    boolean marked = blackSet.contains(object);
    if (!marked) {
      blackSet.add(object);
      markStack.add(object);
    }
    visitor.visitObject(object, root, marked);
  }

  /**
   * Trace the harness root set
   */
  private void traceRoots() {
    for (Mutator m : Mutators.getAll()) {
      for (Address root : m.getRootAddresses()) {
        traceObject(root.loadObjectReference(), true);
      }
    }
  }

  /**
   * Iterate over the heap depth-first, scanning objects until
   * the mark stack is empty.
   */
  private void doClosure() {
    while (markStack.size() > 0) {
      ObjectReference object = markStack.remove(markStack.size()-1);
      scan(object);
    }
    blackSet.clear();
    markStack.clear();
  }
}
