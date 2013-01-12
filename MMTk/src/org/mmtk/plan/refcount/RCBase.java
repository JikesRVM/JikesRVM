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
 * This class implements the global state of a reference counting collector.
 * See Shahriyar et al for details of and rationale for the optimizations used
 * here (http://dx.doi.org/10.1145/2258996.2259008).  See Chapter 4 of
 * Daniel Frampton's PhD thesis for details of and rationale for the cycle
 * collection strategy used by this collector.
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
  public static boolean ccForceFull        = false;
  /** Use backup tracing for cycle collection (currently the only option) */
  public static final boolean CC_BACKUP_TRACE      = true;

  public static boolean performCycleCollection;
  public static final short BT_CLOSURE             = Phase.createSimple("closure-bt");

  /** True if we are building for generational RC */
  public static final boolean BUILD_FOR_GENRC = ((RCBaseConstraints) VM.activePlan.constraints()).buildForGenRC();

  // CHECKSTYLE:OFF

  /**
   * Reference counting specific collection steps.
   */
  protected static final short refCountCollectionPhase = Phase.createComplex("release", null,
      Phase.scheduleGlobal     (PROCESS_OLDROOTBUFFER),
      Phase.scheduleCollector  (PROCESS_OLDROOTBUFFER),
      Phase.scheduleGlobal     (PROCESS_NEWROOTBUFFER),
      Phase.scheduleCollector  (PROCESS_NEWROOTBUFFER),
      Phase.scheduleMutator    (PROCESS_MODBUFFER),
      Phase.scheduleGlobal     (PROCESS_MODBUFFER),
      Phase.scheduleCollector  (PROCESS_MODBUFFER),
      Phase.scheduleMutator    (PROCESS_DECBUFFER),
      Phase.scheduleGlobal     (PROCESS_DECBUFFER),
      Phase.scheduleCollector  (PROCESS_DECBUFFER),
      Phase.scheduleGlobal     (BT_CLOSURE),
      Phase.scheduleCollector  (BT_CLOSURE));

  protected static final short genRCCollectionPhase = Phase.createComplex("release", null,
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
  protected static final short refCountRootClosurePhase = Phase.createComplex("initial-closure", null,
      Phase.scheduleMutator    (PREPARE),
      Phase.scheduleGlobal     (PREPARE),
      Phase.scheduleCollector  (PREPARE),
      Phase.scheduleComplex    (prepareStacks),
      Phase.scheduleCollector  (STACK_ROOTS),
      Phase.scheduleCollector  (ROOTS),
      Phase.scheduleGlobal     (ROOTS),
      Phase.scheduleGlobal     (CLOSURE),
      Phase.scheduleCollector  (CLOSURE));

  protected static final short genRCRootClosurePhase = Phase.createComplex("initial-closure", null,
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
  public short refCountCollection = Phase.createComplex("collection", null,
      Phase.scheduleComplex(initPhase),
      Phase.scheduleComplex(refCountRootClosurePhase),
      Phase.scheduleComplex(refCountCollectionPhase),
      Phase.scheduleComplex(completeClosurePhase),
      Phase.scheduleComplex(finishPhase));
  
  public short genRCCollection = Phase.createComplex("collection", null,
      Phase.scheduleComplex(initPhase),
      Phase.scheduleComplex(genRCRootClosurePhase),
      Phase.scheduleComplex(genRCCollectionPhase),
      Phase.scheduleComplex(completeClosurePhase),
      Phase.scheduleComplex(finishPhase));

  // CHECKSTYLE:ON

  /*****************************************************************************
   *
   * Class fields
   */

  /**
   *
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

  /**
   *
   */
  public final Trace rootTrace;
  public final Trace backupTrace;
  private final BTSweeper rcSweeper;
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
    loFreeSweeper = new BTFreeLargeObjectSweeper();
  }

  @Override
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

  /**
   *
   */
  public static final boolean isRCObject(ObjectReference object) {
    return !object.isNull() && !Space.isInSpace(VM_SPACE, object);
  }

  @Override
  public boolean lastCollectionFullHeap() {
    return performCycleCollection;
  }

  @Override
  public void collectionPhase(short phaseId) {
    if (phaseId == SET_COLLECTION_KIND) {
      super.collectionPhase(phaseId);
      if (CC_ENABLED) {
        ccForceFull = Options.fullHeapSystemGC.getValue();
        if (BUILD_FOR_GENRC) performCycleCollection = (collectionAttempt > 1) || emergencyCollection || ccForceFull;
        else performCycleCollection |= (collectionAttempt > 1) || emergencyCollection || ccForceFull;
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
      modPool.prepare();
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
        rcloSpace.sweep(loFreeSweeper);
      } else {
        rcSpace.release();
      }
      if (!BUILD_FOR_GENRC) performCycleCollection = getPagesAvail() < Options.cycleTriggerThreshold.getPages();
      return;
    }

    super.collectionPhase(phaseId);
  }

  /*****************************************************************************
   *
   * Accounting
   */

  /**
   * {@inheritDoc}
   */
  @Override
  public int getPagesUsed() {
    return (rcSpace.reservedPages() + rcloSpace.reservedPages() + super.getPagesUsed());
  }

  /**
   * Perform a linear scan across all objects in the heap to check for leaks.
   */
  @Override
  public void sanityLinearScan(LinearScan scan) {
    //rcSpace.linearScan(scan);
  }

  @Override
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

  @Override
  @Interruptible
  protected void registerSpecializedMethods() {
    super.registerSpecializedMethods();
  }
}
