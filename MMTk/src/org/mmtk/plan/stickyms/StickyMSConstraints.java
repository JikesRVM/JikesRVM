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
package org.mmtk.plan.stickyms;

import org.mmtk.plan.marksweep.MSConstraints;

import org.mmtk.policy.SegregatedFreeListSpace;

import org.vmmagic.pragma.*;

/**
 * This class and its subclasses communicate to the host VM/Runtime
 * any features of the selected plan that it needs to know.  This is
 * separate from the main Plan/PlanLocal class in order to bypass any
 * issues with ordering of static initialization.
 */
@Uninterruptible
public class StickyMSConstraints extends MSConstraints {
  /** @return The number of specialized scans.  We need nursery & full heap. */
  @Override
  public int numSpecializedScans() { return 2; }

  /** @return True if this plan requires a write barrier */
  @Override
  public boolean needsObjectReferenceWriteBarrier() { return true; }

  /** @return True if this Plan requires a header bit for object logging */
  @Override
  public boolean needsLogBitInHeader() { return true; }

  @Override
  public int maxNonLOSDefaultAllocBytes() { return SegregatedFreeListSpace.MAX_FREELIST_OBJECT_BYTES; }
  
  @Override
  public boolean objectReferenceBulkCopySupported() { return true; }
}
