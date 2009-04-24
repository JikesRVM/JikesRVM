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

/**
 * RVMThread must extend this class to associate appropriate context with processor.
 */
public abstract class ThreadContext extends Selected.Mutator {
  /**
   * The collector context to be used by the given thread.  Only collector
   * threads have collector contexts.
   * <p>
   * NOTE: if we have N processors and we're running with a concurrent
   * collector, we will have 2*N collector contexts - N for the
   * stop-the-world collector threads, and N for the concurrent collector
   * threads.
   */
  public Selected.Collector collectorContext;
}

