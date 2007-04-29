/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan;

import org.mmtk.policy.Space;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.scan.Scan;
import org.mmtk.utility.options.Options;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class and its global counterpart implement the core
 * functionality for a transitive closure over the heap graph. This class
 * specifically implements the unsynchronized thread-local component
 * (ie the 'fast path') of the trace mechanism.<p>
 * 
 * @see org.mmtk.plan.Plan
 * @see org.mmtk.plan.Trace
 * 
 *
 * @author Perry Cheng
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 */
@Uninterruptible public abstract class TraceLocal extends TraceStep 
  implements Constants {
  /****************************************************************************
   * 
   * Instance variables
   */
  // gray objects
  protected final ObjectReferenceDeque values;
  // root locs of white objs
  protected final AddressDeque rootLocations;
  // interior root locations
  protected final AddressPairDeque interiorRootLocations;

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   * 
   * @param trace The global trace class to use.
   */
  public TraceLocal(Trace trace) {
    values = new ObjectReferenceDeque("value", trace.valuePool);
    trace.valuePool.newConsumer();
    rootLocations = new AddressDeque("rootLoc", trace.rootLocationPool);
    trace.rootLocationPool.newConsumer();
    interiorRootLocations = new AddressPairDeque(trace.interiorRootPool);
    trace.interiorRootPool.newConsumer();
  }

  /****************************************************************************
   * 
   * Internally visible Object processing and tracing
   */

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param objLoc The location containing the object reference to be
   * traced.  The object reference is <i>NOT</i> an interior pointer.
   * @param root True if <code>objLoc</code> is within a root.
   */
  @Inline
  public final void traceObjectLocation(Address objLoc, boolean root) { 
    ObjectReference object = objLoc.loadObjectReference();
    ObjectReference newObject = traceObject(object, root);
    objLoc.store(newObject);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.  This reference is presumed <i>not</i>
   * to be from a root.
   *
   * @param objLoc The location containing the object reference to be
   * traced.  The object reference is <i>NOT</i> an interior pointer.
   */
  @Inline
  public final void traceObjectLocation(Address objLoc) { 
    traceObjectLocation(objLoc, false);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param object The object reference to be traced.
   * @param interiorRef The interior reference inside obj that must be traced.
   * @param root True if the reference to <code>obj</code> was held in a root.
   * @return The possibly moved interior reference.
   */
  public final Address traceInteriorReference(ObjectReference object,
    Address interiorRef,
    boolean root) {
    Offset offset = interiorRef.diff(object.toAddress());
    ObjectReference newObject = traceObject(object, root);
    if (VM.VERIFY_ASSERTIONS) {
      if (offset.sLT(Offset.zero()) ||
          offset.sGT(Offset.fromIntSignExtend(1<<24))) {
        // There is probably no object this large
        Log.writeln("ERROR: Suspiciously large delta to interior pointer");
        Log.write("       object base = "); Log.writeln(object);
        Log.write("       interior reference = "); Log.writeln(interiorRef);
        Log.write("       delta = "); Log.writeln(offset);
        VM.assertions._assert(false);
      }
    }
    return newObject.toAddress().plus(offset);
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
  protected void scanObject(ObjectReference object) { 
    Scan.scanObject(this, object);
  }


  /****************************************************************************
   * 
   * Externally visible Object processing and tracing
   */

  /**
   * Add a gray object
   * 
   * @param object The object to be enqueued
   */
  @Inline
  public final void enqueue(ObjectReference object) { 
    values.push(object);
  }

  /**
   * Report a new root location for the trace.
   * 
   * @param location The slot of the root.
   */
  @Inline
  public final void addRootLocation(Address location) { 
    rootLocations.push(location);
  }

  /**
   * Report a new interior root location for the trace.
   * 
   * @param object The object the location resides in.
   * @param location The slot of the root.
   */
  @Inline
  public final void addInteriorRootLocation(ObjectReference object,
                                            Address location) { 
    interiorRootLocations.push(object.toAddress(), location);
  }

  /**
   * Flush the local buffers of the root deques.
   */
  public final void flushRoots() {
    rootLocations.flushLocal();
    interiorRootLocations.flushLocal();
  }

  /**
   * Is the specified object live?
   * 
   * @param object The object.
   * @return True if the object is live.
   */
  @Inline
  public boolean isLive(ObjectReference object) { 
    Space space = Space.getSpaceForObject(object);
    if (space == Plan.loSpace)
      return Plan.loSpace.isLive(object);
    else if (space == Plan.ploSpace)
      return Plan.ploSpace.isLive(object);
    else if (space == null) {
      if (VM.VERIFY_ASSERTIONS) {
        Log.write("space failure: "); Log.writeln(object);
      }
    }
    return true;
  }

  /**
   * Is the specified object reachable? Used for GC Trace
   * 
   * @param object The object.
   * @return True if the object is live.
   */
  @Inline
  public boolean isReachable(ObjectReference object) { 
    return Space.getSpaceForObject(object).isReachable(object);
  }

  /**
   * Is the specified referent of a reference type object live?
   * 
   * @param object The object.
   * @return True if the reference object is live.
   */
  @Inline
  public boolean isReferentLive(ObjectReference object) { 
    return isLive(object);
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
    if (Space.isInSpace(Plan.VM_SPACE, object))
      return (Plan.SCAN_BOOT_IMAGE) ? object : Plan.vmSpace.traceObject(this, object);
    if (Space.isInSpace(Plan.IMMORTAL, object))
      return Plan.immortalSpace.traceObject(this, object);
    if (Space.isInSpace(Plan.LOS, object))
      return Plan.loSpace.traceObject(this, object);
    if (Space.isInSpace(Plan.PLOS, object)) 
      return Plan.ploSpace.traceObject(this, object);
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(false, "No special case for space in traceObject");
    return ObjectReference.nullReference();
  }


  /**
   * Ensure that this object will not move for the rest of the GC.
   * 
   * @param object The object that must not move
   * @return The new object, guaranteed stable for the rest of the GC.
   */
  @Inline
  public ObjectReference precopyObject(ObjectReference object) { 
    return traceObject(object);
  }

  /**
   * This method traces an object with knowledge of the fact that object
   * is a root or not. In simple collectors the fact it is a root is not
   * important so this is the default implementation given here.
   * 
   * @param object The object to be traced.
   * @return The new reference to the same object instance.
   */
  @Inline
  public ObjectReference traceObject(ObjectReference object, boolean root) { 
    return traceObject(object);
  }

  /**
   * Ensure that the referenced object will not move from this point through
   * to the end of the collection. This can involve forwarding the object
   * if necessary.
   * 
   * <i>Non-copying collectors do nothing, copying collectors must
   * override this method in each of their trace classes.</i>
   * 
   * @param object The object that must not move during the collection.
   * @return True If the object will not move during collection
   */
  public boolean willNotMove(ObjectReference object) {
    if (!VM.activePlan.constraints().movesObjects())
      return true;
    if (Space.isInSpace(Plan.LOS, object))
      return true;
    if (Space.isInSpace(Plan.IMMORTAL, object))
      return true;
    if (Space.isInSpace(Plan.VM_SPACE, object))
      return true;
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(false, "willNotMove not defined properly in subclass");
    return false;
  }

  /**
   * If a Finalizable object has moved, return the new location.
   * 
   * @param object The object which may have been forwarded.
   * @return The new location of <code>object</code>.
   */
  public ObjectReference getForwardedFinalizable(ObjectReference object) {
    return getForwardedReference(object);
  }

  /**
   * If the reference object (from a Reference Type) has object has moved,
   * return the new location.
   * 
   * @param object The object which may have been forwarded.
   * @return The new location of <code>object</code>.
   */
  @Inline
  public ObjectReference getForwardedReferent(ObjectReference object) { 
    return getForwardedReference(object);
  }

  /**
   * If the Reference Type object has moved, return the new location.
   * 
   * @param object The object which may have been forwarded.
   * @return The new location of <code>object</code>.
   */
  @Inline
  public ObjectReference getForwardedReferenceType(ObjectReference object) { 
    return getForwardedReference(object);
  }

  /**
   * If the referenced object has moved, return the new location.
   * 
   * Some copying collectors will need to override this method.
   * 
   * @param object The object which may have been forwarded.
   * @return The new location of <code>object</code>.
   */
  @Inline
  public ObjectReference getForwardedReference(ObjectReference object) { 
    return traceObject(object);
  }

  /**
   * Make alive a referent object that is known not to be live
   * (isLive is false). This is used by the ReferenceProcessor.
   * 
   * <i>For many collectors these semantics relfect those of
   * <code>traceObject</code>, which is implemented here.  Other
   * collectors must override this method.</i>
   * 
   * @param object The object which is to be made alive.
   * @return The possibly forwarded address of the object.
   */
  @Inline
  public ObjectReference retainReferent(ObjectReference object) { 
    return traceObject(object);
  }

  /**
   * An object is unreachable and is about to be added to the
   * finalizable queue.  The collector must ensure the object is not
   * collected (despite being otherwise unreachable), and should
   * return its forwarded address if keeping the object alive involves
   * forwarding. This is only ever called once for an object.<p>
   * 
   * <i>For many collectors these semantics relfect those of
   * <code>traceObject</code>, which is implemented here.  Other
   * collectors must override this method.</i>
   * 
   * @param object The object which may have been forwarded.
   * @return The forwarded value for <code>object</code>.  <i>In this
   * case return <code>object</code>, copying collectors must override
   *         this method.
   */
  public ObjectReference retainForFinalize(ObjectReference object) {
    return traceObject(object);
  }

  /**
   * Return true if an object is ready to move to the finalizable
   * queue, i.e. it has no regular references to it.  This method may
   * (and in some cases is) be overridden by subclasses. If this method
   * returns true then it can be assumed that retainForFinalize will be
   * called during the current collection.
   *
   * <i>For many collectors these semantics relfect those of
   * <code>isLive</code>, which is implemented here.  Other
   * collectors must override this method.</i>
   *
   * @param object The object being queried.
   * @return <code>true</code> if the object has no regular references
   * to it.
   */
  public boolean readyToFinalize(ObjectReference object) {
    return !isLive(object);
  }

  /****************************************************************************
   * 
   * Collection
   * 
   * Important notes:
   *   . Global actions are executed by only one thread
   *   . Thread-local actions are executed by all threads
   *   . The following order is guaranteed by BasePlan, with each
   *     separated by a synchronization barrier.
   *      1. globalPrepare()
   *      2. threadLocalPrepare()
   *      3. threadLocalRelease()
   *      4. globalRelease()
   */
  public void prepare() {
    // Nothing to do
  }

  public void release() {
    values.reset();
    rootLocations.reset();
    interiorRootLocations.reset();
  }

  /**
   * Process all GC work.  This method iterates until all work queues
   * are empty.
   */
  @Inline
  public void startTrace() { 
    logMessage(4, "Working on GC in parallel");
    logMessage(5, "processing root locations");
    while (!rootLocations.isEmpty()) {
      Address loc = rootLocations.pop();
      traceObjectLocation(loc, true);
    }
    logMessage(5, "processing interior root locations");
    while (!interiorRootLocations.isEmpty()) {
      ObjectReference obj = interiorRootLocations.pop1().toObjectReference();
      Address interiorLoc = interiorRootLocations.pop2();
      Address interior = interiorLoc.loadAddress();
      Address newInterior = traceInteriorReference(obj, interior, true);
      interiorLoc.store(newInterior);
    }
    completeTrace();
  }

  /**
   * Finishing processing all GC work.  This method iterates until all work queues
   * are empty.
   */
  @Inline
  public void completeTrace() { 
    logMessage(4, "Continuing GC in parallel");
    logMessage(5, "processing gray objects");
    assertMutatorRemsetsFlushed();
    do {
      while (!values.isEmpty()) {
        ObjectReference v = values.pop();
        scanObject(v);
      }
      processRememberedSets();
    } while (!values.isEmpty());
    assertMutatorRemsetsFlushed();
  }

  /**
   * Flush any remembered sets pertaining to the current collection.
   * Non-generational collectors do nothing.
   */

  protected void processRememberedSets() {}

  /**
   * Assert that the remsets have been flushed.  This is critical to 
   * correctness.  We need to maintain the invariant that remset entries
   * do not accrue during GC.  If the host JVM generates barrier entires
   * it is its own responsibility to ensure that they are flushed before
   * returning to MMTk.
   */
  private void assertMutatorRemsetsFlushed() {
    if (VM.VERIFY_ASSERTIONS) {
      for (int m = 0; m < VM.activePlan.mutatorCount(); m++) {
        VM.activePlan.mutator(m).assertRemsetsFlushed();
      }
    }
  }
  /**
   * This method logs a message with preprended thread id, if the
   * verbosity level is greater or equal to the passed level.
   * 
   * @param minVerbose The required verbosity level
   * @param message The message to display
   */
  @Inline
  protected final void logMessage(int minVerbose, String message) { 
    if (Options.verbose.getValue() >= minVerbose) {
      Log.prependThreadId();
      Log.write("    ");
      Log.writeln(message);
    }
  }

  /**
   * Copying traces should override this stub
   * 
   * @return The allocator id to use when copying.
   */
  public int getAllocator() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return -1;
  }

  /**
   * Given a slot (ie the address of an ObjectReference), ensure that the
   * referent will not move for the rest of the GC. This is achieved by 
   * calling the precopyObject method. 
   * 
   * @param slot The slot to check
   */
  @Inline
  public final void precopyObjectLocation(Address slot) { 
    ObjectReference child = slot.loadObjectReference();
    if (!child.isNull()) {
      child = precopyObject(child);
      slot.store(child);
    }
  }
}
