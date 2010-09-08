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

import org.mmtk.plan.semispace.*;
import org.mmtk.policy.RawPageSpace;
import org.mmtk.utility.deque.SortTODSharedDeque;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.TraceGenerator;
import org.mmtk.utility.options.Options;

import org.mmtk.vm.VM;

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
@Uninterruptible public class GCTrace extends SS {

  /****************************************************************************
   *
   * Class variables
   */

  /* Spaces */
  public static final RawPageSpace traceSpace = new RawPageSpace("trace", DEFAULT_POLL_FREQUENCY, VMRequest.create());
  public static final int TRACE = traceSpace.getDescriptor();

  /* GC state */
  public static boolean lastGCWasTracing = false; // True when previous GC was for tracing
  public static boolean traceInducedGC = false; // True if trace triggered GC
  public static boolean deathScan = false;
  public static boolean finalDead = false;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public GCTrace() {
    SortTODSharedDeque workList = new SortTODSharedDeque("workList",traceSpace, 1);
    SortTODSharedDeque traceBuf = new SortTODSharedDeque("traceBuf",traceSpace, 1);
    workList.prepareNonBlocking();
    traceBuf.prepareNonBlocking();
    TraceGenerator.init(workList, traceBuf);
  }

  /**
   * The postBoot method is called by the runtime immediately after
   * command-line arguments are available.
   */
  @Interruptible
  public void postBoot() {
    Options.noFinalizer.setValue(true);
  }

  /**
   * The planExit method is called at RVM termination to allow the
   * trace process to finish.
   */
  @Interruptible
  public final void notifyExit(int value) {
    super.notifyExit(value);
    finalDead = true;
    traceInducedGC = false;
    deathScan = true;
    TraceGenerator.notifyExit(value);
  }

  /**
   * This method controls the triggering of a GC. It is called periodically
   * during allocation. Returns true to trigger a collection.
   *
   * @param spaceFull Space request failed, must recover pages within 'space'.
   * @return True if a collection is requested by the plan.
   */
  public final boolean collectionRequired(boolean spaceFull) {
    if (super.collectionRequired(spaceFull)) {
      traceInducedGC = false;
      return true;
    }
    return false;
  }

  /****************************************************************************
   *
   * Collection
   */

  public void collectionPhase(short phaseId) {
    if (phaseId == PREPARE) {
      lastGCWasTracing = traceInducedGC;
    }
    if (phaseId == RELEASE) {
      if (traceInducedGC) {
        /* Clean up following a trace-induced scan */
        deathScan = false;
      } else {
        /* Finish the collection by calculating the unreachable times */
        deathScan = true;
        TraceGenerator.postCollection();
        deathScan = false;
        /* Perform the semispace collections. */
        super.collectionPhase(phaseId);
      }
    } else if (!traceInducedGC ||
               (phaseId == INITIATE) ||
               (phaseId == PREPARE_STACKS) ||
               (phaseId == ROOTS) ||
               (phaseId == STACK_ROOTS) ||
               (phaseId == COMPLETE)) {
      /* Performing normal GC; sponge off of parent's work. */
      super.collectionPhase(phaseId);
    }
  }


  /****************************************************************************
   *
   * Space management
   */

  /**
   * @return Since trace induced collections are not called to free up memory,
   *         their failure to return memory isn't cause for concern.
   */
  public boolean isLastGCFull() {
    return !lastGCWasTracing;
  }

  /**
   * @return the active Plan as a GCTrace
   */
  public static GCTrace global() {
    return ((GCTrace) VM.activePlan.global());
  }
}
