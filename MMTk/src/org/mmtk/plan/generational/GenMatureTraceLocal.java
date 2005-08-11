/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.generational;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.utility.deque.*;

import org.mmtk.vm.Assert;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

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
public abstract class GenMatureTraceLocal extends TraceLocal
  implements Uninterruptible {

  /****************************************************************************
   *
   * Instance fields.
   */
  private final WriteBuffer remset;
  private final AddressDeque traceRemset;
  private final AddressPairDeque arrayRemset;
  
  /**
   * Constructor
   */
  public GenMatureTraceLocal(Trace trace, GenLocal plan) {
    super(trace);
    this.remset = plan.remset;
    this.traceRemset = plan.traceRemset;
    this.arrayRemset = plan.arrayRemset;
  }

  /**
   * Is the specified object live?
   *
   * @param object The object.
   * @return True if the object is live.
   */
  public boolean isLive(ObjectReference object) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
    if (object.toAddress().GE(Gen.NURSERY_START)) {
      return Gen.nurserySpace.isLive(object);
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
  public ObjectReference traceObject(ObjectReference object)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
    if (object.toAddress().GE(Gen.NURSERY_START)) {
      return Gen.nurserySpace.traceObject(this, object);
    }
    return super.traceObject(object);
  }

  /**
   * Where do we send copied objects ?
   *
   * @return The allocator for copied objects
   */
  public final int getAllocator() throws InlinePragma {
    return Gen.ALLOC_MATURE;
  }
  
  /****************************************************************************
  *
  * Object processing and tracing
  */
 
  /**
   * Process any remembered set entries.
   */
  protected void flushRememberedSets() {
    logMessage(5, "clearing remset");
    remset.flushLocal();
    while (!traceRemset.isEmpty()) {
      traceRemset.pop();
    }
    logMessage(5, "clearing array remset");
    while (!arrayRemset.isEmpty()) {
      Address start = arrayRemset.pop1();
      Address guard = arrayRemset.pop2();
    }
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
    if (object.toAddress().GE(Gen.NURSERY_START))
      return false;
    return super.willNotMove(object);
  }

}
