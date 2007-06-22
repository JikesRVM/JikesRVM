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
package org.mmtk.plan.generational;

import org.mmtk.policy.Space;

import org.mmtk.utility.sanitychecker.SanityChecker;
import org.mmtk.utility.sanitychecker.SanityCheckerLocal;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class performs sanity checks for RefCount collectors.
 */
@Uninterruptible public class GenSanityCheckerLocal extends SanityCheckerLocal {

  /**
   * Return the expected reference count. For non-reference counting
   * collectors this becomes a true/false relationship.
   *
   * @param object The object to check.
   * @param sanityRootRC The number of root references to the object.
   * @return The expected (root excluded) reference count.
   */
  protected int sanityExpectedRC(ObjectReference object,
                                           int sanityRootRC) {
    Space space = Space.getSpaceForObject(object);

    // Nursery
    if (space == Gen.nurserySpace) {
      return global().preGCSanity()
        ? SanityChecker.UNSURE
        : SanityChecker.DEAD;
    }

    // Immortal spaces
    if (space == Gen.immortalSpace || space == Gen.vmSpace) {
      return space.isReachable(object)
        ? SanityChecker.ALIVE
          : SanityChecker.DEAD;
    }

    // Mature space (nursery collection)
    if (VM.activePlan.global().isCurrentGCNursery()) {
      return SanityChecker.UNSURE;
    }

    // Mature space (full heap collection)
    return space.isReachable(object)
      ? SanityChecker.ALIVE
      : SanityChecker.DEAD;
  }

}
