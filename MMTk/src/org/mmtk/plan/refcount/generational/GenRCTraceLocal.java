/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.RefCountSpace;
import org.mmtk.policy.Space;
import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;

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
public final class GenRCTraceLocal extends TraceLocal
  implements Uninterruptible {

  /**
   * Constructor
   */
  public GenRCTraceLocal(Trace trace) {
    super(trace);
  }

  private final GenRCLocal local() {
    return (GenRCLocal)ActivePlan.local();
  }

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
   * Return true if <code>object</code> is a live object.
   *
   * @param object The object in question
   * @return True if <code>object</code> is a live object.
   */
  public boolean isLive(ObjectReference object) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
    if (Space.isInSpace(GenRC.NS, object))
      return GenRC.nurserySpace.isLive(object);
    else if (GenRC.isRCObject(object))
        return RefCountSpace.isLiveRC(object);
    else if (Space.isInSpace(GenRC.META, object))
    return false;
    else
      return true;
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
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
    if (Space.isInSpace(GenRC.NS, object))
      return !GenRC.nurserySpace.isLive(object);
    else if (GenRC.isRCObject(object))
        return RefCountSpace.isFinalizable(object);
    else if (!Space.isInSpace(GenRC.META, object))
      return true;
    else
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
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
    if (Space.isInSpace(GenRC.NS, object))
      return GenRC.nurserySpace.traceObject(this, object);
    else if (GenRC.isRCObject(object))
        RefCountSpace.clearFinalizer(object);
    return object;
  }

  public boolean willNotMove(ObjectReference object) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
    return !(Space.isInSpace(GenRC.NS, object));
  }

  public final ObjectReference precopyObject(ObjectReference object) {
    if (Space.isInSpace(GenRC.NS, object))
      return GenRC.nurserySpace.traceObject(this, object);
    return object;
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
  public final ObjectReference traceObject(ObjectReference object,
                                                  boolean root) {
    if (object.isNull()) return object;
    if (RefCountSpace.RC_SANITY_CHECK && root)
      local().rc.incSanityTraceRoot(object);
    if (Space.isInSpace(GenRC.NS, object)) {
      ObjectReference rtn = GenRC.nurserySpace.traceObject(this, object);
      // every incoming reference to the from-space object must inc the
      // ref count of forwarded (to-space) object...
      if (root) {
        if (RefCountSpace.INC_DEC_ROOT) {
          RefCountSpace.incRC(rtn);
          local().addToRootSet(rtn);
        } else if (RefCountSpace.setRoot(rtn)) {
          local().addToRootSet(rtn);
        }
      } else
        RefCountSpace.incRC(rtn);
      return rtn;
    } else if (GenRC.isRCObject(object)) {
      if (root)
        return GenRC.rcSpace.traceObject(this, object);
      else
        RefCountSpace.incRC(object);
    }
    // else this is not a rc heap pointer
    return object;
  }

  public final int getAllocator() throws InlinePragma {
    return GenRC.ALLOC_RC;
  }

}
