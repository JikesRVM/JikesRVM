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
package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.refcount.RCBase;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the global state of a simple reference counting
 * collector.
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities, and divides global and local state
 * into separate class hierarchies.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  There is a single instance of Plan (or the
 * appropriate sub-class), and a 1:1 mapping of PlanLocal to "kernel
 * threads" (aka CPUs or in Jikes RVM, Processors).  Thus instance
 * methods of PlanLocal allow fast, unsychronized access to functions such as
 * allocation and collection.
 *
 * The global instance defines and manages static resources
 * (such as memory and virtual memory resources).  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance properties of MMTk plans.
 */
@Uninterruptible
public class GenRC extends RCBase {

  /****************************************************************************
   *
   * Class variables
   */

  /** The nursery space, where all new objects are allocated by default. */
  public static CopySpace nurserySpace = new CopySpace("nursery", DEFAULT_POLL_FREQUENCY, false, VMRequest.create(0.15f, true));

  public static final int NS = nurserySpace.getDescriptor();

  // Allocators
  public static final int ALLOC_NURSERY = ALLOC_DEFAULT;

  /****************************************************************************
   * Instance variables
   */

  /**
   * Constructor.
   */
  public GenRC() {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(WITH_COALESCING_RC);
    }
  }

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase to execute.
   */
  @NoInline
  public void collectionPhase(short phaseId) {
    if (phaseId == PREPARE) {
      nurserySpace.prepare(true);
    }

    if (phaseId == RELEASE) {
      nurserySpace.release();
    }

    super.collectionPhase(phaseId);
  }

  /**
   * This method controls the triggering of a GC. It is called periodically
   * during allocation. Returns true to trigger a collection.
   *
   * @param spaceFull Space request failed, must recover pages within 'space'.
   * @return True if a collection is requested by the plan.
   */
  public final boolean collectionRequired(boolean spaceFull) {
    boolean nurseryFull = nurserySpace.reservedPages() > Options.nurserySize.getMaxNursery();

    return super.collectionRequired(spaceFull) || nurseryFull;
  }

  /**
   * Return the number of pages available for allocation, <i>assuming
   * all future allocation is to the nursery</i>.
   *
   * @return The number of pages available for allocation, <i>assuming
   * all future allocation is to the nursery</i>.
   */
  public int getPagesAvail() {
    return super.getPagesAvail() >> 1;
  }

  /**
   * Return the number of pages reserved for copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for copying.
   */
  public final int getCollectionReserve() {
    return nurserySpace.reservedPages() + super.getCollectionReserve();
  }

  /**
   * Return the number of pages in use given the pending
   * allocation.  Simply add the nursery's contribution to that of
   * the superclass.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */

  public final int getPagesUsed() {
    return super.getPagesUsed() + nurserySpace.reservedPages();
  }

  /**
   * Calculate the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   *
   * @return the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   */
  public int getPagesRequired() {
    return super.getPagesRequired() + (nurserySpace.requiredPages() << 1);
  }

  /**
   * @see org.mmtk.plan.Plan#willNeverMove
   *
   * @param object Object in question
   * @return True if the object will never move
   */
  @Override
  public boolean willNeverMove(ObjectReference object) {
    if (Space.isInSpace(NS, object))
      return false;
    return super.willNeverMove(object);
  }
}
