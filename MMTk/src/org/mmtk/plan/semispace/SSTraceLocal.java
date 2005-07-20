/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.semispace;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.Space;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class implments the core functionality for a transitive
 * closure over the heap graph.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 * @author Robin Garner
 * @author Daniel Frampton
 * 
 * @version $Revision$
 * @date $Date$
 */
public class SSTraceLocal extends TraceLocal implements Uninterruptible {
  /**
   * Constructor
   */
  public SSTraceLocal(Trace trace) {
    super(trace);
  }

  /****************************************************************************
   *
   * Externally visible Object processing and tracing
   */

  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param object The object in question
   * @return True if <code>obj</code> is a live object.
   */
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (Space.isInSpace(SS.SS0, object))
      return SS.hi ? SS.copySpace0.isLive(object) : true;
    if (Space.isInSpace(SS.SS1, object))
      return SS.hi ? true : SS.copySpace1.isLive(object);
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
  public ObjectReference traceObject(ObjectReference object)
    throws InlinePragma {
    if (object.isNull()) return object;
    if (Space.isInSpace(SS.SS0, object))
      return SS.copySpace0.traceObject(this,object);
    if (Space.isInSpace(SS.SS1, object))
      return SS.copySpace1.traceObject(this,object);
    // TODO - should this be super.traceObject() ?
    return Space.getSpaceForObject(object).traceObject(this,object);
  }

  /**
   * Ensure that this object will not move for the rest of the GC.
   *
   * @param object The object that must not move
   * @return The new object, guaranteed stable for the rest of the GC.
   */
  public ObjectReference precopyObject(ObjectReference object)
    throws InlinePragma {
    if (object.isNull()) return object;
    else if (Space.isInSpace(SS.SS0, object))
      return SS.copySpace0.traceObject(this,object);
    else if (Space.isInSpace(SS.SS1, object))
      return SS.copySpace1.traceObject(this,object);
    else
      return object;
  }

  /**
   * Will this object move from this point on, during the current trace ?
   * 
   * @param object The object to query.
   * @return True if the object will not move.
   */
  public boolean willNotMove (ObjectReference object) {
    return (SS.hi && !Space.isInSpace(SS.SS0, object))
       || (!SS.hi && !Space.isInSpace(SS.SS1, object));
  }

  /**
   * @return The allocator to use when copying during the trace.
   */
  public final int getAllocator() throws InlinePragma {
    return SS.ALLOC_SS;
  }
}
