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
package org.mmtk.plan.immix;

import org.mmtk.plan.*;
import org.mmtk.policy.Space;
import org.mmtk.policy.immix.ImmixSpace;
import org.mmtk.policy.immix.ObjectHeader;
import org.mmtk.utility.heap.VMRequest;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the global state of an immix collector.<p>
 *
 * See the PLDI'08 paper by Blackburn and McKinley for a description
 * of the algorithm: http://doi.acm.org/10.1145/1375581.1375586<p>
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities, and divides global and local state
 * into separate class hierarchies.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  There is a single instance of Plan (or the
 * appropriate sub-class), and a 1:1 mapping of PlanLocal to "kernel
 * threads" (aka CPUs or in Jikes RVM, Processors).  Thus instance
 * methods of PlanLocal allow fast, unsychronized access to functions such as
 * allocation and collection.<p>
 *
 * The global instance defines and manages static resources
 * (such as memory and virtual memory resources).  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance properties of MMTk plans.
 */
@Uninterruptible
public class Immix extends StopTheWorld {

  /****************************************************************************
   * Constants
   */

  /****************************************************************************
   * Class variables
   */

  /**
   *
   */
  public static final ImmixSpace immixSpace = new ImmixSpace("immix", VMRequest.discontiguous());
  public static final int IMMIX = immixSpace.getDescriptor();

  public static final int SCAN_IMMIX = 0;
  public static final int SCAN_DEFRAG = 1;

  /****************************************************************************
   * Instance variables
   */

  /**
   *
   */
  public final Trace immixTrace = new Trace(metaDataSpace);
  /** will the next collection collect the whole heap? */
  public boolean nextGCWholeHeap = true;
  /** will this collection collect the whole heap */
  public boolean collectWholeHeap = nextGCWholeHeap;
  protected boolean lastGCWasDefrag = false;

  /**
   * Constructor.
   *
   */
  public Immix() {
  }

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * {@inheritDoc}
   */
  @Override
  @Inline
  public void collectionPhase(short phaseId) {
    if (phaseId == SET_COLLECTION_KIND) {
      super.collectionPhase(phaseId);
      immixSpace.decideWhetherToDefrag(emergencyCollection, collectWholeHeap, collectionAttempt, userTriggeredCollection);
      return;
    }

    if (phaseId == PREPARE) {
      super.collectionPhase(phaseId);
      immixTrace.prepare();
      immixSpace.prepare(true);
      return;
    }

    if (phaseId == CLOSURE) {
      immixTrace.prepare();
      return;
    }

    if (phaseId == RELEASE) {
      immixTrace.release();
      lastGCWasDefrag = immixSpace.release(true);
      super.collectionPhase(phaseId);
      return;
    }

    super.collectionPhase(phaseId);
  }

  @Override
  public boolean lastCollectionWasExhaustive() {
    return lastGCWasDefrag;
  }

  /*****************************************************************************
   *
   * Accounting
   */

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  The superclass accounts for its spaces, we just
   * augment this with the mark-sweep space's contribution.
   */
  @Override
  public int getPagesUsed() {
    return immixSpace.reservedPages() + super.getPagesUsed();
  }

  /**
   * Return the number of pages reserved for collection.
   */
  @Override
  public int getCollectionReserve() {
    return super.getCollectionReserve() + immixSpace.defragHeadroomPages();
  }

  @Override
  public boolean willNeverMove(ObjectReference object) {
    if (Space.isInSpace(IMMIX, object)) {
      ObjectHeader.pinObject(object);
      return true;
    } else
      return super.willNeverMove(object);
  }

  @Override
  @Interruptible
  protected void registerSpecializedMethods() {
    TransitiveClosure.registerSpecializedScan(SCAN_IMMIX, ImmixTraceLocal.class);
    TransitiveClosure.registerSpecializedScan(SCAN_DEFRAG, ImmixDefragTraceLocal.class);
    super.registerSpecializedMethods();
  }
}
