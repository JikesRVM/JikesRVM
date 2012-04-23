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
package org.mmtk.plan.refcount.fullheap;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.plan.refcount.RCBaseCollector;
import org.vmmagic.pragma.*;

/**
 * This class implements the collector context for a simple reference counting
 * collector.
 */
@Uninterruptible
public class RCCollector extends RCBaseCollector {
  /************************************************************************
   * Initialization
   */
  private final RCFindRootSetTraceLocal rootTrace;
  private final RCModifiedProcessor modProcessor;

  /**
   * Constructor.
   */
  public RCCollector() {
    rootTrace = new RCFindRootSetTraceLocal(global().rootTrace, newRootBuffer);
    modProcessor = new RCModifiedProcessor();
  }

  /**
   * Get the modified processor to use.
   */
  @Override
  protected final TransitiveClosure getModifiedProcessor() {
    return modProcessor;
  }

  /**
   * Get the root trace to use.
   */
  @Override
  protected final TraceLocal getRootTrace() {
    return rootTrace;
  }
}
