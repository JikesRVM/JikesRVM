/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan;

import org.vmmagic.pragma.*;

/**
 * This class and its subclasses communicate to the host VM/Runtime
 * any features of the selected plan that it needs to know.  This is
 * separate from the main Plan/PlanLocal class in order to bypass any
 * issues with ordering of static initialization.
 */
@Uninterruptible public abstract class PlanConstraints {
  /** @return True if this Plan requires write barriers. */
  public boolean needsWriteBarrier() { return false; }

  /** @return True of this Plan requires read barriers. */
  public boolean needsReadBarrier() { return false; }

  /** @return True if this Plan requires static write barriers. */
  public boolean needsStaticWriteBarrier() { return false;}

  /** @return True if this Plan requires static read barriers. */
  public boolean needsStaticReadBarrier() { return false; }

  /** @return True if this Plan requires linear scanning. */
  public boolean needsLinearScan() { return org.mmtk.utility.Constants.SUPPORT_CARD_SCANNING;}

  /** @return True if this Plan does not support parallel collection. */
  public boolean noParallelGC() { return false;}

  /** @return True if this Plan moves objects. */
  public boolean movesObjects() { return false;}

  /** @return True if this object forwards objects <i>after</i> 
   * determining global object liveness (e.g. many compacting collectors). */
  public boolean needsForwardAfterLiveness() { return false;}

  /** @return Is this plan generational in nature. */
  public boolean generational() { return false;}

  /** @return The number of header bits that are required. */
  public abstract int gcHeaderBits();

  /** @return The number of header words that are required. */
  public abstract int gcHeaderWords();

  /** @return True if this plan contains GCspy. */
  public boolean withGCspy() { return false; }

  /** @return True if this plan contains GCTrace. */
  public boolean generateGCTrace() { return false; }

  /** @return True if type information must be immortal */
  public boolean needsImmortalTypeInfo() { return false; }
}
