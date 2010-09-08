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
package org.jikesrvm.mm.mminterface;

import org.mmtk.plan.CollectorContext;
import org.vmmagic.pragma.Uninterruptible;

/**
 * RVMThread must extend this class to associate appropriate context with processor.
 */
@Uninterruptible
public abstract class ThreadContext extends Selected.Mutator {
  protected CollectorContext collectorContext;

  public final CollectorContext getCollectorContext() {
    return collectorContext;
  }

  public final boolean isCollectorThread() {
    return collectorContext != null;
  }
}

