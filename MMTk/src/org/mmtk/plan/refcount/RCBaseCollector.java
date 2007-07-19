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
package org.mmtk.plan.refcount;

import org.mmtk.plan.*;
import org.mmtk.plan.refcount.cd.CDCollector;
import org.mmtk.plan.refcount.cd.NullCDCollector;
import org.mmtk.plan.refcount.cd.TrialDeletionCollector;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.utility.sanitychecker.SanityCheckerLocal;
import org.mmtk.utility.scan.Scan;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-collector thread</i> behavior
 * and state for the <i>RC</i> plan, which implements a full-heap
 * reference counting collector.<p>
 *
 * Specifically, this class defines <i>RC</i> collection behavior
 * (through <code>trace</code> and the <code>collectionPhase</code>
 * method).<p>
 *
 * @see RCBase for an overview of the reference counting algorithm.<p>
 *
 * FIXME The SegregatedFreeList class (and its decendents such as
 * MarkSweepLocal) does not properly separate mutator and collector
 * behaviors, so the ms field below should really not exist in
 * this class as there is no collection-time allocation in this
 * collector.
 *
 * @see RCBase
 * @see RCBaseMutator
 * @see StopTheWorldCollector
 * @see CollectorContext
 * @see SimplePhase#delegatePhase
 */
@Uninterruptible public abstract class RCBaseCollector extends StopTheWorldCollector {

  /****************************************************************************
   * Instance fields
   */
  public ObjectReferenceDeque newRootSet;
  public ObjectReferenceDeque oldRootSet;
  public DecBuffer decBuffer;
  public ObjectReferenceDeque modBuffer;

  private NullCDCollector nullCD;
  private TrialDeletionCollector trialDeletionCD;
  private RCSanityCheckerLocal sanityChecker;

  /****************************************************************************
   * Initialization
   */

  /**
   * Constructor
   */
  public RCBaseCollector() {
    newRootSet = new ObjectReferenceDeque("new root", global().newRootPool);
    global().newRootPool.newConsumer();
    oldRootSet = new ObjectReferenceDeque("old root", global().oldRootPool);
    global().oldRootPool.newConsumer();
    modBuffer = new ObjectReferenceDeque("mod buf", global().modPool);
    global().modPool.newConsumer();
    decBuffer = new DecBuffer(global().decPool);
    sanityChecker = new RCSanityCheckerLocal();
    switch (RCBase.CYCLE_DETECTOR) {
    case RCBase.NO_CYCLE_DETECTOR:
      nullCD = new NullCDCollector();
      break;
    case RCBase.TRIAL_DELETION:
      trialDeletionCD = new TrialDeletionCollector();
      break;
    }
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a per-collector collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param primary Perform any single-threaded activities using this thread.
   */
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == RCBase.PREPARE) {
      if (RCBase.WITH_COALESCING_RC) {
        processModBuffer();
      }
      processOldRootSet();
      getCurrentTrace().prepare();
      return;
    }

    if (phaseId == RCBase.START_CLOSURE) {
      getCurrentTrace().startTrace();
      return;
    }

    if (phaseId == RCBase.COMPLETE_CLOSURE) {
      getCurrentTrace().completeTrace();
      processNewRootSet();
      return;
    }

    if (phaseId == RCBase.RELEASE) {
      getCurrentTrace().release();
      processDecBuffer();
      return;
    }

    if (!cycleDetector().collectionPhase(phaseId, primary)) {
      super.collectionPhase(phaseId, primary);
    }
  }

  /**
   * Report a root object.
   *
   * @param object The root
   */
  public void reportRoot(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(RCBase.isRCObject(object));
    }
    RCHeader.incRC(object);
    newRootSet.push(object);
  }

  /**
   * Process the old root set, by either decrementing each
   * entry, or unmarking the object's root flag.
   */
  public void processOldRootSet() {
    ObjectReference current;
    while (!(current = oldRootSet.pop()).isNull()) {
      decBuffer.push(current);
    }
    decBuffer.flushLocal();
  }

  /**
   * Move the new root set so that it is the old set for the
   * next collection.
   */
  public void processNewRootSet() {
    ObjectReference current;
    while (!(current = newRootSet.pop()).isNull()) {
      oldRootSet.push(current);
    }
    oldRootSet.flushLocal();
  }

  /**
   * Process the decrement buffers, enqueing recursive
   * decrements if necessary.
   */
  public void processDecBuffer() {
    ObjectReference current;
    while (!(current = decBuffer.pop()).isNull()) {
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(RCBase.isRCObject(current));
      }
      switch (RCHeader.decRC(current)) {
      case RCHeader.DEC_KILL:
        decBuffer.processChildren(current);
        if (global().cycleDetector().allowFree(current)) {
          RCBase.free(current);
        }
        break;
      case RCHeader.DEC_BUFFER:
        cycleDetector().bufferOnDecRC(current);
        break;
      }
    }
  }

  /**
   * Process the modified object buffers.
   */
  public void processModBuffer() {
    TraceStep modProcessor = getModifiedProcessor();
    ObjectReference current;
    while (!(current = modBuffer.pop()).isNull()) {
      RCHeader.makeUnlogged(current);
      Scan.scanObject(modProcessor, current);
    }
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as an <code>MS</code> instance. */
  @Inline
  private static RCBase global() {
    return (RCBase) VM.activePlan.global();
  }

  /** @return The current sanity checker. */
  public SanityCheckerLocal getSanityChecker() {
    return sanityChecker;
  }

  /** @return The TraceStep to use when processing modified objects. */
  protected abstract TraceStep getModifiedProcessor();

  /** @return The active cycle detector instance */
  @Inline
  public final CDCollector cycleDetector() {
    switch (RCBase.CYCLE_DETECTOR) {
    case RCBase.NO_CYCLE_DETECTOR:
      return nullCD;
    case RCBase.TRIAL_DELETION:
      return trialDeletionCD;
    }

    VM.assertions.fail("No cycle detector instance found.");
    return null;
  }
}
