/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan.immix;

import org.mmtk.plan.StopTheWorldConstraints;
import org.mmtk.policy.immix.ObjectHeader;
import static org.mmtk.policy.immix.ImmixConstants.MAX_IMMIX_OBJECT_BYTES;

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
  @Override
  public int gcHeaderBits() { return ObjectHeader.LOCAL_GC_BITS_REQUIRED; }

  /** @return The number of header words that are required. */
  @Override
  public int gcHeaderWords() { return ObjectHeader.GC_HEADER_WORDS_REQUIRED; }

  /** @return True if this plan moves objects. */
  @Override
  public boolean movesObjects() { return true;}

  /** @return The specialized scan methods required */
  @Override
  public int numSpecializedScans() { return 2; }

  /** @return Size (in bytes) beyond which new regular objects must be allocated to the LOS */
  @Override
  public int maxNonLOSDefaultAllocBytes() { return MAX_IMMIX_OBJECT_BYTES; }

  /** @return Size (in bytes) beyond which copied objects must be copied to the LOS */
  @Override
  public int maxNonLOSCopyBytes() { return MAX_IMMIX_OBJECT_BYTES; }
}
