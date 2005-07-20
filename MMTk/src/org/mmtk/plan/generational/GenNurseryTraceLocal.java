/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.generational;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;
import org.mmtk.utility.deque.*;
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
public final class GenNurseryTraceLocal extends TraceLocal
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
  public GenNurseryTraceLocal(Trace trace, GenLocal plan) {
    super(trace);
    this.remset = plan.remset;
    this.traceRemset = plan.traceRemset;
    this.arrayRemset = plan.arrayRemset;
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
    if (!object.isNull() && object.toAddress().GE(Gen.NURSERY_START)) {
      return Gen.nurserySpace.traceObject(this, object);
    }
    return object;
  }

  /**
   * Process any remembered set entries.
   */
  protected void flushRememberedSets() throws InlinePragma {
    logMessage(5, "processing remset");
    remset.flushLocal();
    while (!traceRemset.isEmpty()) {
      Address loc = traceRemset.pop();
      traceObjectLocation(loc, false);
    }
    logMessage(5, "processing array remset");
    while (!arrayRemset.isEmpty()) {
      Address start = arrayRemset.pop1();
      Address guard = arrayRemset.pop2();
      while (start.LT(guard)) {
        traceObjectLocation(start, false);
        start = start.add(BYTES_IN_ADDRESS);
      }
    }
    if (Assert.VERIFY_ASSERTIONS) {
      Assert._assert(traceRemset.isEmpty());
      Assert._assert(arrayRemset.isEmpty());
    }
  }

  /**
   * @return The allocator to use when copying objects during this trace.
   */
  public final int getAllocator() throws InlinePragma {
    return Gen.ALLOC_MATURE;
  }
  
  /**
   * Will the object move from now on during the collection.
   * 
   * @param object The object to query.
   * @return True if the object is guaranteed not to move.
   */
  public boolean willNotMove(ObjectReference object) {
    if (object.isNull()) return false;
    if (object.toAddress().GE(Gen.NURSERY_START)) {
      return false;
    }
    return true;
  }

}
