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
package org.mmtk.plan.semispace.gctrace;

import org.mmtk.plan.Trace;
import org.mmtk.plan.semispace.*;
import org.mmtk.policy.Space;
import org.mmtk.utility.TraceGenerator;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This plan has been modified slightly to perform the processing necessary
 * for GC trace generation.  To maximize performance, it attempts to remain
 * as faithful as possible to semiSpace/Plan.java.
 *
 * The generated trace format is as follows:
 *    B 345678 12
 *      (Object 345678 was created in the boot image with a size of 12 bytes)
 *    U 59843 234 47298
 *      (Update object 59843 at the slot at offset 234 to refer to 47298)
 *    S 1233 12345
 *      (Update static slot 1233 to refer to 12345)
 *    T 4567 78924
 *      (The TIB of 4567 is set to refer to 78924)
 *    D 342789
 *      (Object 342789 became unreachable)
 *    A 6860 24 346648 3
 *      (Object 6860 was allocated, requiring 24 bytes, with fp 346648 on
 *        thread 3; this allocation has perfect knowledge)
 *    a 6884 24 346640 5
 *      (Object 6864 was allocated, requiring 24 bytes, with fp 346640 on
 * thread 5; this allocation DOES NOT have perfect knowledge)
 *    I 6860 24 346648 3
 *      (Object 6860 was allocated into immortal space, requiring 24 bytes,
 *        with fp 346648 on thread 3; this allocation has perfect knowledge)
 *    i 6884 24 346640 5
 *      (Object 6864 was allocated into immortal space, requiring 24 bytes,
 *        with fp 346640 on thread 5; this allocation DOES NOT have perfect
 *        knowledge)
 *    48954->[345]LObject;:blah()V:23   Ljava/lang/Foo;
 *      (Citation for: a) where the was allocated, fp of 48954,
 *         at the method with ID 345 -- or void Object.blah() -- and bytecode
 *         with offset 23; b) the object allocated is of type java.lang.Foo)
 *    D 342789 361460
 *      (Object 342789 became unreachable after 361460 was allocated)
 *
 * This class implements a simple semi-space collector. See the Jones
 * & Lins GC book, section 2.2 for an overview of the basic
 * algorithm. This implementation also includes a large object space
 * (LOS), and an uncollected "immortal" space.<p>
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  Instances of Plan map 1:1 to "kernel
 * threads" (aka CPUs).  Thus instance
 * methods allow fast, unsychronized access to Plan utilities such as
 * allocation and collection.  Each instance rests on static resources
 * (such as memory and virtual memory resources) which are "global"
 * and therefore "static" members of Plan.  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance proprties of this plan.
 */
@Uninterruptible
public final class GCTraceTraceLocal extends SSTraceLocal {

  /**
   * Constructor
   *
   * @param trace The global trace to use.
   */
  public GCTraceTraceLocal(Trace trace) {
    super(trace, false);
  }

  /****************************************************************************
   *
   * Object processing and tracing
   */

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies (such as those needed for trace generation)
   * and taking the appropriate actions.
   *
   * @param object The object reference to be traced.  In certain
   * cases, this should <i>NOT</i> be an interior pointer.
   * @return The possibly moved reference.
   */
  @Override
  @Inline
  public ObjectReference traceObject(ObjectReference object) {
    if (object.isNull()) return object;
    if (GCTrace.traceInducedGC) {
      /* We are performing a root scan following an allocation. */
      TraceGenerator.rootEnumerate(object);
      return object;
    } else if (GCTrace.deathScan) {
      /* We are performing the last scan before program termination. */
      TraceGenerator.propagateDeathTime(object);
      return object;
    } else {
      /* *gasp* We are actually performing garbage collection */
      return super.traceObject(object);
    }
  }

  /**
   * If the referenced object has moved, return the new location.
   *
   * Some copying collectors will need to override this method.
   *
   * @param object The object which may have been forwarded.
   * @return The new location of <code>object</code>.
   */
  @Override
  @Inline
  public ObjectReference getForwardedReference(ObjectReference object) {
    if (object.isNull()) return object;
    if (SS.hi && Space.isInSpace(SS.SS0, object)) {
      return SS.copySpace0.traceObject(this, object, GCTrace.ALLOC_SS);
    } else if (!SS.hi && Space.isInSpace(SS.SS1, object)) {
      return SS.copySpace1.traceObject(this, object, GCTrace.ALLOC_SS);
    }
    return object;
  }

  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param object The object in question
   * @return True if <code>obj</code> is a live object.
   */
  @Override
  public boolean isLive(ObjectReference object) {
      if (object.isNull()) return false;
      else if (GCTrace.traceInducedGC) return true;
      else return super.isLive(object);
  }

  /**
   * Return true if <code>obj</code> is a reachable object.
   *
   * @param object The object in question
   * @return True if <code>obj</code> is a reachable object;
   * unreachable objects may still be live, however
   */
  @Override
  public boolean isReachable(ObjectReference object) {
    if (GCTrace.finalDead) return false;
    else if (object.isNull()) return false;
    else {
      Space space = Space.getSpaceForObject(object);
      return space.isReachable(object);
    }
  }

  /**
   * Is this object guaranteed not to move during the collection.
   *
   * @param object The object to check.
   * @return True if the object is guaranteed not to move.
   */
  @Override
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    if (GCTrace.traceInducedGC) return true;
    else return super.willNotMoveInCurrentCollection(object);
  }
}
