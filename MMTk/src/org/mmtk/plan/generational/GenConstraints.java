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

import org.mmtk.plan.StopTheWorldConstraints;

import org.mmtk.policy.CopySpace;

import org.vmmagic.pragma.*;

/**
 * This class and its subclasses communicate to the host VM/Runtime
 * any features of the selected plan that it needs to know.  This is
 * separate from the main Plan/PlanLocal class in order to bypass any
 * issues with ordering of static initialization.
 */
@Uninterruptible public class GenConstraints extends StopTheWorldConstraints {

  /** @return True if this plan is generational. */
  public boolean generational() { return true; }

  /** @return True if this plan moves objects. */
  public boolean movesObjects() { return true; }

  /** @return The number of header bits that are required. */
  public int gcHeaderBits() { return CopySpace.LOCAL_GC_BITS_REQUIRED; }

  /** @return The number of header words that are required. */
  public int gcHeaderWords() { return CopySpace.GC_HEADER_WORDS_REQUIRED; }

  /** @return True if this plan requires a write barrier */
  public boolean needsWriteBarrier() { return true; }
}
