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
package org.mmtk.plan.concurrent;

import org.mmtk.plan.Phase;
import org.mmtk.plan.Simple;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.ConcurrentTrigger;
import org.mmtk.utility.options.Options;

import org.vmmagic.pragma.*;

/**
 * This class implements the global state of a concurrent collector.
 */
@Uninterruptible
public abstract class Concurrent extends Simple {

  /****************************************************************************
   * Constants
   */

  /****************************************************************************
   * Class variables
   */

  /**
   *
   */
  public static final short FLUSH_MUTATOR               = Phase.createSimple("flush-mutator", null);
  public static final short SET_BARRIER_ACTIVE          = Phase.createSimple("set-barrier", null);
  public static final short FLUSH_COLLECTOR             = Phase.createSimple("flush-collector", null);
  public static final short CLEAR_BARRIER_ACTIVE        = Phase.createSimple("clear-barrier", null);

  // CHECKSTYLE:OFF

  /**
   * When we preempt a concurrent marking phase we must flush mutators and then continue the closure.
   */
  protected static final short preemptConcurrentClosure = Phase.createComplex("preeempt-concurrent-trace", null,
      Phase.scheduleMutator  (FLUSH_MUTATOR),
      Phase.scheduleCollector(CLOSURE));

  public static final short CONCURRENT_CLOSURE = Phase.createConcurrent("concurrent-closure",
                                                                        Phase.scheduleComplex(preemptConcurrentClosure));

  /**
   * Perform the initial determination of liveness from the roots.
   */
  protected static final short concurrentClosure = Phase.createComplex("concurrent-mark", null,
      Phase.scheduleGlobal    (SET_BARRIER_ACTIVE),
      Phase.scheduleMutator   (SET_BARRIER_ACTIVE),
      Phase.scheduleCollector (FLUSH_COLLECTOR),
      Phase.scheduleConcurrent(CONCURRENT_CLOSURE),
      Phase.scheduleGlobal    (CLEAR_BARRIER_ACTIVE),
      Phase.scheduleMutator   (CLEAR_BARRIER_ACTIVE));

  /** Build, validate and then build another sanity table */
  protected static final short preSanityPhase = Phase.createComplex("sanity", null,
      Phase.scheduleComplex    (sanityBuildPhase),
      Phase.scheduleGlobal     (SANITY_SET_PREGC),
      Phase.scheduleComplex    (sanityCheckPhase),
      Phase.scheduleComplex    (sanityBuildPhase));

  /** Validate, build and validate the second sanity table */
  protected static final short postSanityPhase = Phase.createComplex("sanity", null,
      Phase.scheduleGlobal     (SANITY_SET_POSTGC),
      Phase.scheduleComplex    (sanityCheckPhase),
      Phase.scheduleComplex    (sanityBuildPhase),
      Phase.scheduleGlobal     (SANITY_SET_PREGC),
      Phase.scheduleComplex    (sanityCheckPhase));

  // CHECKSTYLE:OFF

  /****************************************************************************
   * Instance variables
   */

  /****************************************************************************
   * Constructor.
   */
  public Concurrent() {
    Options.concurrentTrigger = new ConcurrentTrigger();
  }

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * {@inheritDoc}
   */
  @Override
  @Interruptible
  public void processOptions() {
    super.processOptions();

    /* Set up the concurrent marking phase */
    replacePhase(Phase.scheduleCollector(CLOSURE), Phase.scheduleComplex(concurrentClosure));

    if (Options.sanityCheck.getValue()) {
      Log.writeln("Collection sanity checking enabled.");
      replacePhase(Phase.schedulePlaceholder(PRE_SANITY_PLACEHOLDER), Phase.scheduleComplex(preSanityPhase));
      replacePhase(Phase.schedulePlaceholder(POST_SANITY_PLACEHOLDER), Phase.scheduleComplex(postSanityPhase));
    }
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   *
   */
  private boolean inConcurrentCollection = false;

  @Override
  @Inline
  public void collectionPhase(short phaseId) {
    if (phaseId == SET_BARRIER_ACTIVE) {
      ConcurrentMutator.newMutatorBarrierActive = true;
      return;
    }
    if (phaseId == CLEAR_BARRIER_ACTIVE) {
      ConcurrentMutator.newMutatorBarrierActive = false;
      return;
    }
    if (phaseId == CLOSURE) {
      return;
    }
    if (phaseId == PREPARE) {
      inConcurrentCollection = true;
    }
    if (phaseId == RELEASE) {
      inConcurrentCollection = false;
    }
    super.collectionPhase(phaseId);
  }

  @Override
  protected boolean concurrentCollectionRequired() {
    return !Phase.concurrentPhaseActive() &&
      ((getPagesReserved() * 100) / getTotalPages()) > Options.concurrentTrigger.getValue();
  }

  @Override
  public boolean lastCollectionFullHeap() {
    // TODO: Why this?
    return !inConcurrentCollection;
  }

  /*****************************************************************************
   *
   * Accounting
   */
}
