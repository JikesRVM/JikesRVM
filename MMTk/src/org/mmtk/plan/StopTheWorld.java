/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan;

import org.mmtk.policy.Space;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.*;
import org.mmtk.utility.sanitychecker.SanityChecker;
import org.mmtk.utility.statistics.Timer;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This abstract class implements the core functionality for
 * stop-the-world collectors.  Stop-the-world collectors should
 * inherit from this class.<p>
 *
 * This class defines the collection phases, and provides base
 * level implementations of them.  Subclasses should provide
 * implementations for the spaces that they introduce, and
 * delegate up the class hierarchy.<p>
 *
 * For details of the split between global and thread-local operations
 * @see org.mmtk.plan.Plan
 */
@Uninterruptible public abstract class StopTheWorld extends Plan
  implements Constants {
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
  public static final short PRECOPY             = Phase.createSimple("precopy");
  public static final short ROOTS               = Phase.createSimple("root");
  public static final short BOOTIMAGE_ROOTS     = Phase.createSimple("bootimage-root");
  public static final short START_CLOSURE       = Phase.createSimple("start-closure", scanTime);
  public static final short SOFT_REFS           = Phase.createSimple("soft-ref", refTypeTime);
  public static final short COMPLETE_CLOSURE    = Phase.createSimple("complete-closure", scanTime);
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
  public static final short SANITY_PREPARE      = Phase.createSimple("sanity-prepare", null);
  public static final short SANITY_ROOTS        = Phase.createSimple("sanity-roots", null);
  public static final short SANITY_BUILD_TABLE  = Phase.createSimple("sanity-build-table", null);
  public static final short SANITY_CHECK_TABLE  = Phase.createSimple("sanity-check-table", null);
  public static final short SANITY_RELEASE      = Phase.createSimple("sanity-release", null);

  /** Trace and set up a sanity table */
  private static final short sanityBuildPhase = Phase.createComplex("sanity-build", null,
      Phase.scheduleGlobal     (SANITY_PREPARE),
      Phase.scheduleCollector  (SANITY_PREPARE),
      Phase.scheduleCollector  (SANITY_ROOTS),
      Phase.scheduleGlobal     (SANITY_ROOTS),
      Phase.scheduleCollector  (SANITY_BUILD_TABLE));

  /** Validate a sanity table */
  private static final short sanityCheckPhase = Phase.createComplex("sanity-check", null,
      Phase.scheduleCollector  (SANITY_CHECK_TABLE),
      Phase.scheduleCollector  (SANITY_RELEASE),
      Phase.scheduleGlobal     (SANITY_RELEASE));

  /** Build and validate a sanity table */
  private static final short sanityPhase = Phase.createComplex("sanity", null,
      Phase.scheduleComplex    (sanityBuildPhase),
      Phase.scheduleComplex    (sanityCheckPhase));

  /** Start the collection, including preparation for any collected spaces. */
  protected static final short initPhase = Phase.createComplex("init",
      Phase.scheduleGlobal     (SET_COLLECTION_KIND),
      Phase.scheduleGlobal     (INITIATE),
      Phase.scheduleCollector  (INITIATE),
      Phase.scheduleMutator    (INITIATE),
      Phase.schedulePlaceholder(PRE_SANITY_PLACEHOLDER));

  /**
   * Perform the initial determination of liveness from the roots.
   */
  protected static final short rootClosurePhase = Phase.createComplex("initial-closure", null,
      Phase.scheduleMutator    (PREPARE),
      Phase.scheduleGlobal     (PREPARE),
      Phase.scheduleCollector  (PREPARE),
      Phase.scheduleCollector  (PRECOPY),
      Phase.scheduleCollector  (BOOTIMAGE_ROOTS),
      Phase.scheduleCollector  (ROOTS),
      Phase.scheduleGlobal     (ROOTS),
      Phase.scheduleCollector  (START_CLOSURE));

  /**
   * Complete closure including reference types and finalizable objects.
   */
  protected static final short refTypeClosurePhase = Phase.createComplex("refType-closure", null,
      Phase.scheduleCollector  (SOFT_REFS),    
      Phase.scheduleCollector  (COMPLETE_CLOSURE),
      Phase.scheduleCollector  (WEAK_REFS),
      Phase.scheduleCollector  (FINALIZABLE),  
      Phase.scheduleCollector  (COMPLETE_CLOSURE),
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
  protected static final short completeClosurePhase = Phase.createComplex("refType-closure", null,
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

  /* Basic GC sanity checker */
  private SanityChecker sanityChecker = new SanityChecker();

  /**
   * The current collection attempt.
   */
  protected int collectionAttempt;

  /****************************************************************************
   * Collection
   */

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  @Interruptible
  public void postBoot() {
    super.postBoot();

    if (Options.sanityCheck.getValue()) {
      if (getSanityChecker() == null ||
          VM.activePlan.collector().getSanityChecker() == null) {
        Log.writeln("Collector does not support sanity checking!");
      } else {
        Log.writeln("Collection sanity checking enabled.");
        replacePhase(PRE_SANITY_PLACEHOLDER, Phase.scheduleComplex(sanityPhase));
        replacePhase(POST_SANITY_PLACEHOLDER, Phase.scheduleComplex(sanityPhase));
      }
    }
  }

  /**
   * @return Return the current sanity checker.
   */
  public SanityChecker getSanityChecker() {
    return sanityChecker;
  }

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId The unique of the phase to perform.
   */
  @Inline
  public void collectionPhase(short phaseId) {
    if (phaseId == SET_COLLECTION_KIND) {
      requiredAtStart = getPagesRequired();
      collectionAttempt = VM.collection.maximumCollectionAttempt();
      emergencyCollection = lastCollectionFullHeap() && collectionAttempt > 1;
      if (collectionAttempt > MAX_COLLECTION_ATTEMPTS) {
        VM.assertions.fail("Too many collection attempts. Suspect plan is not setting FullHeap flag");
      }
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

    if (phaseId == PREPARE) {
      loSpace.prepare(true);
      ploSpace.prepare(true);
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
      ploSpace.release(true);
      immortalSpace.release();
      VM.memory.globalReleaseVMSpace();
      return;
    }

    if (phaseId == COMPLETE) {
      setGCStatus(NOT_IN_GC);
      Space.clearAllAllocationFailed();
      awaitingAsyncCollection = false;
      return;
    }

    if (Options.sanityCheck.getValue() &&
        getSanityChecker().collectionPhase(phaseId)) {
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
  public void replacePlaceholderPhase(short placeholderPhase, int newScheduledPhase) {
    ComplexPhase cp = (ComplexPhase)Phase.getPhase(collection);
    cp.replacePhase(Phase.schedulePlaceholder(placeholderPhase), newScheduledPhase);
  }
}
