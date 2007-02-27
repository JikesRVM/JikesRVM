/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.copyms;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.Space;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class implments the thread-local functionality for a
 * transitive closure over a coping/mark-sweep hybrid collector.
 * 
 *
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 */
@Uninterruptible public final class CopyMSTraceLocal extends TraceLocal {

  /**
   * Constructor
   */
  public CopyMSTraceLocal(Trace trace) {
    super(trace);
  }

  /****************************************************************************
   * 
   * Externally visible Object processing and tracing
   */

  /**
   * Is the specified object reachable?
   * 
   * @param object The object.
   * @return True if the object is live.
   */
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (Space.isInSpace(CopyMS.NURSERY, object)) {
      return CopyMS.nurserySpace.isLive(object);
    }
    if (Space.isInSpace(CopyMS.MARK_SWEEP, object)) {
      return CopyMS.msSpace.isLive(object);
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
   * In this instance, we refer objects in the mark-sweep space to the
   * msSpace for tracing, and defer to the superclass for all others.
   * 
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  @Inline
  public ObjectReference traceObject(ObjectReference object) { 
    if (object.isNull()) return object;
    if (Space.isInSpace(CopyMS.NURSERY, object))
      return CopyMS.nurserySpace.traceObject(this, object);
    if (Space.isInSpace(CopyMS.MARK_SWEEP, object))
      return CopyMS.msSpace.traceObject(this, object);
    return super.traceObject(object);
  }


  /**
   * Ensure that this object will not move for the rest of the GC.
   * 
   * @param object The object that must not move
   * @return The new object, guaranteed stable for the rest of the GC.
   */
  @Inline
  public ObjectReference precopyObject(ObjectReference object) { 
    if (object.isNull()) return object;
    else if (Space.isInSpace(CopyMS.NURSERY, object))
      return CopyMS.nurserySpace.traceObject(this, object);
    else
      return object;
  }

  /**
   * Will this object move from this point on, during the current collection ?
   * 
   * @param object The object to query.
   * @return True if the object will not move during this collection.
   */
  public boolean willNotMove(ObjectReference object) {
    return !Space.isInSpace(CopyMS.NURSERY, object);
  }

  /**
   * @return The allocator to use when copying objects.
   */
  @Inline
  public final int getAllocator() { 
    return CopyMS.ALLOC_MS;
  }
}
