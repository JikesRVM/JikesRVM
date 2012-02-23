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
package org.mmtk.plan.refcount;

import org.mmtk.plan.Phase;
import org.mmtk.plan.StopTheWorld;
import org.mmtk.plan.Trace;
import org.mmtk.plan.refcount.backuptrace.BTFreeLargeObjectSweeper;
import org.mmtk.plan.refcount.backuptrace.BTScanLargeObjectSweeper;
import org.mmtk.plan.refcount.backuptrace.BTSweeper;
import org.mmtk.policy.ExplicitFreeListSpace;
import org.mmtk.policy.ExplicitLargeObjectSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.utility.alloc.LinearScan;
import org.mmtk.utility.deque.SharedDeque;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.sanitychecker.SanityChecker;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements the global state of a a simple reference counting collector.
 */
@Uninterruptible
public class RCBase extends StopTheWorld {
  public static final short PROCESS_OLDROOTBUFFER  = Phase.createSimple("old-root");
  public static final short PROCESS_NEWROOTBUFFER  = Phase.createSimple("new-root");
  public static final short PROCESS_MODBUFFER      = Phase.createSimple("mods");
  public static final short PROCESS_DECBUFFER      = Phase.createSimple("decs");

  /** Is cycle collection enabled? */
  public static final boolean CC_ENABLED           = true;
  /** Force full cycle collection at each GC? */
  public static final boolean CC_FORCE_FULL        = false;
  /** Use backup tracing for cycle collection (currently the only option) */
  public static final boolean CC_BACKUP_TRACE      = true;

  public static boolean performCycleCollection;
  public static final short BT_CLOSURE             = Phase.createSimple("closure-bt");

  // CHECKSTYLE:OFF

  /**
   * Reference counting specific collection steps.
   */
  protected static final short refCountCollectionPhase = Phase.createComplex("release", null,
      Phase.scheduleGlobal     (PROCESS_OLDROOTBUFFER),
      Phase.scheduleCollector  (PROCESS_OLDROOTBUFFER),
      Phase.scheduleGlobal     (PROCESS_NEWROOTBUFFER),
      Phase.scheduleCollector  (PROCESS_NEWROOTBUFFER),
      Phase.scheduleMutator    (PROCESS_DECBUFFER),
      Phase.scheduleGlobal     (PROCESS_DECBUFFER),
      Phase.scheduleCollector  (PROCESS_DECBUFFER),
      Phase.scheduleGlobal     (BT_CLOSURE),
      Phase.scheduleCollector  (BT_CLOSURE));

  /**
   * Perform the initial determination of liveness from the roots.
   */
  protected static final short rootClosurePhase = Phase.createComplex("initial-closure", null,
      Phase.scheduleMutator    (PREPARE),
      Phase.scheduleGlobal     (PREPARE),
      Phase.scheduleCollector  (PREPARE),
      Phase.scheduleComplex    (prepareStacks),
      Phase.scheduleCollector  (STACK_ROOTS),
      Phase.scheduleCollector  (ROOTS),
      Phase.scheduleGlobal     (ROOTS),
      Phase.scheduleMutator    (PROCESS_MODBUFFER),
      Phase.scheduleGlobal     (PROCESS_MODBUFFER),
      Phase.scheduleCollector  (PROCESS_MODBUFFER),
      Phase.scheduleGlobal     (CLOSURE),
      Phase.scheduleCollector  (CLOSURE));

  /**
   * This is the phase that is executed to perform a collection.
   */
  public short collection = Phase.createComplex("collection", null,
      Phase.scheduleComplex(initPhase),
      Phase.scheduleComplex(rootClosurePhase),
      Phase.scheduleComplex(refCountCollectionPhase),
      Phase.scheduleComplex(completeClosurePhase),
      Phase.scheduleComplex(finishPhase));

  // CHECKSTYLE:ON

  /*****************************************************************************
   *
   * Class fields
   */
  public static final ExplicitFreeListSpace rcSpace = new ExplicitFreeListSpace("rc", VMRequest.create());
  public static final ExplicitLargeObjectSpace rcloSpace = new ExplicitLargeObjectSpace("rclos", VMRequest.create());

  public static final int REF_COUNT = rcSpace.getDescriptor();
  public static final int REF_COUNT_LOS = rcloSpace.getDescriptor();

  public final SharedDeque modPool = new SharedDeque("mod", metaDataSpace, 1);
  public final SharedDeque decPool = new SharedDeque("dec", metaDataSpace, 1);
  public final SharedDeque newRootPool = new SharedDeque("newRoot", metaDataSpace, 1);
  public final SharedDeque oldRootPool = new SharedDeque("oldRoot", metaDataSpace, 1);

  /*****************************************************************************
   *
   * Instance fields
   */
  public final Trace rootTrace;
  public final Trace backupTrace;
  private final BTSweeper rcSweeper;
  private final BTScanLargeObjectSweeper loScanSweeper;
  private final BTFreeLargeObjectSweeper loFreeSweeper;

  /**
   * Constructor
   */
  public RCBase() {
    Options.noReferenceTypes.setDefaultValue(true);
    Options.noFinalizer.setDefaultValue(true);

    rootTrace = new Trace(metaDataSpace);
    backupTrace = new Trace(metaDataSpace);
    rcSweeper = new BTSweeper();
    loScanSweeper = new BTScanLargeObjectSweeper();
    loFreeSweeper = new BTFreeLargeObjectSweeper();
  }

  /**
   * The processOptions method is called by the runtime immediately after
   * command-line arguments are available. Allocation must be supported
   * prior to this point because the runtime infrastructure may require
   * allocation in order to parse the command line arguments.  For this
   * reason all plans should operate gracefully on the default minimum
   * heap size until the point that processOptions is called.
   */
  @Interruptible
  public void processOptions() {
    super.processOptions();

    if (!Options.noReferenceTypes.getValue()) {
      VM.assertions.fail("Reference Types are not supported by RC");
    }
    if (!Options.noFinalizer.getValue()) {
      VM.assertions.fail("Finalizers are not supported by RC");
    }
  }

  /*****************************************************************************
   *
   * Collection
   */

  public static final boolean isRCObject(ObjectReference object) {
    return !object.isNull() && !Space.isInSpace(VM_SPACE, object);
  }

  /**
   * @return Whether last GC is a full GC.
   */
  public boolean lastCollectionFullHeap() {
    return performCycleCollection;
  }

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase
   */
  public void collectionPhase(short phaseId) {
    if (phaseId == SET_COLLECTION_KIND) {
      super.collectionPhase(phaseId);
      if (CC_ENABLED) {
        performCycleCollection = (collectionAttempt > 1) || emergencyCollection || CC_FORCE_FULL;
        if (performCycleCollection && Options.verbose.getValue() > 0) Log.write(" [CC] ");
      }
      return;
    }

    if (phaseId == PREPARE) {
      VM.finalizableProcessor.clear();
      VM.weakReferences.clear();
      VM.softReferences.clear();
      VM.phantomReferences.clear();
      rootTrace.prepare();
      rcSpace.prepare();
      if (CC_BACKUP_TRACE && performCycleCollection) {
        backupTrace.prepare();
      }
      return;
    }

    if (phaseId == CLOSURE) {
      rootTrace.prepare();
      return;
    }

    if (phaseId == BT_CLOSURE) {
      if (CC_BACKUP_TRACE && performCycleCollection) {
        backupTrace.prepare();
      }
      return;
    }

    if (phaseId == PROCESS_OLDROOTBUFFER) {
      oldRootPool.prepare();
      return;
    }

    if (phaseId == PROCESS_NEWROOTBUFFER) {
      newRootPool.prepare();
      return;
    }


    if (phaseId == PROCESS_MODBUFFER) {
      modPool.prepare();
      return;
    }

    if (phaseId == PROCESS_DECBUFFER) {
      decPool.prepare();
      return;
    }

    if (phaseId == RELEASE) {
      rootTrace.release();
      if (CC_BACKUP_TRACE && performCycleCollection) {
        backupTrace.release();
        rcSpace.sweepCells(rcSweeper);
        rcloSpace.sweep(loScanSweeper);
        rcloSpace.sweep(loFreeSweeper);
      } else {
        rcSpace.release();
      }
      return;
    }

    super.collectionPhase(phaseId);
  }

  /*****************************************************************************
   *
   * Accounting
   */

  /**
   * Return the number of pages used given the pending
   * allocation.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  public int getPagesUsed() {
    return (rcSpace.reservedPages() + rcloSpace.reservedPages() + super.getPagesUsed());
  }

  /**
   * Perform a linear scan across all objects in the heap to check for leaks.
   */
  public void sanityLinearScan(LinearScan scan) {
    //rcSpace.linearScan(scan);
  }

  /**
   * Return the expected reference count. For non-reference counting
   * collectors this becomes a true/false relationship.
   *
   * @param object The object to check.
   * @param sanityRootRC The number of root references to the object.
   * @return The expected (root excluded) reference count.
   */
  public int sanityExpectedRC(ObjectReference object, int sanityRootRC) {
    if (RCBase.isRCObject(object)) {
      int fullRC = RCHeader.getRC(object);
      if (fullRC == 0) {
        return SanityChecker.DEAD;
      }
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(fullRC >= sanityRootRC);
      return fullRC - sanityRootRC;
    }
    return SanityChecker.ALIVE;
  }

  /**
   * Register specialized methods.
   */
  @Interruptible
  protected void registerSpecializedMethods() {
    super.registerSpecializedMethods();
  }
}
