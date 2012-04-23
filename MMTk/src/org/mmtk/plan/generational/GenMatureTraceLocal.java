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

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This abstract class implments the core functionality for a transitive
 * closure over the heap graph.
 */
@Uninterruptible
public abstract class GenMatureTraceLocal extends TraceLocal {

  /****************************************************************************
   *
   * Instance fields.
   */
  private final ObjectReferenceDeque modbuf;
  private final AddressDeque remset;
  private final AddressPairDeque arrayRemset;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public GenMatureTraceLocal(int specializedScan, Trace trace, GenCollector plan) {
    super(specializedScan, trace);
    this.modbuf = plan.modbuf;
    this.remset = plan.remset;
    this.arrayRemset = plan.arrayRemset;
  }

  /**
   * Constructor
   */
  public GenMatureTraceLocal(Trace trace, GenCollector plan) {
    super(Gen.SCAN_MATURE, trace);
    this.modbuf = plan.modbuf;
    this.remset = plan.remset;
    this.arrayRemset = plan.arrayRemset;
  }

  /****************************************************************************
   *
   * Object processing and tracing
   */

  /**
   * Is the specified object live?
   *
   * @param object The object.
   * @return True if the object is live.
   */
  @Override
  @Inline
  public boolean isLive(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!object.isNull());
    if (Gen.inNursery(object)) {
      return Gen.nurserySpace.isLive(object);
    }
    return super.isLive(object);
  }

  /**
   * Return true if this object is guaranteed not to move during this
   * collection (i.e. this object is defintely not an unforwarded
   * object).
   *
   * @param object
   * @return True if this object is guaranteed not to move during this
   *         collection.
   */
  @Override
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    if (Gen.inNursery(object))
      return false;
    else
      return super.willNotMoveInCurrentCollection(object);
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
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!object.isNull());
    if (Gen.inNursery(object))
      return Gen.nurserySpace.traceObject(this, object, Gen.ALLOC_MATURE_MAJORGC);
    return super.traceObject(object);
  }

  /**
   * Process any remembered set entries.
   */
  @Override
  protected void processRememberedSets() {
    logMessage(5, "clearing modbuf");
    ObjectReference obj;
    while (!(obj = modbuf.pop()).isNull()) {
      HeaderByte.markAsUnlogged(obj);
    }
    logMessage(5, "clearing remset");
    while (!remset.isEmpty()) {
      remset.pop();
    }
    logMessage(5, "clearing array remset");
    while (!arrayRemset.isEmpty()) {
      arrayRemset.pop1();
      arrayRemset.pop2();
    }
  }

}
