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
package org.mmtk.plan;

import org.mmtk.policy.Space;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;
import org.mmtk.utility.deque.*;
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
 */
@Uninterruptible
public abstract class TraceLocal extends TransitiveClosure implements Constants {
  /****************************************************************************
   *
   * Instance variables
   */
  /* gray object */
  protected final ObjectReferenceDeque values;
  /* delayed root slots */
  protected final AddressDeque rootLocations;

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
    this(-1, trace);
  }

  /**
   * Constructor
   *
   * @param specializedScan The specialized scan id.
   * @param trace The global trace class to use.
   */
  public TraceLocal(int specializedScan, Trace trace) {
    super(specializedScan);
    values = new ObjectReferenceDeque("value", trace.valuePool);
    rootLocations = new AddressDeque("roots", trace.rootLocationPool);
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
   * @param source The source of the reference.
   * @param slot The location containing the object reference to be
   *        traced.  The object reference is <i>NOT</i> an interior pointer.
   */
  @Inline
  public final void processEdge(ObjectReference source, Address slot) {
    ObjectReference object = VM.activePlan.global().loadObjectReference(slot);
    ObjectReference newObject = traceObject(object, false);
    VM.activePlan.global().storeObjectReference(slot, newObject);
  }

  /**
   * Report a root edge to be processed during GC. As the given reference
   * may theoretically point to an object required during root scanning,
   * the caller has requested processing be delayed.
   *
   * NOTE: delayed roots are assumed to be raw.
   *
   * @param slot The location containing the object reference to be
   * traced.  The object reference is <i>NOT</i> an interior pointer.
   */
  @Inline
  public final void reportDelayedRootEdge(Address slot) {
    rootLocations.push(slot);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param slot The location containing the object reference to be
   * traced.  The object reference is <i>NOT</i> an interior pointer.
   * @param untraced True if <code>objLoc</code> is an untraced root.
   */
  @Inline
  public final void processRootEdge(Address slot, boolean untraced) {
    ObjectReference object;
    if (untraced) object = slot.loadObjectReference();
    else     object = VM.activePlan.global().loadObjectReference(slot);
    ObjectReference newObject = traceObject(object, true);
    if (untraced) slot.store(newObject);
    else     VM.activePlan.global().storeObjectReference(slot, newObject);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param target The object the interior edge points within.
   * @param slot The location of the interior edge.
   * @param root True if this is a root edge.
   */
  public final void processInteriorEdge(ObjectReference target, Address slot, boolean root) {
    Address interiorRef = slot.loadAddress();
    Offset offset = interiorRef.diff(target.toAddress());
    ObjectReference newTarget = traceObject(target, root);
    if (VM.VERIFY_ASSERTIONS) {
      if (offset.sLT(Offset.zero()) || offset.sGT(Offset.fromIntSignExtend(1<<24))) {
        // There is probably no object this large
        Log.writeln("ERROR: Suspiciously large delta to interior pointer");
        Log.write("       object base = "); Log.writeln(target);
        Log.write("       interior reference = "); Log.writeln(interiorRef);
        Log.write("       delta = "); Log.writeln(offset);
        VM.assertions._assert(false);
      }
    }
    slot.store(newTarget.toAddress().plus(offset));
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
    if (specializedScan >= 0) {
      VM.scanning.specializedScanObject(specializedScan, this, object);
    } else {
      VM.scanning.scanObject(this, object);
    }
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
  public final void processNode(ObjectReference object) {
    values.push(object);
  }

  /**
   * Flush the local buffers of all deques.
   */
  public final void flush() {
    values.flushLocal();
    rootLocations.flushLocal();
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
    else if (space == Plan.nonMovingSpace)
      return Plan.nonMovingSpace.isLive(object);
    else if (Plan.USE_CODE_SPACE && space == Plan.smallCodeSpace)
      return Plan.smallCodeSpace.isLive(object);
    else if (Plan.USE_CODE_SPACE && space == Plan.largeCodeSpace)
      return Plan.largeCodeSpace.isLive(object);
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
    if (Space.isInSpace(Plan.NON_MOVING, object))
      return Plan.nonMovingSpace.traceObject(this, object);
    if (Plan.USE_CODE_SPACE && Space.isInSpace(Plan.SMALL_CODE, object))
      return Plan.smallCodeSpace.traceObject(this, object);
    if (Plan.USE_CODE_SPACE && Space.isInSpace(Plan.LARGE_CODE, object))
      return Plan.largeCodeSpace.traceObject(this, object);
    if (VM.VERIFY_ASSERTIONS) {
      Log.write("Failing object => "); Log.writeln(object);
      Space.printVMMap();
      VM.assertions._assert(false, "No special case for space in traceObject");
    }
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
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    if (!VM.activePlan.constraints().movesObjects())
      return true;
    if (Space.isInSpace(Plan.LOS, object))
      return true;
    if (Space.isInSpace(Plan.IMMORTAL, object))
      return true;
    if (Space.isInSpace(Plan.VM_SPACE, object))
      return true;
    if (Space.isInSpace(Plan.NON_MOVING, object))
      return true;
    if (Plan.USE_CODE_SPACE && Space.isInSpace(Plan.SMALL_CODE, object))
      return true;
    if (Plan.USE_CODE_SPACE && Space.isInSpace(Plan.LARGE_CODE, object))
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
  }

  /**
   * Process any roots for which processing was delayed.
   */
  @Inline
  public void processRoots() {
    logMessage(5, "processing delayed root objects");
    while (!rootLocations.isEmpty()) {
      processRootEdge(rootLocations.pop(), true);
    }
  }

  /**
   * Finishing processing all GC work.  This method iterates until all work queues
   * are empty.
   */
  @Inline
  public void completeTrace() {
    logMessage(4, "Processing GC in parallel");
    if (!rootLocations.isEmpty()) {
      processRoots();
    }
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
   * Process GC work until either complete or workLimit
   * units of work are completed.
   *
   * @param workLimit The maximum units of work to perform.
   * @return True if all work was completed within workLimit.
   */
  @Inline
  public boolean incrementalTrace(int workLimit) {
    logMessage(4, "Continuing GC in parallel (incremental)");
    logMessage(5, "processing gray objects");
    int units = 0;
    do {
      while (!values.isEmpty() && units < workLimit) {
        ObjectReference v = values.pop();
        scanObject(v);
        units++;
      }
    } while (!values.isEmpty() && units < workLimit);
    return values.isEmpty();
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
    /* FIXME: PNT
    if (VM.VERIFY_ASSERTIONS) {
      for (int m = 0; m < VM.activePlan.mutatorCount(); m++) {
        VM.activePlan.mutator(m).assertRemsetsFlushed();
      }
    }
    */
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
   * Given a slot (ie the address of an ObjectReference), ensure that the
   * referent will not move for the rest of the GC. This is achieved by
   * calling the precopyObject method.
   *
   * @param slot The slot to check
   * @param untraced Is this is an untraced reference?
   */
  @Inline
  public final void processPrecopyEdge(Address slot, boolean untraced) {
    ObjectReference child;
    if (untraced) child = slot.loadObjectReference();
    else          child = VM.activePlan.global().loadObjectReference(slot);
    if (!child.isNull()) {
      child = precopyObject(child);
      if (untraced) slot.store(child);
      else          VM.activePlan.global().storeObjectReference(slot, child);
    }
  }
}
