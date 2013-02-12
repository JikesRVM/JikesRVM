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
package org.mmtk.vm;

import org.mmtk.plan.TraceLocal;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 *
 */
@Uninterruptible
public abstract class Debug {
  /**
   * Global switch for debugging - if {@code false} the other methods of this
   * class are never called.
   * @return Whether debugging is enabled
   */
  public abstract boolean isEnabled();

  /**
   * A modbuf (object remembering barrier) entry has been
   * traced during collection.
   * @param object The modbuf entry
   */
  public void modbufEntry(ObjectReference object) { }

  /**
   * A remset (slot remembering barrier) entry has been
   * traced during collection.
   * @param slot The remset entry
   */
  public void remsetEntry(Address slot) { }

  /**
   * An array remset entry has been traced during collection.  Implicitly
   * the slots from start (inclusive) through to guard (non-inclusive)
   * are traced as remset entries
   * @param start The entry start address
   * @param guard The guard
   */
  public void arrayRemsetEntry(Address start, Address guard) { }

  /**
   * A global GC collection phase
   * @param phaseId The phase ID
   * @param before true at the start of the phase, false at the end
   */
  public void globalPhase(short phaseId, boolean before) { }

  /**
   * A per-collector GC collection phase
   * @param phaseId The phase ID
   * @param ordinal The collector ID (within this collection)
   * @param before true at the start of the phase, false at the end
   */
  public void collectorPhase(short phaseId, int ordinal, boolean before) { }

  /**
   * A per-mutator GC collection phase
   * @param phaseId The phase ID
   * @param ordinal The mutator ID
   * @param before true at the start of the phase, false at the end
   */
  public void mutatorPhase(short phaseId, int ordinal, boolean before) { }

  /**
   * Trace an object during GC
   *
   * *** Non-standard, requires plumbing into a collector during debugging ***
   *
   * @param trace The trace being performed
   * @param object The object
   */
  public void traceObject(TraceLocal trace, ObjectReference object) { }

  /**
   * An entry has been inserted at the head of a queue
   *
   * *** Non-standard, requires plumbing into a collector during debugging ***
   *
   * @param queueName The name of the queue
   * @param value The value
   */
  public void queueHeadInsert(String queueName, Address value) {
  }

  /**
   * An entry has been inserted at the head of a queue
   *
   * *** Non-standard, requires plumbing into a collector during debugging ***
   *
   * @param queueName The name of the queue
   * @param value The value
   */
  public void queueTailInsert(String queueName, Address value) {
  }

  /**
   * An entry has been inserted at the head of a queue
   *
   * *** Non-standard, requires plumbing into a collector during debugging ***
   *
   * @param queueName The name of the queue
   * @param value The value
   */
  public void queueHeadRemove(String queueName, Address value) { }

  /**
   * An entry has been inserted at the head of a queue
   *
   * *** Non-standard, requires plumbing into a collector during debugging ***
   *
   * @param queueName The name of the queue
   * @param value The value
   */
  public void queueTailRemove(String queueName, Address value) { }

  /*
   * NOTE: These methods should not be called by anything other than the
   * reflective mechanisms in org.mmtk.vm.VM, and are not implemented by
   * subclasses.
   *
   * This hack exists only to allow us to declare the respective
   * methods as protected.
   */
  static final boolean isEnabledTrapdoor(Debug d) {
    return d.isEnabled();
  }

}
