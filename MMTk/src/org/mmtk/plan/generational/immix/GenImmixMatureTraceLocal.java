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

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements the core functionality for a transitive
 * closure over the heap graph, specifically in a generational immix
 * collector.
 */
@Uninterruptible
public final class GenImmixMatureTraceLocal extends GenMatureTraceLocal{

  /**
   * Constructor
   */
  public GenImmixMatureTraceLocal(Trace global, GenCollector plan) {
    super(GenImmix.SCAN_IMMIX, global, plan);
  }

  @Override
  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    if (object.isNull()) return object;

    if (Space.isInSpace(GenImmix.IMMIX, object)) {
      return GenImmix.immixSpace.fastTraceObject(this, object);
    }
    return super.traceObject(object);
  }

  @Override
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (Space.isInSpace(GenImmix.IMMIX, object)) {
      return GenImmix.immixSpace.isLive(object);
    }
    return super.isLive(object);
  }

  @Override
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    if (Space.isInSpace(GenImmix.IMMIX, object)) {
      return true;
    }
    return super.willNotMoveInCurrentCollection(object);
  }

  @Inline
  @Override
  protected void scanObject(ObjectReference object) {
    super.scanObject(object);
    if (MARK_LINE_AT_SCAN_TIME && Space.isInSpace(GenImmix.IMMIX, object))
      GenImmix.immixSpace.markLines(object);
  }
}
