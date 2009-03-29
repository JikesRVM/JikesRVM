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
package org.mmtk.plan.markcompact;

import org.mmtk.plan.StopTheWorldConstraints;

import org.mmtk.policy.MarkCompactSpace;

import org.vmmagic.pragma.*;

/**
 * This class and its subclasses communicate to the host VM/Runtime
 * any features of the selected plan that it needs to know.  This is
 * separate from the main Plan/PlanLocal class in order to bypass any
 * issues with ordering of static initialization.
 */
@Uninterruptible
public class MCConstraints extends StopTheWorldConstraints {
  @Override
  public boolean movesObjects() { return true; }
  @Override
  public boolean needsForwardAfterLiveness() { return true; }
  @Override
  public boolean needsLinearScan() { return true; }
  @Override
  public int gcHeaderBits() { return MarkCompactSpace.LOCAL_GC_BITS_REQUIRED; }
  @Override
  public int gcHeaderWords() { return MarkCompactSpace.GC_HEADER_WORDS_REQUIRED; }
  @Override
  public int numSpecializedScans() { return 2; }
}
