/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.refcount.fullheap;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.RefCountSpace;
import org.mmtk.policy.Space;
import org.mmtk.vm.ActivePlan;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class implments the core functionality for a transitive
 * closure over the heap graph.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class RCTraceLocal extends TraceLocal implements Uninterruptible {

  /**
   * Constructor
   */
  public RCTraceLocal(Trace trace) {
    super(trace);
  }

  private final RCLocal local() { return (RCLocal)ActivePlan.local(); }

  /**
   * Flush any remembered sets pertaining to the current collection.
   */
  protected void flushRememberedSets() {
    local().processModBufs();
  }
  
  /****************************************************************************
   *
   * Externally visible Object processing and tracing
   */

  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param object The object in question
   * @return True if <code>object</code> is a live object.
   */
  public boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (RC.isRCObject(object))
      return RefCountSpace.isLiveRC(object);
    if (Space.isInSpace(RC.META, object))
      return false;
    return true;
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.  We do not trace objects that are not
   * roots.
   *
   * @param object The object reference to be traced.  This is <i>NOT</i>
   * an interior pointer.
   * @param root True if this reference to <code>object</code> was held
   * in a root.
   * @return The possibly moved reference.
   */
  public ObjectReference traceObject(ObjectReference object, boolean root) {
    if (object.isNull() || !root)
      return object;
    if (RefCountSpace.RC_SANITY_CHECK)
      local().rc.incSanityTraceRoot(object);

    if (RC.isRCObject(object))
      return RC.rcSpace.traceObject(this, object);

    // else this is not an rc heap pointer
    return object;
  }

  public ObjectReference traceObject(ObjectReference object) {
    return object;
  }

  /**
   * Return true if an object is ready to move to the finalizable
   * queue, i.e. it has no regular references to it.
   *
   * @param object The object being queried.
   * @return <code>true</code> if the object has no regular references
   * to it.
   */
  public boolean readyToFinalize(ObjectReference object) {
    if (RC.isRCObject(object))
      return RefCountSpace.isFinalizable(object);
    if (!Space.isInSpace(RC.META, object))
      return true;
    return false;
  }

  /**
   * An object has just been moved to the finalizable queue.  No need
   * to forward because no copying is performed in this GC, but should
   * clear the finalizer bit of the object so that its reachability
   * now is soley determined by the finalizer queue from which it is
   * now reachable.
   *
   * @param object The object being queried.
   * @return The object (no copying is performed).
   */
  public ObjectReference retainForFinalize(ObjectReference object) {
    if (RC.isRCObject(object))
      RefCountSpace.clearFinalizer(object);
    return object;
  }
}
