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
package org.mmtk.plan.concurrent;

import org.mmtk.plan.SimpleConstraints;
import org.vmmagic.pragma.*;

/**
 * This class and its subclasses communicate to the host VM/Runtime
 * any features of the selected plan that it needs to know.  This is
 * separate from the main Plan/PlanLocal class in order to bypass any
 * issues with ordering of static initialization.
 */
@Uninterruptible
public abstract class ConcurrentConstraints extends SimpleConstraints {
  public boolean needsConcurrentWorkers() { return true; }

  public boolean needsWriteBarrier() { return true; }

  public boolean needsReferenceTypeReadBarrier() { return true; }

  public boolean needsStaticWriteBarrier() { return false; /* TODO: true */ }
}
