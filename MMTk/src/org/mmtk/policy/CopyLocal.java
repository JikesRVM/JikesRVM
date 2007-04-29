/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.policy;

import org.mmtk.utility.alloc.BumpPointer;

import org.vmmagic.pragma.*;

/**
 * This class implements unsynchronized (local) elements of a
 * copying collector. Allocation is via the bump pointer 
 * (@see BumpPointer). 
 * 
 * @see BumpPointer
 * @see CopySpace
 * 
 * @author Daniel Frampton
 * @author Steve Blackburn
 */
@Uninterruptible public final class CopyLocal extends BumpPointer {

  /**
   * Constructor
   * 
   * @param space The space to bump point into.
   */
  public CopyLocal(CopySpace space) {
    super(space, true);
  }
}
