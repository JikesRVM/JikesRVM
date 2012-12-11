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
package org.mmtk.plan.refcount.generational;

import static org.mmtk.policy.SegregatedFreeListSpace.MAX_FREELIST_OBJECT_BYTES;

import org.mmtk.plan.refcount.RCBaseConstraints;
import org.vmmagic.pragma.*;

/**
 * This class and its subclasses communicate to the host VM/Runtime
 * any features of the selected plan that it needs to know.  This is
 * separate from the main Plan/PlanLocal class in order to bypass any
 * issues with ordering of static initialization.
 */
@Uninterruptible
public class GenRCConstraints extends RCBaseConstraints {
  @Override
  public boolean movesObjects() { return true; }
  @Override
  public int maxNonLOSCopyBytes() { return MAX_FREELIST_OBJECT_BYTES;}
  @Override
  public boolean buildForGenRC() { return true; }
}
