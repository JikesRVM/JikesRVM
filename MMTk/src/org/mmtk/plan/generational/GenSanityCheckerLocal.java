/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.generational;

import org.mmtk.policy.Space;

import org.mmtk.utility.sanitychecker.SanityChecker;
import org.mmtk.utility.sanitychecker.SanityCheckerLocal;
import org.mmtk.vm.ActivePlan;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class performs sanity checks for RefCount collectors. 
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class GenSanityCheckerLocal extends SanityCheckerLocal 
  implements Uninterruptible {
  
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
    if(ActivePlan.global().isCurrentGCNursery()) {
      return SanityChecker.UNSURE;
    }

    // Mature space (full heap collection)
    return space.isLive(object) 
      ? SanityChecker.ALIVE 
      : SanityChecker.DEAD;
  }
 
}
