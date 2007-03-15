/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005, 2006
 */
package org.mmtk.plan.refcount;

import org.mmtk.plan.StopTheWorldConstraints;

import org.vmmagic.pragma.*;

/**
 * This class and its subclasses communicate to the host VM/Runtime
 * any features of the selected plan that it needs to know.  This is
 * separate from the main Plan/PlanLocal class in order to bypass any
 * issues with ordering of static initialization.
 * 
 *
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 */
@Uninterruptible public class RCBaseConstraints extends StopTheWorldConstraints {

  public boolean needsWriteBarrier() { return true; }

  public int gcHeaderBits() { return RCHeader.LOCAL_GC_BITS_REQUIRED; }

  public int gcHeaderWords() { return RCHeader.GC_HEADER_WORDS_REQUIRED; }
}
