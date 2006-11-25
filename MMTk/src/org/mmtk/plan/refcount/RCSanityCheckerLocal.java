/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */
package org.mmtk.plan.refcount;

import org.mmtk.utility.sanitychecker.SanityChecker;
import org.mmtk.utility.sanitychecker.SanityCheckerLocal;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class performs sanity checks for reference counting collectors.
 * 
 * $Id$
 * 
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
@Uninterruptible public class RCSanityCheckerLocal extends SanityCheckerLocal {

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
    if (VM.activePlan.global().getSanityChecker().preGCSanity()) {
      return SanityChecker.UNSURE;
    }
    
    if (RCBase.isRCObject(object)) {
      if (!RCHeader.isLiveRC(object)) {
        return SanityChecker.DEAD;
      }
      return RCHeader.getRC(object) - sanityRootRC;
    } else {
      return SanityChecker.UNSURE;
    }
  }

}
