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
package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.plan.refcount.RCHeader;
import org.mmtk.policy.Space;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class implments the thread-local functionality for a transitive
 * closure over a mark-sweep space.
 */
@Uninterruptible public final class GenRCTraceLocal extends TraceLocal {
  /**
   * Constructor
   */
  public GenRCTraceLocal(Trace trace) {
    super(trace);
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
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (Space.isInSpace(GenRC.NS, object)) {
      return GenRC.nurserySpace.isLive(object);
    }
    if (GenRC.isRCObject(object)) {
      return RCHeader.isLiveRC(object);
    }
    return super.isLive(object);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param object The object reference to be traced.  This is <i>NOT</i>
   * an interior pointer.
   * @param root True if this reference to <code>object</code> was held
   * in a root.
   * @return The possibly moved reference.
   */
  public ObjectReference traceObject(ObjectReference object,
                                           boolean root) {
    if (object.isNull()) return object;
    if (Space.isInSpace(GenRC.NS, object)) {
      object = GenRC.nurserySpace.traceObject(this, object, GenRC.ALLOC_RC);
    } else if (!GenRC.isRCObject(object)) {
      return object;
    }
    if (root) {
      collector().reportRoot(object);
    } else {
      RCHeader.incRC(object);
    }
    return object;
  }

  /**
   * This method traces an object with knowledge of the fact that object
   * is a root or not. In simple collectors the fact it is a root is not
   * important so this is the default implementation given here.
   *
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    return traceObject(object, false);
  }

  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!object.isNull());
    return !(Space.isInSpace(GenRC.NS, object));
  }

  public ObjectReference precopyObject(ObjectReference object) {
    if (Space.isInSpace(GenRC.NS, object))
      return GenRC.nurserySpace.traceObject(this, object, GenRC.ALLOC_RC);
    return object;
  }

  /**
   * Miscellaneous
   */

  /**
   * Called during the trace to process any remsets. As there is a bug
   * in JikesRVM where write barriers occur during GC, this is
   * necessary.
   */
  public void flushRememberedSets() {
    collector().processModBuffer();
  }

  /**
   * @return The current RC collector instace.
   */
  @Inline
  private static GenRCCollector collector() {
    return (GenRCCollector)VM.activePlan.collector();
  }
}
