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
package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.refcount.RCBase;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.options.Options;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements the global state of a a simple reference counting collector.
 */
@Uninterruptible
public class GenRC extends RCBase {

  public static final int ALLOC_NURSERY = ALLOC_DEFAULT;
  public static final int ALLOC_RC      = RCBase.ALLOCATORS + 1;

  /** The nursery space is where all new objects are allocated by default */
  public static final CopySpace nurserySpace = new CopySpace("nursery", false, VMRequest.create(0.15f, true));

  public static final int NURSERY = nurserySpace.getDescriptor();

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase
   */
  public final void collectionPhase(short phaseId) {
   if (phaseId == PREPARE) {
      nurserySpace.prepare(true);
      super.collectionPhase(phaseId);
      return;
    }

    if (phaseId == RELEASE) {
      super.collectionPhase(phaseId);
      nurserySpace.release();
      switchNurseryZeroingApproach(nurserySpace);
      return;
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
  public final boolean collectionRequired(boolean spaceFull, Space space) {
    boolean nurseryFull = nurserySpace.reservedPages() > Options.nurserySize.getMaxNursery();
    return super.collectionRequired(spaceFull, space) || nurseryFull;
  }

  /*****************************************************************************
   *
   * Accounting
   */

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
   * @see org.mmtk.plan.Plan#willNeverMove
   *
   * @param object Object in question
   * @return True if the object will never move
   */
  @Override
  public boolean willNeverMove(ObjectReference object) {
    if (Space.isInSpace(NURSERY, object)) {
      return false;
    }
    if (Space.isInSpace(REF_COUNT_LOS, object)) {
      return true;
    }
    return super.willNeverMove(object);
  }

  @Interruptible
  @Override
  public void fullyBooted() {
    super.fullyBooted();
    nurserySpace.setZeroingApproach(Options.nurseryZeroing.getNonTemporal(), Options.nurseryZeroing.getConcurrent());
  }
}
