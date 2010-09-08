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
package org.mmtk.plan.generational.immix;

import static org.mmtk.policy.immix.ImmixConstants.MARK_LINE_AT_SCAN_TIME;

import org.mmtk.plan.generational.GenCollector;
import org.mmtk.plan.generational.GenMatureTraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implments the core functionality for a transitive
 * closure over the heap graph, specifically in a defragmenting pass over
 * a generational immix collector.
 */
@Uninterruptible
public final class GenImmixMatureDefragTraceLocal extends GenMatureTraceLocal{

  /**
   * Constructor
   */
  public GenImmixMatureDefragTraceLocal(Trace global, GenCollector plan) {
    super(GenImmix.SCAN_DEFRAG, global, plan);
  }

  /**
   * Is the specified object live?
   *
   * @param object The object.
   * @return True if the object is live.
   */
  public boolean isLive(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(GenImmix.immixSpace.inImmixDefragCollection());
    if (object.isNull()) return false;
    if (Space.isInSpace(GenImmix.IMMIX, object)) {
      return GenImmix.immixSpace.isLive(object);
    }
    return super.isLive(object);
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
  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(GenImmix.immixSpace.inImmixDefragCollection());
    if (object.isNull()) return object;
    if (Space.isInSpace(GenImmix.IMMIX, object))
      return GenImmix.immixSpace.traceObject(this, object, GenImmix.ALLOC_MATURE_MAJORGC);
    return super.traceObject(object);
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
    if (Space.isInSpace(GenImmix.IMMIX, object)) {
      return GenImmix.immixSpace.willNotMoveThisGC(object);
    }
    return super.willNotMoveInCurrentCollection(object);
  }

  /**
   * Collectors that move objects <b>must</b> override this method.
   * It performs the deferred scanning of objects which are forwarded
   * during bootstrap of each copying collection.  Because of the
   * complexities of the collection bootstrap (such objects are
   * generally themselves gc-critical), the forwarding and scanning of
   * the objects must be dislocated.  It is an error for a non-moving
   * collector to call this method.
   *
   * @param object The forwarded object to be scanned
   */
  @Inline
  @Override
  protected void scanObject(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS && Options.verbose.getValue() >= 9) {
      Log.write("SO["); Log.write(object); Log.writeln("]");
    }
    super.scanObject(object);
    if (MARK_LINE_AT_SCAN_TIME && Space.isInSpace(GenImmix.IMMIX, object))
      GenImmix.immixSpace.markLines(object);
  }
}
