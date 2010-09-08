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
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.options.*;
import org.mmtk.utility.statistics.Timer;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This abstract class implements the core functionality for
 * simple collectors.<p>
 *
 * This class defines the collection phases, and provides base
 * level implementations of them.  Subclasses should provide
 * implementations for the spaces that they introduce, and
 * delegate up the class hierarchy.<p>
 *
 * For details of the split between global and thread-local operations
 * @see org.mmtk.plan.Plan
 */
@Uninterruptible
public abstract class Simple extends Plan implements Constants {
  /****************************************************************************
   * Constants
   */

  /* Shared Timers */
  private static final Timer refTypeTime = new Timer("refType", false, true);
  private static final Timer scanTime = new Timer("scan", false, true);
  private static final Timer finalizeTime = new Timer("finalize", false, true);

  /* Phases */
  public static final short SET_COLLECTION_KIND = Phase.createSimple("set-collection-kind", null);
  public static final short INITIATE            = Phase.createSimple("initiate", null);
  public static final short PREPARE             = Phase.createSimple("prepare");
  public static final short PREPARE_STACKS      = Phase.createSimple("prepare-stacks", null);
  public static final short STACK_ROOTS         = Phase.createSimple("stacks");
  public static final short ROOTS               = Phase.createSimple("root");
  public static final short CLOSURE             = Phase.createSimple("closure", scanTime);
  public static final short SOFT_REFS           = Phase.createSimple("soft-ref", refTypeTime);
  public static final short WEAK_REFS           = Phase.createSimple("weak-ref", refTypeTime);
  public static final short FINALIZABLE         = Phase.createSimple("finalize", finalizeTime);
  public static final short WEAK_TRACK_REFS     = Phase.createSimple("weak-track-ref", refTypeTime);
  public static final short PHANTOM_REFS        = Phase.createSimple("phantom-ref", refTypeTime);
  public static final short FORWARD             = Phase.createSimple("forward");
  public static final short FORWARD_REFS        = Phase.createSimple("forward-ref", refTypeTime);
  public static final short FORWARD_FINALIZABLE = Phase.createSimple("forward-finalize", finalizeTime);
  public static final short RELEASE             = Phase.createSimple("release");
  public static final short COMPLETE            = Phase.createSimple("complete", null);

  /* Sanity placeholder */
  public static final short PRE_SANITY_PLACEHOLDER  = Phase.createSimple("pre-sanity-placeholder", null);
  public static final short POST_SANITY_PLACEHOLDER = Phase.createSimple("post-sanity-placeholder", null);

  /* Sanity phases */
  public static final short SANITY_SET_PREGC    = Phase.createSimple("sanity-setpre", null);
  public static final short SANITY_SET_POSTGC   = Phase.createSimple("sanity-setpost", null);
  public static final short SANITY_PREPARE      = Phase.createSimple("sanity-prepare", null);
  public static final short SANITY_ROOTS        = Phase.createSimple("sanity-roots", null);
  public static final short SANITY_COPY_ROOTS   = Phase.createSimple("sanity-copy-roots", null);
  public static final short SANITY_BUILD_TABLE  = Phase.createSimple("sanity-build-table", null);
  public static final short SANITY_CHECK_TABLE  = Phase.createSimple("sanity-check-table", null);
  public static final short SANITY_RELEASE      = Phase.createSimple("sanity-release", null);

  // CHECKSTYLE:OFF

  /** Ensure stacks are ready to be scanned */
  protected static final short prepareStacks = Phase.createComplex("prepare-stacks", null,
      Phase.scheduleMutator    (PREPARE_STACKS),
      Phase.scheduleGlobal     (PREPARE_STACKS));

  /** Trace and set up a sanity table */
  protected static final short sanityBuildPhase = Phase.createComplex("sanity-build", null,
      Phase.scheduleGlobal     (SANITY_PREPARE),
      Phase.scheduleCollector  (SANITY_PREPARE),
      Phase.scheduleComplex    (prepareStacks),
      Phase.scheduleCollector  (SANITY_ROOTS),
      Phase.scheduleGlobal     (SANITY_ROOTS),
      Phase.scheduleCollector  (SANITY_COPY_ROOTS),
      Phase.scheduleGlobal     (SANITY_BUILD_TABLE));

  /** Validate a sanity table */
  protected static final short sanityCheckPhase = Phase.createComplex("sanity-check", null,
      Phase.scheduleGlobal     (SANITY_CHECK_TABLE),
      Phase.scheduleCollector  (SANITY_RELEASE),
      Phase.scheduleGlobal     (SANITY_RELEASE));

  /** Start the collection, including preparation for any collected spaces. */
  protected static final short initPhase = Phase.createComplex("init",
      Phase.scheduleGlobal     (SET_COLLECTION_KIND),
      Phase.scheduleGlobal     (INITIATE),
      Phase.schedulePlaceholder(PRE_SANITY_PLACEHOLDER));

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
      Phase.scheduleGlobal     (CLOSURE),
      Phase.scheduleCollector  (CLOSURE));

  /**
   * Complete closure including reference types and finalizable objects.
   */
  protected static final short refTypeClosurePhase = Phase.createComplex("refType-closure", null,
      Phase.scheduleCollector  (SOFT_REFS),
      Phase.scheduleGlobal     (CLOSURE),
      Phase.scheduleCollector  (CLOSURE),
      Phase.scheduleCollector  (WEAK_REFS),
      Phase.scheduleCollector  (FINALIZABLE),
      Phase.scheduleGlobal     (CLOSURE),
      Phase.scheduleCollector  (CLOSURE),
      Phase.schedulePlaceholder(WEAK_TRACK_REFS),
      Phase.scheduleCollector  (PHANTOM_REFS));

  /**
   * Ensure that all references in the system are correct.
   */
  protected static final short forwardPhase = Phase.createComplex("forward-all", null,
      /* Finish up */
      Phase.schedulePlaceholder(FORWARD),
      Phase.scheduleCollector  (FORWARD_REFS),
      Phase.scheduleCollector  (FORWARD_FINALIZABLE));

  /**
   * Complete closure including reference types and finalizable objects.
   */
  protected static final short completeClosurePhase = Phase.createComplex("release", null,
      Phase.scheduleMutator    (RELEASE),
      Phase.scheduleCollector  (RELEASE),
      Phase.scheduleGlobal     (RELEASE));


  /**
   * The collection scheme - this is a small tree of complex phases.
   */
  protected static final short finishPhase = Phase.createComplex("finish",
      Phase.schedulePlaceholder(POST_SANITY_PLACEHOLDER),
      Phase.scheduleCollector  (COMPLETE),
      Phase.scheduleGlobal     (COMPLETE));

  /**
   * This is the phase that is executed to perform a collection.
   */
  public short collection = Phase.createComplex("collection", null,
      Phase.scheduleComplex(initPhase),
      Phase.scheduleComplex(rootClosurePhase),
      Phase.scheduleComplex(refTypeClosurePhase),
      Phase.scheduleComplex(forwardPhase),
      Phase.scheduleComplex(completeClosurePhase),
      Phase.scheduleComplex(finishPhase));

  // CHECKSTYLE:ON

  /**
   * The current collection attempt.
   */
  protected int collectionAttempt;

  /****************************************************************************
   * Collection
   */

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId The unique of the phase to perform.
   */
  @Inline
  public void collectionPhase(short phaseId) {
    if (phaseId == SET_COLLECTION_KIND) {
      collectionAttempt = Allocator.getAndClearMaxCollectionAttempts();
      emergencyCollection = lastCollectionWasExhaustive() && collectionAttempt > 1;
      if (emergencyCollection) {
        if (Options.verbose.getValue() >= 1) Log.write("[Emergency]");
        forceFullHeapCollection();
      }
      return;
    }

    if (phaseId == INITIATE) {
      setGCStatus(GC_PREPARE);
      return;
    }

    if (phaseId == PREPARE_STACKS) {
      stacksPrepared = true;
      return;
    }

    if (phaseId == PREPARE) {
      loSpace.prepare(true);
      nonMovingSpace.prepare(true);
      if (USE_CODE_SPACE) {
        smallCodeSpace.prepare(true);
        largeCodeSpace.prepare(true);
      }
      immortalSpace.prepare();
      VM.memory.globalPrepareVMSpace();
      return;
    }

    if (phaseId == ROOTS) {
      VM.scanning.resetThreadCounter();
      setGCStatus(GC_PROPER);
      return;
    }

    if (phaseId == RELEASE) {
      loSpace.release(true);
      nonMovingSpace.release();
      if (USE_CODE_SPACE) {
        smallCodeSpace.release();
        largeCodeSpace.release(true);
      }
      immortalSpace.release();
      VM.memory.globalReleaseVMSpace();
      return;
    }

    if (phaseId == COMPLETE) {
      setGCStatus(NOT_IN_GC);
      Space.clearAllAllocationFailed();
      return;
    }

    if (Options.sanityCheck.getValue() && sanityChecker.collectionPhase(phaseId)) {
      return;
    }

    Log.write("Global phase "); Log.write(Phase.getName(phaseId));
    Log.writeln(" not handled.");
    VM.assertions.fail("Global phase not handled!");
  }

  /**
   * Replace a scheduled phase. Used for example to replace a placeholder.
   *
   * @param oldScheduledPhase The scheduled phase to replace.
   * @param newScheduledPhase The new scheduled phase.
   */
  public void replacePhase(int oldScheduledPhase, int newScheduledPhase) {
    ComplexPhase cp = (ComplexPhase)Phase.getPhase(collection);
    cp.replacePhase(oldScheduledPhase, newScheduledPhase);
  }

  /**
   * Replace a placeholder phase.
   *
   * @param placeHolderPhase The placeholder phase
   * @param newScheduledPhase The new scheduled phase.
   */
  public void replacePlaceholderPhase(short placeHolderPhase, int newScheduledPhase) {
    ComplexPhase cp = (ComplexPhase)Phase.getPhase(collection);
    cp.replacePhase(Phase.schedulePlaceholder(placeHolderPhase), newScheduledPhase);
  }
}
