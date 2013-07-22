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
package org.mmtk.harness.vm;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.lang.runtime.ReferenceValue;
import org.mmtk.plan.TraceLocal;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.harness.Clock;

/**
 * This class manages SoftReferences, WeakReferences, and
 * PhantomReferences.
 * <p>
 * The harness only provides reference types to ensure that the MMTk
 * Plan processes them correctly, so all types have the semantics
 * of weak references.
 */
@Uninterruptible
public final class ReferenceProcessor extends org.mmtk.vm.ReferenceProcessor {

  private static Map<Semantics,ReferenceProcessor> processors =
    new EnumMap<Semantics,ReferenceProcessor>(Semantics.class);

  static {
    processors.put(Semantics.SOFT, new ReferenceProcessor(Semantics.SOFT));
    processors.put(Semantics.WEAK, new ReferenceProcessor(Semantics.WEAK));
    processors.put(Semantics.PHANTOM, new ReferenceProcessor(Semantics.PHANTOM));
  }

  static ReferenceProcessor getProcessorFor(Semantics semantics) {
    return processors.get(semantics);
  }

  /**
   * Discover a reference value while scanning stacks
   * @param ref Reference value
   */
  public static void discover(ReferenceValue ref) {
    processors.get(ref.getSemantics()).add(ref);
  }

  /*
   * The set of reference objects of this semantics
   */
  private final Set<ReferenceValue> oldRefs = new HashSet<ReferenceValue>();
  private final Set<ReferenceValue> currentRefs = Collections.synchronizedSet(new HashSet<ReferenceValue>());
  private final Set<ReferenceValue> newRefs = Collections.synchronizedSet(new HashSet<ReferenceValue>());

  private final Semantics semantics;

  private ReferenceProcessor(Semantics semantics) {
    this.semantics = semantics;
  }

  /**
   * Add a reference value to the set of references of this type.
   * <p>
   * This method is thread-safe, to support concurrent collection of
   * roots, by virtue of the synchronized collection types used for newRefs
   * and currentRefs.
   *
   * @param ref The reference value
   */
  private void add(ReferenceValue ref) {
    Clock.stop();
    Trace.trace(Item.REFERENCES, "Discovered reference %s", ref);
    Clock.start();
    if (!oldRefs.contains(ref)) {
      newRefs.add(ref);
    } else {
      currentRefs.add(ref);
    }
  }

  @Override
  public void clear() {
    Clock.stop();
    Trace.trace(Item.REFERENCES, "Clearing %s references", semantics);
    Clock.start();
    currentRefs.clear();
    newRefs.clear();
  }

  /**
   * {@inheritDoc}
   * <p>
   * TODO support concurrent scans
   * <p>
   * TODO the nursery/mature logic could be improved
   */
  @Override
  public synchronized void scan(TraceLocal trace, boolean nursery) {
    Clock.stop();
    Trace.trace(Item.REFERENCES, "Scanning %s references: current = %d, new = %d, %s",
        semantics,currentRefs.size(), newRefs.size(), nursery  ? "nursery" : "full-heap");
    Clock.start();
    if (!nursery) {
      scanReferenceSet(trace, currentRefs);
    }
    scanReferenceSet(trace, newRefs);
    oldRefs.clear();
    oldRefs.addAll(currentRefs);
    oldRefs.addAll(newRefs);
    currentRefs.clear();
    newRefs.clear();
  }

  private void scanReferenceSet(TraceLocal trace, Set<ReferenceValue> set) {
    for (ReferenceValue value : set) {
      ObjectReference referent = value.getObjectValue();
      if (trace.isReferentLive(referent)) {
        value.processReference(trace);
      } else {
        value.clear();
      }
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Only relevant to collectors like MarkCompact.
   */
  @Override
  public void forward(TraceLocal trace, boolean nursery) {
    Clock.stop();
    Trace.trace(Item.REFERENCES, "Forwarding %s references: %s",
        semantics,nursery ? "nursery" : "full-heap");
    Clock.start();
    for (ReferenceValue value : oldRefs) {
      value.forwardReference(trace);
    }
  }

  @Override
  public int countWaitingReferences() {
    return currentRefs.size() + newRefs.size();
  }
}
