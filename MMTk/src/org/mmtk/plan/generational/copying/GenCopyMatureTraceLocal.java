package org.mmtk.plan.generational.copying;

import org.mmtk.plan.generational.GenCollector;
import org.mmtk.plan.generational.GenMatureTraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.policy.Space;

import org.mmtk.vm.Assert;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class implments the core functionality for a transitive
 * closure over the heap graph, specifically in a Generational copying
 * collector. 
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class GenCopyMatureTraceLocal extends GenMatureTraceLocal implements Uninterruptible {

  /**
   * Constructor
   */
  public GenCopyMatureTraceLocal(Trace global, GenCollector plan) {
    super(global, plan);
  }

  private static final GenCopy global() {
    return (GenCopy) VM.activePlan.global();
  }

  /**
   * Trace a reference into the mature space during GC. This involves
   * determining whether the instance is in from space, and if so,
   * calling the <code>traceObject</code> method of the Copy
   * collector.
   * 
   * @param object The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  public final ObjectReference traceObject(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(global().traceFullHeap());
    if (object.isNull()) return object;

    if (Space.isInSpace(GenCopy.MS0, object))
      return GenCopy.matureSpace0.traceObject(this, object);
    if (Space.isInSpace(GenCopy.MS1, object))
      return GenCopy.matureSpace1.traceObject(this, object);
    return super.traceObject(object);
  }

  /**
   * Return true if <code>obj</code> is a live object.
   * 
   * @param object The object in question
   * @return True if <code>obj</code> is a live object.
   */
  public final boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (Space.isInSpace(GenCopy.MS0, object))
      return GenCopy.matureSpace0.isLive(object);
    if (Space.isInSpace(GenCopy.MS1, object))
      return GenCopy.matureSpace1.isLive(object);
    return super.isLive(object);
  }

  /****************************************************************************
   * 
   * Object processing and tracing
   */


  /**
   * Return true if this object is guaranteed not to move during this
   * collection (i.e. this object is defintely not an unforwarded
   * object).
   * 
   * @param object
   * @return True if this object is guaranteed not to move during this
   *         collection.
   */
  public boolean willNotMove(ObjectReference object) {
    if (Space.isInSpace(GenCopy.toSpaceDesc(), object)) {
      return true;
    }
    if (Space.isInSpace(GenCopy.fromSpaceDesc(), object)) {
      return false;
    }
    return super.willNotMove(object);
  }
}
