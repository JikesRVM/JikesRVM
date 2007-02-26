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
 * @version $Revision$
 * @date $Date$
 */
@Uninterruptible public final class CopyLocal extends BumpPointer {
  public static final String Id = "$Id$";

  /**
   * Constructor
   * 
   * @param space The space to bump point into.
   */
  public CopyLocal(CopySpace space) {
    super(space, true);
  }

  /**
   * Re-associate this bump pointer with a different space. Also 
   * reset the bump pointer so that it will use the new space
   * on the next call to <code>alloc</code>.
   * 
   * @param space The space to associate the bump pointer with.
   */
  public void rebind(CopySpace space) {
    super.rebind(space);
  }
}
