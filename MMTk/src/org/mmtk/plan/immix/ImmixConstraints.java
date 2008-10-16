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
package org.mmtk.plan.immix;

import org.mmtk.plan.StopTheWorldConstraints;
import org.mmtk.policy.immix.ObjectHeader;

import org.vmmagic.pragma.*;

/**
 * This class and its subclasses communicate to the host VM/Runtime
 * any features of the selected plan that it needs to know.  This is
 * separate from the main Plan/PlanLocal class in order to bypass any
 * issues with ordering of static initialization.
 */
@Uninterruptible
public class ImmixConstraints extends StopTheWorldConstraints {

  /** @return The number of header bits that are required. */
  public int gcHeaderBits() { return ObjectHeader.LOCAL_GC_BITS_REQUIRED; }

  /** @return The number of header words that are required. */
  public int gcHeaderWords() { return ObjectHeader.GC_HEADER_WORDS_REQUIRED; }

  /** @return True if this plan moves objects. */
  public boolean movesObjects() { return true;}

  /** @return The specialized scan methods required */
  public int numSpecializedScans() { return 2; }

  /** @return true because we cannot accommodate large objects in default allocator */
  public boolean requiresLOS() { return true; }
}
