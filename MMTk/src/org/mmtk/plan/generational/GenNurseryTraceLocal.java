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
package org.mmtk.plan.generational;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.utility.HeaderByte;
import org.mmtk.utility.deque.*;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implments the core functionality for a transitive
 * closure over the heap graph.
 */
@Uninterruptible
public final class GenNurseryTraceLocal extends TraceLocal {

  /****************************************************************************
   *
   * Instance fields.
   */
  private final ObjectReferenceDeque modbuf;
  private final AddressDeque remset;
  private final AddressPairDeque arrayRemset;


  /**
   * Constructor
   */
  public GenNurseryTraceLocal(Trace trace, GenCollector plan) {
    super(Gen.SCAN_NURSERY, trace);
    this.modbuf = plan.modbuf;
    this.remset = plan.remset;
    this.arrayRemset = plan.arrayRemset;
  }

  /****************************************************************************
   *
   * Externally visible Object processing and tracing
   */

  /**
   * Is the specified object live?
   *
   * @param object The object.
   * @return True if the object is live.
   */
  @Override
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (Gen.inNursery(object)) {
      return Gen.nurserySpace.isLive(object);
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(super.isLive(object));
    return true;
  }

  /**
   * This method is the core method during the trace of the object graph.
   * The role of this method is to:
   *
   * 1. Ensure the traced object is not collected.
   * 2. If this is the first visit to the object enqueue it to be scanned.
   * 3. Return the forwarded reference to the object.
   *
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  @Override
  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    if (Gen.inNursery(object)) {
      return Gen.nurserySpace.traceObject(this, object, Gen.ALLOC_MATURE_MINORGC);
    }
    return object;
  }

  /**
   * Process any remembered set entries.
   */
  @Override
  @Inline
  protected void processRememberedSets() {
    logMessage(5, "processing modbuf");
    ObjectReference obj;
    while (!(obj = modbuf.pop()).isNull()) {
      if (VM.DEBUG) VM.debugging.modbufEntry(obj);
      HeaderByte.markAsUnlogged(obj);
      scanObject(obj);
    }
    logMessage(5, "processing remset");
    while (!remset.isEmpty()) {
      Address loc = remset.pop();
      if (VM.DEBUG) VM.debugging.remsetEntry(loc);
      processRootEdge(loc, false);
    }
    logMessage(5, "processing array remset");
    arrayRemset.flushLocal();
    while (!arrayRemset.isEmpty()) {
      Address start = arrayRemset.pop1();
      Address guard = arrayRemset.pop2();
      if (VM.DEBUG) VM.debugging.arrayRemsetEntry(start,guard);
      while (start.LT(guard)) {
        processRootEdge(start, false);
        start = start.plus(BYTES_IN_ADDRESS);
      }
    }
  }

  /**
   * Will the object move from now on during the collection.
   *
   * @param object The object to query.
   * @return True if the object is guaranteed not to move.
   */
  @Override
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    if (object.isNull()) return false;
    return !Gen.inNursery(object);
  }

}
