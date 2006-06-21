/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.markcompact;

import org.mmtk.policy.Space;

import org.mmtk.utility.sanitychecker.SanityChecker;
import org.mmtk.utility.sanitychecker.SanityCheckerLocal;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class performs sanity checks for MarkCompact collectors.
 * 
 * $Id$
 * 
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class MCSanityCheckerLocal extends SanityCheckerLocal implements
    Uninterruptible {

  /**
   * Return the expected reference count. For non-reference counting collectors
   * this becomes a true/false relationship.
   * 
   * @param object
   *          The object to check.
   * @param sanityRootRC
   *          The number of root references to the object.
   * @return The expected (root excluded) reference count.
   */
  protected int sanityExpectedRC(ObjectReference object, int sanityRootRC) {
    Space space = Space.getSpaceForObject(object);

    // Nursery
    if (space == MC.mcSpace) {
      // We are never sure about objects in MC.
      // This is not very satisfying but allows us to use the sanity checker to
      // detect dangling pointers.
      return SanityChecker.UNSURE;
    } else {
      return super.sanityExpectedRC(object, sanityRootRC);
    }
  }

}
