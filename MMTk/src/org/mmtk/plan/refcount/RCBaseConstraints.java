/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan.refcount;

import org.mmtk.plan.StopTheWorldConstraints;
import org.mmtk.policy.RefCountSpace;
import org.vmmagic.pragma.*;

/**
 * Common Reference Counting constants.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public abstract class RCBaseConstraints extends StopTheWorldConstraints
  implements Uninterruptible {

  public boolean needsWriteBarrier() { return true; }

  public boolean noParallelGC() { return true; }

  public int gcHeaderBits() { return RefCountSpace.LOCAL_GC_BITS_REQUIRED; }

  public int gcHeaderWords() { return RefCountSpace.GC_HEADER_WORDS_REQUIRED; }
}
