/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.generational.marksweep;

import org.mmtk.plan.generational.GenLocal;
import org.mmtk.plan.generational.GenMatureTraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.Space;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This abstract class implments the core functionality for a transitive
 * closure over the heap graph, specifically in a Generational Mark-Sweep
 * collector.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public final class GenMSMatureTraceLocal extends GenMatureTraceLocal
  implements Uninterruptible {

  /**
   * Constructor
   */
  public GenMSMatureTraceLocal(Trace global, GenLocal plan) {
    super(global, plan);
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
  public final ObjectReference traceObject(ObjectReference object)
    throws InlinePragma {
    if (object.isNull()) return object;

    if (Space.isInSpace(GenMS.MS, object)) {
      return GenMS.msSpace.traceObject(this, object);
    }
    return super.traceObject(object);
  }

  /**
   * Is the specified object live?
   *
   * @param object The object.
   * @return True if the object is live.
   */
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (Space.isInSpace(GenMS.MS, object)) {
      return GenMS.msSpace.isLive(object);
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
   * collection.
   */
  public boolean willNotMove(ObjectReference object) {
    if (Space.isInSpace(GenMS.MS,object)) {
      return true;
    }
    return super.willNotMove(object);
  }
}
